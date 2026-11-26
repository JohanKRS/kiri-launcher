package io.github.johankrs.kiri.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What is being pressed on a pane of glass, for the pane to light up.
 *
 * Something pressable on a pane seldom reaches the pane's edge: there is a margin around what
 * is written. Lit up on its own it is a rectangle that stops short of the edge with a straight
 * cut. So the pane does the lighting, with [panePress]: the lit part is stretched to every edge
 * of the pane it is close to and takes the pane's own outline there, rounded corners included.
 * A side that ends in the middle of the pane gets softly rounded corners of its own.
 *
 * Whatever is pressable says where it is: Kiri's own controls through [PanePressIndication],
 * the views of another app's widget by calling [down] and [up] themselves.
 */
@Stable
class PanePress internal constructor(private val scope: CoroutineScope) {
    /** The pressed part, in the pane's own coordinates. */
    internal var area by mutableStateOf(Rect.Zero)
        private set

    /** How lit it is, 0 to 1. */
    internal val glow = Animatable(0f)

    /** Where the pane is, for whatever is on it to say where it is itself. */
    internal var coordinates: LayoutCoordinates? = null

    /**
     * True where a finger may be about to scroll what it touches: a press is then lit up a
     * moment late, so that the start of a scroll lights nothing.
     */
    var waits: Boolean = false

    private var job: Job? = null

    /** Where the finger is, until it is lit: a press may wait, see [waits]. */
    private var pending: Rect? = null

    /** A finger went down on [area], given in the pane's coordinates. */
    fun down(area: Rect) {
        job?.cancel()
        pending = area
        job = scope.launch {
            if (waits) delay(WAIT_MS)
            light()
            glow.animateTo(1f, tween(IN_MS))
        }
    }

    /**
     * The finger is off again. A press that was [completed], a tap, is shown in full before
     * it fades, however short it was; one that was given up fades from where it got to.
     */
    fun up(completed: Boolean) {
        job?.cancel()
        job = scope.launch {
            if (completed) {
                light()
                if (glow.value < 1f) glow.animateTo(1f, tween(IN_MS))
            }
            pending = null
            glow.animateTo(0f, tween(OUT_MS, easing = LinearOutSlowInEasing))
        }
    }

    private fun light() {
        area = pending ?: return
        pending = null
    }

    private companion object {
        const val WAIT_MS = 70L
        const val IN_MS = 90
        const val OUT_MS = 260
    }
}

@Composable
fun rememberPanePress(): PanePress {
    val scope = rememberCoroutineScope()
    return remember(scope) { PanePress(scope) }
}

/** The pane whatever is pressed belongs to, if it lights its presses itself. */
val LocalPanePress = compositionLocalOf<PanePress?> { null }

/**
 * Lights up what [press] says is pressed, over what the pane shows. It goes after the pane's
 * glass and its clip, so the light takes the pane's outline, and before any padding, so it
 * reaches the edge.
 */
fun Modifier.panePress(press: PanePress, color: Color, reach: Dp = PressReach): Modifier = this
    .onPlaced { press.coordinates = it }
    .drawWithContent {
        drawContent()
        val glow = press.glow.value
        if (glow <= 0f) return@drawWithContent
        val area = press.area
        val near = reach.toPx()
        // Each side goes to the pane's edge when it is close to it.
        val left = if (area.left <= near) 0f else area.left
        val top = if (area.top <= near) 0f else area.top
        val right = if (size.width - area.right <= near) size.width else area.right
        val bottom = if (size.height - area.bottom <= near) size.height else area.bottom
        if (right <= left || bottom <= top) return@drawWithContent
        val lit = color.copy(alpha = color.alpha * PRESS_ALPHA * glow)
        // A corner between two sides that end inside the pane is rounded; one on an edge is
        // the edge's.
        val soft = CornerRadius(minOf(PressCorner.toPx(), (right - left) / 2f, (bottom - top) / 2f))
        val square = CornerRadius.Zero
        val freeLeft = left > 0f
        val freeTop = top > 0f
        val freeRight = right < size.width
        val freeBottom = bottom < size.height
        val anyRounded = (freeLeft || freeRight) && (freeTop || freeBottom)
        if (!anyRounded) {
            drawRect(lit, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
        } else {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        topLeft = if (freeLeft && freeTop) soft else square,
                        topRight = if (freeRight && freeTop) soft else square,
                        bottomRight = if (freeRight && freeBottom) soft else square,
                        bottomLeft = if (freeLeft && freeBottom) soft else square,
                    ),
                )
            }
            drawPath(path, lit)
        }
    }

/** A side of what is pressed that is this close to the pane's edge is taken to the edge. */
val PressReach = 32.dp

private val PressCorner = 14.dp

/** How much of the colour of the writing a full press puts over the glass. */
private const val PRESS_ALPHA = 0.14f

/**
 * For Kiri's own controls on a pane with [panePress]: instead of lighting up themselves, they
 * tell the pane where they are. On a pane without it nothing is lit.
 */
object PanePressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PanePressNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0x50414e45
}

private class PanePressNode(private val source: InteractionSource) :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode {
    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                val press = currentValueOf(LocalPanePress) ?: return@collect
                when (interaction) {
                    is PressInteraction.Press -> where(press)?.let(press::down)
                    is PressInteraction.Release -> press.up(completed = true)
                    is PressInteraction.Cancel -> press.up(completed = false)
                }
            }
        }
    }

    /** Where this is on the pane, or null while one of the two is not on the screen. */
    private fun where(press: PanePress): Rect? {
        val pane = press.coordinates?.takeIf { it.isAttached } ?: return null
        if (!isAttached) return null
        val own = requireLayoutCoordinates().takeIf { it.isAttached } ?: return null
        return pane.localBoundingBoxOf(own, clipBounds = false)
    }
}
