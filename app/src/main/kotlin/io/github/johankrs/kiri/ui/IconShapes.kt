package io.github.johankrs.kiri.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import io.github.johankrs.kiri.core.look.IconShape
import kotlin.math.roundToInt

/** Between a circle and a square, with no straight side. */
internal val SquircleShape: Shape = GenericShape { size, _ ->
    val points = IconShape.squirclePoints()
    moveTo(points[0] * size.width, points[1] * size.height)
    for (index in 1 until points.size / 2) {
        lineTo(points[index * 2] * size.width, points[index * 2 + 1] * size.height)
    }
    close()
}

private val RoundedIconShape: Shape = RoundedCornerShape(percent = (IconShape.ROUNDED.cornerShare * 100).roundToInt())
private val SoftSquareIconShape: Shape = RoundedCornerShape(percent = (IconShape.SOFT_SQUARE.cornerShare * 100).roundToInt())

/** The outline of this icon shape, for glass and edges drawn on screen. */
internal fun IconShape.outline(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.ROUNDED -> RoundedIconShape
    IconShape.SOFT_SQUARE -> SoftSquareIconShape
}
