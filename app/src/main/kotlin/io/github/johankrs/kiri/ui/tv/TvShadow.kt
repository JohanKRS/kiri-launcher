package io.github.johankrs.kiri.ui.tv

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.LruCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A soft shadow under a rounded box of the TV's home screen: one rounded rectangle blurred so
 * that it fades evenly to nothing. Built from a few layers each a little larger, as it was,
 * every layer showed as a band, the more so on a large screen.
 *
 * [blur] is how far it fades, in px: the spread (sigma) of the blur. With [hollow] it is only
 * seen outside the box, as under a banner of glass, which would show it through.
 *
 * The blur is made once, into a picture of the shadow alone, and the picture is what is drawn:
 * the graphics card of a TV only lays it down, as cheaply as any small image, and nothing is
 * blurred or cut while the focus moves. The pictures are shared by every box of the same size.
 */
internal class SoftShadow(private val blur: Float, private val hollow: Boolean = false) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Draws it under the box being drawn, rounded by [corner] (px), [drop] px lower and as dark as
     * [alpha] where it is whole, that is under the box: at the box's edges it is half of that.
     */
    fun draw(scope: DrawScope, alpha: Float, drop: Float, corner: Float) {
        if (alpha <= 0f) return
        val width = scope.size.width
        val height = scope.size.height
        if (width < 1f || height < 1f) return
        val picture = ShadowPictures.of(width, height, corner, drop, blur, hollow)
        paint.alpha = (alpha.coerceAtMost(1f) * 255f).roundToInt()
        scope.drawIntoCanvas { it.nativeCanvas.drawBitmap(picture.bitmap, -picture.reach, -picture.reach, paint) }
    }
}

/** A shadow made into a picture: the box's top left corner is [reach] px in from its own. */
private class ShadowPicture(val bitmap: Bitmap, val reach: Float)

/**
 * The pictures of the shadows, by size: a banner's, its wider one with the focus, a widget's.
 * Only a handful of sizes are on a screen at once, and each picture is a mask of one byte a pixel.
 */
private object ShadowPictures {
    private data class Key(val width: Int, val height: Int, val corner: Int, val drop: Int, val blur: Int, val hollow: Boolean)

    private val pictures = object : LruCache<Key, ShadowPicture>(4 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: ShadowPicture): Int = value.bitmap.allocationByteCount
    }

    fun of(width: Float, height: Float, corner: Float, drop: Float, blur: Float, hollow: Boolean): ShadowPicture {
        // A tenth of a pixel tells nothing apart, and keeps a box that only moves from making new ones.
        val key = Key(tenths(width), tenths(height), tenths(corner), tenths(drop), tenths(blur), hollow)
        return pictures.get(key) ?: make(width, height, corner, drop, blur, hollow).also { pictures.put(key, it) }
    }

    private fun tenths(value: Float): Int = (value * 10f).roundToInt()

    private fun make(width: Float, height: Float, corner: Float, drop: Float, blur: Float, hollow: Boolean): ShadowPicture {
        // Android takes a radius, and blurs by 0.57735 of it plus half a pixel.
        val radius = ((blur - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
        val sigma = 0.57735f * radius + 0.5f
        // A gaussian is all but gone three spreads out.
        val reach = ceil(3f * sigma + 1f)
        val bitmap = Bitmap.createBitmap(
            ceil(width + 2f * reach).toInt(),
            ceil(height + 2f * reach + drop.coerceAtLeast(0f)).toInt(),
            Bitmap.Config.ALPHA_8,
        )
        val canvas = Canvas(bitmap)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(reach, reach + drop, reach + width, reach + height + drop, corner, corner, shade)
        if (hollow) {
            // Cut where the box is, with its own soft edge, so nothing of it shows through glass.
            val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            canvas.drawRoundRect(reach, reach, reach + width, reach + height, corner, corner, cut)
        }
        bitmap.prepareToDraw()
        return ShadowPicture(bitmap, reach)
    }
}
