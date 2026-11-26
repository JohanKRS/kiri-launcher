package io.github.johankrs.kiri.ui

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import io.github.johankrs.kiri.core.look.GlassShine
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How the icons of one surface are being moved, for them to answer as tiles of glass would:
 * they lean a little with the movement and catch the light, and come to rest when it stops.
 *
 * Whatever scrolls the icons tells [moved] how far. [lean] follows the speed of that, softly,
 * and is only ever read while drawing: nothing is composed or measured again because of it.
 * While nothing moves, nothing runs.
 */
@Stable
class GlassMotion internal constructor(
    /** The icons move sideways (the pages of the home screen) and not up and down (the drawer). */
    val horizontal: Boolean,
    private val scope: CoroutineScope,
    /** The speed, in pixels a millisecond, at which the icons lean all the way. */
    private val fullSpeed: Float,
) {
    /** From -1 to 1: how far the icons lean, and to which side. 0 at rest. */
    var lean by mutableFloatStateOf(0f)
        private set

    // How far the icons were moved since the last frame, in pixels.
    private var pending = 0f
    private var settling: Job? = null

    /** The icons were moved by [deltaPx] along the way they scroll. */
    fun moved(deltaPx: Float) {
        if (deltaPx == 0f || fullSpeed <= 0f) return
        pending += deltaPx
        if (settling?.isActive != true) settling = scope.launch { settle() }
    }

    /** Leans with the movement frame by frame, and ends once the icons are back at rest. */
    private suspend fun settle() {
        var value = lean
        var speed = 0f
        var frame = withFrameNanos { it }
        // What moved before the first frame has no time to be measured against.
        pending = 0f
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - frame) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            frame = now
            val target = (pending / (seconds * 1000f) / fullSpeed).coerceIn(-1f, 1f)
            pending = 0f
            // A spring pulls the lean towards the speed: it lags a little and settles softly.
            speed += ((target - value) * STIFFNESS - speed * DAMPING) * seconds
            value += speed * seconds
            if (target == 0f && abs(value) < AT_REST && abs(speed) < AT_REST * 10f) {
                lean = 0f
                return
            }
            lean = value.coerceIn(-1.2f, 1.2f)
        }
    }

    private companion object {
        const val STIFFNESS = 400f
        const val DAMPING = 24f
        const val AT_REST = 0.004f
    }
}

/** The movement the icons below this point answer to, or null where they stay still. */
val LocalGlassMotion = staticCompositionLocalOf<GlassMotion?> { null }

/** A [GlassMotion] for icons that scroll sideways ([horizontal]) or up and down. */
@Composable
internal fun rememberGlassMotion(horizontal: Boolean): GlassMotion {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    return remember(horizontal, scope, density) { GlassMotion(horizontal, scope, FULL_SPEED_DP_PER_MS * density) }
}

/** A quick but ordinary scroll: about a screen and a half in a second. */
private const val FULL_SPEED_DP_PER_MS = 1.5f

/** Leans this icon with [motion], as far as [shine] says. */
internal fun Modifier.glassTilt(motion: GlassMotion, shine: GlassShine): Modifier = graphicsLayer {
    val lean = motion.lean
    if (lean != 0f) {
        if (motion.horizontal) rotationY = lean * shine.tiltDegrees else rotationX = -lean * shine.tiltDegrees
    }
}

private data class SheenOutlineKey(val width: Int, val height: Int, val shape: Shape, val direction: LayoutDirection)

/** Shared outlines for icons of the same pixel size and shape, avoiding 30+ identical 120-vertex Paths. */
private val SheenOutlines = LruCache<SheenOutlineKey, android.graphics.Path>(12)

private val SHEEN_COLORS = intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF)
private val SHEEN_STOPS = floatArrayOf(0f, 0.5f, 1f)

/**
 * The band of light that crosses an icon of [shape] while it moves: soft at both sides, a
 * little askew, as bright as [shine] says at full speed and gone at rest. It travels the way
 * the icons do.
 *
 * Drawn directly over content using a translated [LinearGradient] in `drawPath`, avoiding both
 * an extra child composable and GPU stencil `clipPath` passes.
 */
internal fun Modifier.glassSheen(motion: GlassMotion, shine: GlassShine, shape: Shape): Modifier = drawWithCache {
    val key = SheenOutlineKey(size.width.roundToInt(), size.height.roundToInt(), shape, layoutDirection)
    val outline = SheenOutlines.get(key) ?: Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
    }.asAndroidPath().also { SheenOutlines.put(key, it) }

    val reach = size.minDimension * BAND_SHARE
    val askew = reach * BAND_SLANT
    val middle = Offset(size.width / 2f, size.height / 2f)
    val along = if (motion.horizontal) Offset(reach, askew) else Offset(askew, reach)
    val start = middle - along
    val end = middle + along
    val shader = LinearGradient(start.x, start.y, end.x, end.y, SHEEN_COLORS, SHEEN_STOPS, Shader.TileMode.CLAMP)
    val matrix = Matrix()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

    onDrawWithContent {
        drawContent()
        val lean = motion.lean
        val strength = min(1f, abs(lean) * BAND_GAIN) * shine.glow
        if (strength > 0.004f) {
            val travel = lean * BAND_TRAVEL * (if (motion.horizontal) size.width else size.height)
            matrix.setTranslate(if (motion.horizontal) travel else 0f, if (motion.horizontal) 0f else travel)
            shader.setLocalMatrix(matrix)
            paint.alpha = (strength * 255f).roundToInt().coerceIn(0, 255)
            drawContext.canvas.nativeCanvas.drawPath(outline, paint)
        }
    }
}

/** Half the width of the band, as a share of the icon. */
private const val BAND_SHARE = 0.3f

/** How far the band is turned from square to the movement (about 20 degrees). */
private const val BAND_SLANT = 0.36f

/** The band is at its brightest well before the icons lean all the way. */
private const val BAND_GAIN = 1.6f

/** How far across the icon the band is carried at full lean, as a share of the icon. */
private const val BAND_TRAVEL = 0.9f
