package io.github.johankrs.kiri.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import io.github.johankrs.kiri.core.look.BoxBlur
import io.github.johankrs.kiri.core.look.WallpaperFraming
import io.github.johankrs.kiri.core.look.WallpaperSetup
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What a [WallpaperSetup] looks like, small, for the settings to show while it is adjusted.
 *
 * [image] is the whole wallpaper, which is wider than the screen when it travels with the
 * pages. [shareX] and [shareY] say how much of the picture it covers along each side, for
 * turning a drag of the finger into a pan.
 */
@Immutable
class WallpaperPreview(val image: ImageBitmap, val shareX: Float, val shareY: Float)

/**
 * Makes the wallpaper out of a picture the person chose in Kiri: keeps the picture, draws it
 * the way a [WallpaperSetup] says, and gives it to the device for the home screen, the lock
 * screen or both.
 *
 * What it put on the home screen it also keeps for itself, so glass can show the wallpaper
 * blurred without Kiri being allowed to read the device's files.
 *
 * There is one for the whole app.
 */
@Stable
class WallpaperStudio private constructor(private val appContext: Context) {
    private val manager = WallpaperManager.getInstance(appContext)
    private val folder = File(appContext.filesDir, "wallpaper")
    private val original = File(folder, "original")
    private val applied = File(folder, "applied.jpg")
    private val notes = appContext.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** Goes up when another picture is chosen. 0 while none was chosen yet. */
    var version by mutableIntStateOf(if (original.exists()) 1 else 0)
        private set

    // The picture as last read, and what it was read for.
    private var decoded: Bitmap? = null
    private var decodedKey = ""

    /** Releases the cached decoded source bitmap when leaving wallpaper settings or trimming memory. */
    fun releasePreview() {
        if (lock.tryLock()) {
            try {
                decoded?.recycle()
                decoded = null
                decodedKey = ""
            } finally {
                lock.unlock()
            }
        }
    }

