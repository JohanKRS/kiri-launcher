package io.github.johankrs.kiri.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The picture the person chose for the TV's home screen. Kiri keeps its own copy, no larger
 * than a TV's screen needs, so it stays even when the drive it came from is taken out.
 *
 * There is one for the whole app.
 */
@Stable
class TvPicture private constructor(private val appContext: Context) {
    private val file = File(appContext.filesDir, "tv/wallpaper.jpg")
    private val lock = Mutex()

    /** Goes up when another picture is chosen. 0 while there is none. */
    var version by mutableIntStateOf(if (file.exists()) 1 else 0)
        private set

    val chosen: Boolean get() = version > 0

    /** The picture, or null when none was chosen or it cannot be read. */
    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }

    /** Keeps the picture at [uri] in place of the one before. False when it is not a picture. */
    suspend fun keep(uri: Uri): Boolean {
        val kept = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val resolver = appContext.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
                    // Read only as much of it as covers the screen: photos are far larger.
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2
                    val raw = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching false
                    val turn = resolver.openInputStream(uri)?.use { input ->
                        runCatching { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    }
                    val upright = when (turn) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> turned(raw, 90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> turned(raw, 180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> turned(raw, 270f)
                        else -> raw
                    }
                    val scale = max(WIDTH / upright.width.toFloat(), HEIGHT / upright.height.toFloat()).coerceAtMost(1f)
                    val fitted = if (scale < 1f) {
                        Bitmap.createScaledBitmap(upright, (upright.width * scale).roundToInt(), (upright.height * scale).roundToInt(), true)
                            .also { if (it !== upright) upright.recycle() }
                    } else {
                        upright
                    }
                    file.parentFile?.mkdirs()
                    val incoming = File(file.parentFile, "incoming.jpg")
                    incoming.outputStream().use { fitted.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    fitted.recycle()
                    incoming.renameTo(file)
                }.getOrDefault(false)
            }
        }
        if (kept) version++
        return kept
    }

    private fun turned(raw: Bitmap, degrees: Float): Bitmap {
        val turned = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        if (turned !== raw) raw.recycle()
        return turned
    }

    companion object {
        /** A TV's screen at its largest that matters for a picture behind banners. */
        private const val WIDTH = 1920
        private const val HEIGHT = 1080

        @Volatile
        private var instance: TvPicture? = null

        fun of(context: Context): TvPicture = instance ?: synchronized(this) {
            instance ?: TvPicture(context.applicationContext).also { instance = it }
        }
    }
}

/** Where pictures to choose from come from: the TV's own, USB drives included. */
interface TvPictureSource {
    /** Whether Kiri may read the pictures; when not, [permission] asks for it. */
    fun allowed(): Boolean

    /** The permission that lets Kiri see the pictures. */
    val permission: String

    /** The pictures, newest first, at most [limit] of them. */
    suspend fun list(limit: Int = 400): List<Uri>

    /** A small picture of [uri], about [width] by [height] pixels; null when it cannot be read. */
    suspend fun thumbnail(uri: Uri, width: Int, height: Int): ImageBitmap?
}

/** The pictures Android knows about on every drive of the device. */
class MediaPictures(private val context: Context) : TvPictureSource {
    override val permission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun allowed(): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED || Environment.isExternalStorageManager()

    override suspend fun list(limit: Int): List<Uri> = withContext(Dispatchers.IO) {
        if (!allowed()) return@withContext emptyList()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val found = ArrayList<Uri>()
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && found.size < limit) {
                    found += ContentUris.withAppendedId(collection, cursor.getLong(id))
                }
            }
        }
        found
    }

    override suspend fun thumbnail(uri: Uri, width: Int, height: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(width, height), null).asImageBitmap() }.getOrNull()
    }
}
