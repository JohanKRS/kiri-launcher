package io.github.johankrs.kiri.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * What a row or a button of the settings shows while it is pressed, or has the focus of a remote
 * or a keyboard.
 *
 * Pressed, a shade of [tint] goes over it: darker in the light theme, as the default of Compose
 * has always drawn it, and lighter in the dark one. With the focus, which only a remote or a
 * keyboard gives, it is also filled with [focusFill] and ringed in [ring], so that it can be
 * told from across a room on a TV, in either theme.
 */
class ShadeIndication(
    private val tint: Color,
    private val pressed: Float,
    private val focusFill: Color,
    private val ring: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ShadeNode(interactionSource, tint.copy(alpha = pressed), focusFill, ring)

    override fun equals(other: Any?): Boolean =
        other is ShadeIndication && other.tint == tint && other.pressed == pressed && other.focusFill == focusFill && other.ring == ring

    override fun hashCode(): Int = ((tint.hashCode() * 31 + pressed.hashCode()) * 31 + focusFill.hashCode()) * 31 + ring.hashCode()

    companion object {
        /** For [colors]: dark on light, light on dark, and the ring in the colour of the theme. */
        fun of(colors: KiriColors): ShadeIndication = if (colors.dark) {
            ShadeIndication(Color.White, pressed = 0.2f, focusFill = colors.accent.copy(alpha = 0.24f), ring = colors.accent)
        } else {
            ShadeIndication(Color.Black, pressed = 0.3f, focusFill = colors.accent.copy(alpha = 0.14f), ring = colors.accent)
        }
    }
}

/** How thick the ring around what has the focus is, and how far inside its edge. */
private val RingWidth = 2.5.dp
private val RingInset = 3.dp

/** The rounding of the ring: that of a block of settings, less the inset, so the two run together. */
private val RingCorner = 19.dp

/** Up to this size, a square thing is a round button, and its ring is a circle. */
private val RoundButton = 64.dp

private class ShadeNode(
    private val source: InteractionSource,
    private val pressedShade: Color,
    private val focusFill: Color,
    private val ring: Color,
) : Modifier.Node(), DrawModifierNode {
    private var presses = 0
    private var hovers = 0
    private var focuses = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers--
                    is FocusInteraction.Focus -> focuses++
                    is FocusInteraction.Unfocus -> focuses--
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val focused = focuses > 0
        when {
            presses > 0 -> drawRect(pressedShade, size = size)
            focused || hovers > 0 -> drawRect(focusFill, size = size)
        }
        if (!focused) return
        val width = RingWidth.toPx()
        val inset = RingInset.toPx() + width / 2
        val round = size.width <= RoundButton.toPx() && abs(size.width - size.height) < 2f
        if (round) {
            drawCircle(ring, radius = size.minDimension / 2 - inset, style = Stroke(width))
        } else {
            val corner = minOf(RingCorner.toPx(), size.height / 2 - inset)
            drawRoundRect(
                color = ring,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width),
            )
        }
    }
}
