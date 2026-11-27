package io.github.johankrs.kiri.ui.tv

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt

/**
 * A soft shadow under a rounded box of the TV's home screen: one rounded rectangle that the
 * graphics card blurs, so that it fades evenly to nothing. Built from a few layers each a little
 * larger, as it was, every layer showed as a band, the more so on a large screen.
 *
 * [blur] is how far it fades, in px: the spread (sigma) of the blur. Made once for a size and
 * kept: once the graphics card has made its blur, it draws as cheaply as a plain rectangle.
 */
internal class SoftShadow(blur: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Android takes a radius, and blurs by 0.57735 of it plus half a pixel.
        maskFilter = BlurMaskFilter(((blur - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
    }

    /**
     * Draws it under the box being drawn, rounded by [corner] (px), [drop] px lower and as dark as
     * [alpha] where it is whole, that is under the box: at the box's edges it is half of that.
     */
    fun draw(scope: DrawScope, alpha: Float, drop: Float, corner: Float) {
        if (alpha <= 0f) return
        paint.color = android.graphics.Color.argb((alpha.coerceAtMost(1f) * 255f).roundToInt(), 0, 0, 0)
        val width = scope.size.width
        val height = scope.size.height
        scope.drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, drop, width, height + drop, corner, corner, paint) }
    }
}