    /** Takes the picture at [uri] as the one to make wallpapers from. False when it is not a picture. */
    suspend fun choose(uri: Uri): Boolean {
        val taken = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    folder.mkdirs()
                    val incoming = File(folder, "incoming")
                    val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        incoming.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Only a file that really is a picture replaces the one in use.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(incoming.path, bounds)
                    if (copied == null || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        incoming.delete()
                        false
                    } else {
                        original.delete()
                        incoming.renameTo(original).also {
                            if (it) {
                                decoded?.recycle()
                                decoded = null
                                decodedKey = ""
                            }
                        }
                    }
                }.getOrDefault(false)
            }
        }
        if (taken) version++
        return taken
    }

    /** The picture chosen, as it was given, for a backup; null when none was chosen or it is too large. */
    suspend fun originalBytes(maxBytes: Long): ByteArray? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching { original.takeIf { it.exists() && it.length() in 1..maxBytes }?.readBytes() }.getOrNull()
        }
    }

    /**
     * Takes the picture of a backup as the one to make wallpapers from, as [choose] does with a
     * picture from the gallery. False when it is not a picture.
     */
    suspend fun restoreOriginal(bytes: ByteArray): Boolean {
        val taken = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    folder.mkdirs()
                    val incoming = File(folder, "incoming")
                    incoming.writeBytes(bytes)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(incoming.path, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        incoming.delete()
                        false
                    } else {
                        original.delete()
                        incoming.renameTo(original).also {
                            if (it) {
                                decoded?.recycle()
                                decoded = null
                                decodedKey = ""
                            }
                        }
                    }
                }.getOrDefault(false)
            }
        }
        if (taken) withContext(Dispatchers.Main) { version++ }
        return taken
    }

    /** The wallpaper [setup] makes on a screen of [screen] pixels, drawn [heightPx] tall. */
    suspend fun preview(setup: WallpaperSetup, screen: IntSize, heightPx: Int, density: Float): WallpaperPreview? {
        if (screen.width <= 0 || screen.height <= 0 || heightPx <= 0) return null
        return withContext(Dispatchers.Default) {
            lock.withLock {
                runCatching<WallpaperPreview?> {
                    val source = picture(::previewSample) ?: return@runCatching null
                    val scale = heightPx / screen.height.toFloat()
                    val width = (WallpaperFraming.wallpaperWidth(screen.width, setup.scrollPercent) * scale).roundToInt()
                    val frame = WallpaperFraming.frame(
                        source.width, source.height, width, heightPx, setup.zoomPercent, setup.panX, setup.panY,
                    )
                    WallpaperPreview(
                        image = draw(setup, source, width, heightPx, setup.blurDp * density * scale).asImageBitmap(),
                        shareX = frame.width / source.width,
                        shareY = frame.height / source.height,
                    )
                }.getOrNull()
            }
        }
    }

    /** Makes the wallpaper of [setup] for a screen of [screen] pixels and gives it to the device. */
    suspend fun apply(setup: WallpaperSetup, screen: IntSize, density: Float): Boolean {
        if (screen.width <= 0 || screen.height <= 0) return false
        return withContext(Dispatchers.Default) {
            lock.withLock {
                runCatching {
                    val width = WallpaperFraming.wallpaperWidth(screen.width, setup.scrollPercent)
                    val source = picture { pictureWidth, pictureHeight ->
                        applySample(pictureWidth, pictureHeight, width, screen.height, setup.zoomPercent)
                    } ?: return@runCatching false
                    val bitmap = try {
                        draw(setup, source, width, screen.height, setup.blurDp * density)
                    } finally {
                        // The full picture is large: recycle and drop once drawn.
                        if (decoded === source) {
                            decoded = null
                            decodedKey = ""
                        }
                        source.recycle()
                    }
                    try {
                        var where = 0
                        if (setup.target.onHome) where = where or WallpaperManager.FLAG_SYSTEM
                        if (setup.target.onLock) where = where or WallpaperManager.FLAG_LOCK
                        if (setup.target.onHome) {
                            // Wider than the screen: the room the wallpaper travels over.
                            runCatching { manager.suggestDesiredDimensions(width, screen.height) }
                        }
                        manager.setBitmap(bitmap, null, true, where)
                        if (setup.target.onHome) {
                            folder.mkdirs()
                            applied.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            notes.edit()
                                .putInt(KEY_APPLIED_ID, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
                                .apply()
                        }
                        true
                    } finally {
                        bitmap.recycle()
                    }
                }.getOrDefault(false)
            }
        }
    }

    /**
     * The wallpaper Kiri itself put on the home screen, at half its size, while it is still
     * the one there. Null once the wallpaper was changed somewhere else.
     */
    fun appliedCopy(): Bitmap? = runCatching {
        if (!applied.exists()) return@runCatching null
        val current = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        if (current != notes.getInt(KEY_APPLIED_ID, NO_ID)) return@runCatching null
        BitmapFactory.decodeFile(applied.path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }.getOrNull()

    /**
     * The picture chosen, upright, read [sizeFor] times smaller than it is (a power of two,
     * given the upright width and height of the whole picture).
     */
    private fun picture(sizeFor: (width: Int, height: Int) -> Int): Bitmap? {
        val shape = shape() ?: return null
        val sample = sizeFor(shape.width, shape.height).coerceAtLeast(1)
        val key = "$version/$sample"
        decoded?.takeIf { !it.isRecycled && decodedKey == key }?.let { return it }
        val raw = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return upright(raw, shape.turn).also {
            decoded?.recycle()
            decoded = it
            decodedKey = key
        }
    }

    /** How large the chosen picture is once upright, and how far it has to be turned. */
    private class Shape(val width: Int, val height: Int, val turn: Float)

    private var shapeOf: Shape? = null
    private var shapeVersion = -1

    /** Read from the file's header only, once for each picture chosen. */
    private fun shape(): Shape? {
        if (shapeVersion == version) shapeOf?.let { return it }
        if (!original.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val turn = turn()
        val sideways = turn == 90f || turn == 270f
        return Shape(
            width = if (sideways) bounds.outHeight else bounds.outWidth,
            height = if (sideways) bounds.outWidth else bounds.outHeight,
            turn = turn,
        ).also {
            shapeOf = it
            shapeVersion = version
        }
    }

    /** For the previews: the longer side at least [PREVIEW_SIDE], and under twice that. */
    private fun previewSample(width: Int, height: Int): Int {
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= PREVIEW_SIDE) sample *= 2
        return sample
    }

    /**
     * For the wallpaper itself: as small as it can be read while every pixel of a wallpaper of
     * [outWidth] by [outHeight] at [zoomPercent] still gets at least one pixel of the picture,
     * so it is never stretched; and never larger than [MAX_APPLY_PIXELS], so a photo of 50 or
     * 200 megapixels does not run out of memory.
     */
    private fun applySample(width: Int, height: Int, outWidth: Int, outHeight: Int, zoomPercent: Int): Int {
        val zoom = max(1f, zoomPercent / 100f)
        // How many pixels of the whole picture fall on one pixel of the wallpaper.
        val spare = min(width / (outWidth * zoom), height / (outHeight * zoom))
        var sample = 1
        while (sample * 2 <= spare) sample *= 2
        while ((width / sample).toLong() * (height / sample) > MAX_APPLY_PIXELS) sample *= 2
        return sample
    }

    private fun turn(): Float {
        val orientation = runCatching {
            ExifInterface(original.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    /** Photos say which way up they were taken instead of being stored that way. */
    private fun upright(raw: Bitmap, turn: Float): Bitmap {
        if (turn == 0f) return raw
        val turned = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn) }, true)
        if (turned !== raw) raw.recycle()
        return turned
    }

    /** [source] framed, blurred and darkened as [setup] says, [width] by [height] pixels. */
    private fun draw(setup: WallpaperSetup, source: Bitmap, width: Int, height: Int, blurPx: Float): Bitmap {
        val frame = WallpaperFraming.frame(source.width, source.height, width, height, setup.zoomPercent, setup.panX, setup.panY)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val whole = Rect(0, 0, width, height)
        canvas.drawBitmap(
            source,
            Rect(frame.left.roundToInt(), frame.top.roundToInt(), frame.right.roundToInt(), frame.bottom.roundToInt()),
            whole,
            paint,
        )
        if (blurPx >= 1f) {
            // A blur hides detail anyway, so a smaller copy is blurred and stretched back.
            val shrink = (blurPx / 4f).coerceIn(1f, MAX_SHRINK)
            val smallWidth = (width / shrink).roundToInt().coerceAtLeast(1)
            val smallHeight = (height / shrink).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(out, smallWidth, smallHeight, true)
            val pixels = IntArray(smallWidth * smallHeight)
            small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
            if (small !== out) small.recycle()
            BoxBlur.blur(pixels, smallWidth, smallHeight, radius = (blurPx / shrink / 2f).roundToInt().coerceAtLeast(1))
            val soft = Bitmap.createBitmap(pixels, smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
            canvas.drawBitmap(soft, null, whole, paint)
            soft.recycle()
        }
        if (setup.dimPercent > 0) canvas.drawColor(Color.argb(setup.dimPercent * 255 / 100, 0, 0, 0))
        return out
    }

    companion object {
        /** The settings draw their previews from a copy of the picture at most this large. */
        private const val PREVIEW_SIDE = 1024
        /** The most pixels a picture is read with to make the wallpaper: 64 MB at 4 bytes each. */
        private const val MAX_APPLY_PIXELS = 4096L * 4096L
        private const val MAX_SHRINK = 8f
        private const val KEY_APPLIED_ID = "appliedId"
        private const val NO_ID = Int.MIN_VALUE

        @Volatile
        private var instance: WallpaperStudio? = null

        fun of(context: Context): WallpaperStudio = instance ?: synchronized(this) {
            instance ?: WallpaperStudio(context.applicationContext).also { instance = it }
        }
    }
}
