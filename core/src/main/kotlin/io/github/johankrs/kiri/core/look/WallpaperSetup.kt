package io.github.johankrs.kiri.core.look

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a wallpaper chosen in Kiri is put. */
enum class WallpaperTarget(val key: String) {
    HOME("home"),
    LOCK("lock"),
    BOTH("both");

    val onHome: Boolean get() = this != LOCK
    val onLock: Boolean get() = this != HOME

    companion object {
        fun fromKey(key: String?): WallpaperTarget? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How the picture chosen in Kiri becomes the wallpaper: which part of it is seen, how much it
 * is darkened and blurred, how far it travels as the home pages turn, and where it is put.
 */
data class WallpaperSetup(
    /** 100 shows as much of the picture as fits; more comes closer. From [ZOOM_PERCENT]. */
    val zoomPercent: Int = 100,
    /** Where the part seen sits in the picture: -1000 the left or top end, 1000 the other. */
    val panX: Int = 0,
    val panY: Int = 0,
    /** How much the picture is darkened. From [DIM_PERCENT]. */
    val dimPercent: Int = 0,
    /** How much the picture itself is blurred, in dp. From [BLUR_DP]. */
    val blurDp: Int = 0,
    /** How far the picture travels with the home pages: 0 stays still. From [SCROLL_PERCENT]. */
    val scrollPercent: Int = 0,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
) {
    fun withZoom(percent: Int): WallpaperSetup = copy(zoomPercent = percent.coerceIn(ZOOM_PERCENT))
    fun withDim(percent: Int): WallpaperSetup = copy(dimPercent = percent.coerceIn(DIM_PERCENT))
    fun withBlur(dp: Int): WallpaperSetup = copy(blurDp = dp.coerceIn(BLUR_DP))
    fun withScroll(percent: Int): WallpaperSetup = copy(scrollPercent = percent.coerceIn(SCROLL_PERCENT))
    fun withPan(x: Int, y: Int): WallpaperSetup = copy(panX = x.coerceIn(PAN), panY = y.coerceIn(PAN))

    /** The setting as one line of text, to be saved. */
    val stored: String
        get() = listOf(zoomPercent, panX, panY, dimPercent, blurDp, scrollPercent).joinToString(",") + "," + target.key

    companion object {
        val ZOOM_PERCENT = 100..300
        const val ZOOM_STEP = 5
        val DIM_PERCENT = 0..70
        const val DIM_STEP = 5
        val BLUR_DP = 0..40
        const val BLUR_STEP = 2
        val SCROLL_PERCENT = 0..100
        const val SCROLL_STEP = 10
        val PAN = -1000..1000

        const val KEY = "wallpaper.setup"

        /** What [stored] wrote. Anything missing or broken falls back to the default. */
        fun fromStored(text: String?): WallpaperSetup {
            val parts = text?.split(',') ?: return WallpaperSetup()
            if (parts.size != 7) return WallpaperSetup()
            val numbers = parts.take(6).map { it.toIntOrNull() ?: return WallpaperSetup() }
            return WallpaperSetup(target = WallpaperTarget.fromKey(parts[6]) ?: WallpaperTarget.BOTH)
                .withZoom(numbers[0])
                .withPan(numbers[1], numbers[2])
                .withDim(numbers[3])
                .withBlur(numbers[4])
                .withScroll(numbers[5])
        }
    }
}

/** A part of a picture, in the picture's own pixels. */
data class PictureFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** The arithmetic of fitting a picture to the screen as a wallpaper. */
object WallpaperFraming {
    /** A wallpaper that travels all the way is this much wider than the screen. */
    const val MAX_EXTRA_WIDTH = 0.5f

    /**
     * How wide the wallpaper is made for a screen [screenWidth] wide: the screen itself, plus
     * the room the picture needs to travel as the pages turn.
     */
    fun wallpaperWidth(screenWidth: Int, scrollPercent: Int): Int =
        (screenWidth * (1f + MAX_EXTRA_WIDTH * scrollPercent.coerceIn(WallpaperSetup.SCROLL_PERCENT) / 100f)).roundToInt()

    /**
     * The part of a picture of [pictureWidth] by [pictureHeight] that fills a wallpaper of
     * [width] by [height]. It always has the proportions of the wallpaper and never leaves the
     * picture: zoom 100 is the largest such part, and the pans slide it over what is left.
     */
    fun frame(
        pictureWidth: Int,
        pictureHeight: Int,
        width: Int,
        height: Int,
        zoomPercent: Int,
        panX: Int,
        panY: Int,
    ): PictureFrame {
        if (pictureWidth <= 0 || pictureHeight <= 0 || width <= 0 || height <= 0) return PictureFrame(0f, 0f, 0f, 0f)
        // How many picture pixels one wallpaper pixel covers when the picture just fills it.
        val perPixel = min(pictureWidth / width.toFloat(), pictureHeight / height.toFloat())
        val zoom = max(1f, zoomPercent / 100f)
        val frameWidth = width * perPixel / zoom
        val frameHeight = height * perPixel / zoom
        val left = (pictureWidth - frameWidth) / 2f * (1f + panX.coerceIn(WallpaperSetup.PAN) / 1000f)
        val top = (pictureHeight - frameHeight) / 2f * (1f + panY.coerceIn(WallpaperSetup.PAN) / 1000f)
        return PictureFrame(left, top, left + frameWidth, top + frameHeight)
    }

    /**
     * The pan after the picture was dragged by [dragged] of the frame's own size (1 is a whole
     * frame), for a frame that covers [frameShare] of the picture along that side. Dragging
     * the picture one way moves the frame the other way.
     */
    fun panAfterDrag(pan: Int, dragged: Float, frameShare: Float): Int {
        val free = 1f - frameShare
        if (free <= 0.0001f) return 0
        // Half the free room is a pan of 1000.
        val moved = -dragged * frameShare / (free / 2f) * 1000f
        return (pan + moved).roundToInt().coerceIn(WallpaperSetup.PAN)
    }
}
