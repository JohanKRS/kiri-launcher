package io.github.johankrs.kiri.data

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import io.github.johankrs.kiri.core.look.BoxBlur
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whether Kiri has its own picture of the wallpaper, and if not, why. */
enum class WallpaperReading {
    /** The person has not allowed Kiri to read the wallpaper. */
    NOT_ALLOWED,

    /** The wallpaper is a live one: there is no picture to read, it draws itself. */
    ANIMATED,

    /** It is allowed and a picture, but reading it failed. */
    UNREADABLE,

    /** Kiri has the picture. */
    READY,
}

/**
 * The wallpaper as Kiri can know it: it reads the picture by itself, follows it when it is
 * changed, and blurs its own small copy for glass to show through.
 *
 * A wallpaper made in Kiri needs nothing: Kiri kept its own copy of it (see [WallpaperStudio]).
 * Any other one Android only hands to an app that was given access to all files, so that is
 * asked of the person once. A live wallpaper cannot be read at all: only the device itself can
 * blur that one.
 *
 * There is one for the whole app, so what it knows survives the screen being rebuilt.
 */
@Stable
class WallpaperSource private constructor(private val appContext: Context) {
    private val manager = WallpaperManager.getInstance(appContext)
    private val studio = WallpaperStudio.of(appContext)
    private val lock = Mutex()

    /** What the last look at the wallpaper found. Null before the first look. */
    var reading: WallpaperReading? by mutableStateOf(null)
        private set

    /** Goes up every time the device says the wallpaper changed: time to look again. */
    var changes by mutableIntStateOf(0)
        private set

    /** Kiri itself changed the wallpaper: look again without waiting for the device to say so. */
    fun changed() {
        changes++
    }

    // The copy last read, half the size of the screen, and what it was read for.
    private var copy: Bitmap? = null
    private var copyKey = ""

    // Last values sent to WallpaperManager, so identical frames skip synchronous Binder IPC.
    private var lastOffsetToken: IBinder? = null
    private var lastOffsetPages: Int = -1
    private var lastOffsetAlong: Float = Float.NaN

    // The pictures last blurred, by what they were blurred for: the one behind glass and the
    // deeper one behind the settings.
    // Kept in the order they were last asked for, so the one given up is the one not in use.
    private val frosted = LinkedHashMap<String, ImageBitmap>(4, 0.75f, true)

    init {
        runCatching {
            manager.addOnColorsChangedListener(
                { _, which -> if (which and WallpaperManager.FLAG_SYSTEM != 0) changes++ },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /** Releases the sharp working copy when the system asks for memory (the blurred bitmaps stay). */
    fun trimMemory() {
        if (lock.tryLock()) {
            try {
                copy?.recycle()
                copy = null
                copyKey = ""
            } finally {
                lock.unlock()
            }
        }
    }

    /** Whether the person has allowed Kiri to read the wallpaper. */
    fun canRead(): Boolean = Environment.isExternalStorageManager() ||
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && appContext.checkSelfPermission(LEGACY_PERMISSION) == PackageManager.PERMISSION_GRANTED)

    /**
     * Whether this build can ask for the reading at all. Google Play's build cannot (it has no
     * access to all files, see [declares]): there only a wallpaper made in Kiri is blurred.
     */
    val canAsk: Boolean
        get() = appContext.declares(Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && appContext.declares(LEGACY_PERMISSION))

    /**
     * The permission to ask for with the usual dialog, on the Android versions where that is
     * enough. Null where only the "access to all files" screen will do (see [accessScreens]).
     */
    val permissionToAsk: String?
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) LEGACY_PERMISSION else null

    /** The system screens where access to all files is given, the most direct one first. */
    fun accessScreens(): List<Intent> = listOf(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", appContext.packageName, null),
        ),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )

    /**
     * The wallpaper as it fills a screen of [screen] pixels, blurred by [radiusPx] screen
     * pixels: as tall as the screen, and wider than it when the wallpaper travels with the
     * pages. Null when Kiri has no picture of it ([reading] says why) or no blur is wanted.
     *
     * The picture is kept: asking again for the same thing costs nothing.
     */
    suspend fun blurred(screen: IntSize, radiusPx: Float): ImageBitmap? {
        if (screen.width <= 0 || screen.height <= 0) return null
        var found: WallpaperReading = WallpaperReading.UNREADABLE
        val picture = withContext(Dispatchers.Default) {
            lock.withLock {
                val live = runCatching { manager.wallpaperInfo }.getOrNull() != null
                val sharp = if (live) null else currentCopy(screen)
                found = when {
                    live -> WallpaperReading.ANIMATED
                    sharp != null -> WallpaperReading.READY
                    !canRead() -> WallpaperReading.NOT_ALLOWED
                    else -> WallpaperReading.UNREADABLE
                }
                if (sharp == null) {
                    copy?.recycle()
                    copy = null
                    copyKey = ""
                    frosted.clear()
                    null
                } else if (radiusPx < 1f) {
                    null
                } else {
                    val key = "$copyKey/${radiusPx.roundToInt()}"
                    frosted[key] ?: blur(sharp, screen, radiusPx).also { made ->
                        if (frosted.size >= KEPT_BLURS) frosted.remove(frosted.keys.first())
                        frosted[key] = made
                    }
                }
            }
        }
        reading = found
        return picture
    }

