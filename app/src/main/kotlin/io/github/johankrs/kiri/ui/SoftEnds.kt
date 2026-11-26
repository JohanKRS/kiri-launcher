package io.github.johankrs.kiri.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * One end of something that scrolls, where what leaves dissolves instead of being cut by a
 * straight line: nothing of it is left at [gone] from that end, and all of it at [whole].
 */
@Immutable
internal data class SoftEnd(val gone: Dp, val whole: Dp)

/**
 * Lets what is drawn here dissolve at its [top] and [bottom]: it goes out of focus and fades,
 * as if into mist, where a plain list would be cut off. Where the device cannot blur (before
 * Android 12) it only fades.
 *
 * [fadeTop] and [fadeBottom] are read during draw so offscreen layers and GPU blur passes are
 * skipped completely whenever an end has nothing scrolled into it.
 */
@Composable
internal fun Modifier.softEnds(
    top: SoftEnd?,
    bottom: SoftEnd?,
    fadeTop: () -> Boolean = { true },
    fadeBottom: () -> Boolean = { true },
): Modifier {
    if (top == null && bottom == null) return this
    val all = rememberGraphicsLayer()
    val topSharp = rememberGraphicsLayer()
    val topHaze = rememberGraphicsLayer()
    val bottomSharp = rememberGraphicsLayer()
    val bottomHaze = rememberGraphicsLayer()
    return drawWithCache {
        val blur: RenderEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BlurEffect(HAZE.toPx(), HAZE.toPx(), TileMode.Decal)
        } else {
            null
        }
        val topBand = top?.let { band(it, mirrored = false, hazy = blur != null) }
        val bottomBand = bottom?.let { band(it, mirrored = true, hazy = blur != null) }

        topSharp.compositingStrategy = CompositingStrategy.Offscreen
        topSharp.clip = true
        bottomSharp.compositingStrategy = CompositingStrategy.Offscreen
        bottomSharp.clip = true
        if (blur != null) {
            topHaze.compositingStrategy = CompositingStrategy.Offscreen
            topHaze.clip = true
            topHaze.renderEffect = blur
            bottomHaze.compositingStrategy = CompositingStrategy.Offscreen
            bottomHaze.clip = true
            bottomHaze.renderEffect = blur
        }

        onDrawWithContent {
            val drawTop = topBand != null && fadeTop()
            val drawBottom = bottomBand != null && fadeBottom()
            if (!drawTop && !drawBottom) {
                drawContent()
                return@onDrawWithContent
            }
            val width = size.width.toInt()
            val height = size.height.toInt()
            if (width <= 0 || height <= 0) {
                drawContent()
                return@onDrawWithContent
            }
            all.record { this@onDrawWithContent.drawContent() }
            val from = if (drawTop) topBand!!.height else 0
            val until = height - (if (drawBottom) bottomBand!!.height else 0)
            if (until > from) {
                clipRect(top = from.toFloat(), bottom = until.toFloat()) { drawLayer(all) }
            }
            if (drawTop) drawEnd(topBand!!, all, topSharp, topHaze, width = width, at = 0, hazy = blur != null)
            if (drawBottom) drawEnd(bottomBand!!, all, bottomSharp, bottomHaze, width = width, at = until, hazy = blur != null)
        }
    }
}

/** A band at one end: how tall it is and how much of the sharp and of the blurred picture shows along it. */
private class Band(val height: Int, val sharp: Brush, val haze: Brush?)

private fun Density.band(end: SoftEnd, mirrored: Boolean, hazy: Boolean): Band? {
    val whole = end.whole.toPx()
    val gone = end.gone.toPx().coerceIn(0f, whole)
    if (whole - gone < 1f) return null
    val height = ceil(whole).toInt()
    // A place along the way from gone (0) to whole (1), as a share of the band.
    fun at(share: Float): Float = ((gone + (whole - gone) * share) / height).coerceIn(0f, 1f)
    val startY = if (mirrored) height.toFloat() else 0f
    val endY = if (mirrored) 0f else height.toFloat()
    // With a blurred picture to take over, the sharp one leaves earlier.
    val sharp = Brush.verticalGradient(
        0f to Color.Transparent,
        at(if (hazy) SHARP_FROM else 0f) to Color.Transparent,
        at(1f) to Color.Black,
        1f to Color.Black,
        startY = startY,
        endY = endY,
    )
    val haze = if (hazy) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            at(0f) to Color.Transparent,
            at(HAZE_FULL_FROM) to Color.Black,
            at(HAZE_FULL_UNTIL) to Color.Black,
            at(1f) to Color.Transparent,
            1f to Color.Transparent,
            startY = startY,
            endY = endY,
        )
    } else {
        null
    }
    return Band(height, sharp, haze)
}

/** Draws the [band] that starts [at] pixels from the top: the sharp picture fading, then the blurred one over it. */
private fun DrawScope.drawEnd(
    band: Band,
    content: GraphicsLayer,
    sharp: GraphicsLayer,
    haze: GraphicsLayer,
    width: Int,
    at: Int,
    hazy: Boolean,
) {
    val bandSize = IntSize(width, band.height)
    sharp.topLeft = IntOffset(0, at)
    sharp.record(bandSize) {
        translate(top = -at.toFloat()) { drawLayer(content) }
        drawRect(brush = band.sharp, blendMode = BlendMode.DstIn)
    }
    drawLayer(sharp)

    val mist = band.haze
    if (mist != null && hazy) {
        haze.topLeft = IntOffset(0, at)
        haze.record(bandSize) {
            translate(top = -at.toFloat()) { drawLayer(content) }
            drawRect(brush = mist, blendMode = BlendMode.DstIn)
        }
        drawLayer(haze)
    }
}

/** How much the picture is blurred where it dissolves. */
private val HAZE = 5.dp

/** Along a band, from gone (0) to whole (1): where the sharp picture begins to show. */
private const val SHARP_FROM = 0.4f

/** Where the blurred picture is all there, between coming up from nothing and giving way to the sharp one. */
private const val HAZE_FULL_FROM = 0.45f
private const val HAZE_FULL_UNTIL = 0.6f