    /**
     * Tells the wallpaper which home page is on screen, so one that scrolls can follow.
     * Returns how far along its way that puts the wallpaper, from 0 (its left end shows) to 1.
     */
    fun scrollTo(windowToken: IBinder?, position: Float, pages: Int): Float {
        val along = if (pages > 1) (position / (pages - 1)).coerceIn(0f, 1f) else 0.5f
        val token = windowToken ?: return along
        val tokenChanged = token !== lastOffsetToken
        val pagesChanged = tokenChanged || pages != lastOffsetPages
        val alongChanged = tokenChanged || lastOffsetAlong.isNaN() || abs(along - lastOffsetAlong) > 1e-4f
        if (!pagesChanged && !alongChanged) return along
        runCatching {
            if (pagesChanged) {
                manager.setWallpaperOffsetSteps(if (pages > 1) 1f / (pages - 1) else 1f, 1f)
                lastOffsetPages = pages
            }
            if (alongChanged) {
                manager.setWallpaperOffsets(token, along, 0.5f)
                lastOffsetAlong = along
            }
            lastOffsetToken = token
        }
        return along
    }

    /** The copy for this wallpaper and screen, read again only when one of them changed. */
    private fun currentCopy(screen: IntSize): Bitmap? {
        val id = runCatching { manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(0)
        val key = "$id/$changes/${screen.width}x${screen.height}"
        if (key != copyKey || copy == null) {
            val previous = copy
            copy = read(screen)
            previous?.recycle()
            copyKey = key
        }
        return copy
    }

    /**
     * The wallpaper at half the size it has on the screen: as tall as the screen, and as wide
     * as the device shows it. A wallpaper that travels with the pages is wider than the screen,
     * and all of its width is kept, so glass can show the part that is behind it right now.
     * Kiri's own copy when the wallpaper was made here, and otherwise the one read from the
     * device, which takes the access the person may not have given.
     */
    private fun read(screen: IntSize): Bitmap? = own(screen) ?: if (canRead()) fromDevice(screen) else null

    /** The wallpaper Kiri made, which is wider than the screen when it travels. */
    private fun own(screen: IntSize): Bitmap? = runCatching {
        val kept = studio.appliedCopy() ?: return@runCatching null
        try {
            val fit = fit(kept.width, kept.height, screen) ?: return@runCatching null
            val bitmap = Bitmap.createBitmap(fit.width, fit.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawBitmap(
                kept,
                null,
                RectF(fit.left, fit.top, fit.left + fit.drawnWidth, fit.top + fit.drawnHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            bitmap
        } finally {
            kept.recycle()
        }
    }.getOrNull()

    private fun fromDevice(screen: IntSize): Bitmap? = runCatching {
        val drawable = manager.drawable ?: return@runCatching null
        try {
            val fit = fit(drawable.intrinsicWidth, drawable.intrinsicHeight, screen) ?: return@runCatching null
            val bitmap = Bitmap.createBitmap(fit.width, fit.height, Bitmap.Config.ARGB_8888)
            drawable.setBounds(
                fit.left.roundToInt(),
                fit.top.roundToInt(),
                (fit.left + fit.drawnWidth).roundToInt(),
                (fit.top + fit.drawnHeight).roundToInt(),
            )
            drawable.isFilterBitmap = true
            drawable.draw(Canvas(bitmap))
            bitmap
        } finally {
            // The device keeps the full picture in this app's memory otherwise.
            manager.forgetLoadedWallpaper()
        }
    }.getOrNull()

    /** Where a wallpaper is drawn in the copy kept of it, and how large that copy is. */
    private class Fit(
        val width: Int,
        val height: Int,
        val left: Float,
        val top: Float,
        val drawnWidth: Float,
        val drawnHeight: Float,
    )

    /**
     * How a wallpaper of [sourceWidth] by [sourceHeight] lies in its copy. The device makes it
     * fill the screen. What is then too tall is cut evenly above and below; what is too wide
     * is where the wallpaper travels, and is kept (up to a few screens of it).
     */
    private fun fit(sourceWidth: Int, sourceHeight: Int, screen: IntSize): Fit? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val screenWidth = (screen.width / COPY_SHRINK).coerceAtLeast(1)
        val height = (screen.height / COPY_SHRINK).coerceAtLeast(1)
        val scale = max(screenWidth / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val width = drawnWidth.roundToInt().coerceIn(screenWidth, screenWidth * WIDEST)
        return Fit(
            width = width,
            height = height,
            left = (width - drawnWidth) / 2f,
            top = (height - drawnHeight) / 2f,
            drawnWidth = drawnWidth,
            drawnHeight = drawnHeight,
        )
    }

    /**
     * Blurs [sharp], keeping its proportions. A strong blur hides detail anyway, so the
     * stronger it is, the smaller the picture that is blurred: that keeps it quick, and the
     * picture is stretched when drawn.
     */
    private fun blur(sharp: Bitmap, screen: IntSize, radiusPx: Float): ImageBitmap {
        val shrink = (radiusPx / 4f).coerceIn(COPY_SHRINK.toFloat(), MAX_SHRINK)
        val height = (screen.height / shrink).roundToInt().coerceAtLeast(1)
        val width = (sharp.width * height / sharp.height.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(sharp, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== sharp) small.recycle()
        // Three passes of this reach look like a lens blur of about twice the reach.
        BoxBlur.blur(pixels, width, height, radius = (radiusPx / shrink / 2f).roundToInt().coerceAtLeast(1))
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    companion object {
        private const val LEGACY_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
        private const val COPY_SHRINK = 2
        private const val MAX_SHRINK = 8f
        private const val KEPT_BLURS = 3

        /** The widest wallpaper kept whole, in screens. */
        private const val WIDEST = 4

        @Volatile
        private var instance: WallpaperSource? = null

        fun of(context: Context): WallpaperSource = instance ?: synchronized(this) {
            instance ?: WallpaperSource(context.applicationContext).also { instance = it }
        }
    }
}
