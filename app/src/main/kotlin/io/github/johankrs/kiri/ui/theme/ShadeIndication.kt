package io.github.johankrs.kiri.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * What a row or a button of the settings shows while it is pressed, or has the focus of a remote
 * or a keyboard: a shade of [tint] over it. Darker in the light theme, as the default of Compose
 * has always drawn it; lighter in the dark one, where a darker shade would not be seen.
 */
class ShadeIndication(private val tint: Color, private val focused: Float, private val pressed: Float) :
    IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ShadeNode(interactionSource, tint.copy(alpha = focused), tint.copy(alpha = pressed))

    override fun equals(other: Any?): Boolean =
        other is ShadeIndication && other.tint == tint && other.focused == focused && other.pressed == pressed

    override fun hashCode(): Int = (tint.hashCode() * 31 + focused.hashCode()) * 31 + pressed.hashCode()

    companion object {
        /** For [KiriColors]: dark on light, light on dark. */
        fun of(dark: Boolean): ShadeIndication =
            if (dark) ShadeIndication(Color.White, focused = 0.12f, pressed = 0.2f) else ShadeIndication(Color.Black, focused = 0.1f, pressed = 0.3f)
    }
}

private class ShadeNode(
    private val source: InteractionSource,
    private val focusedShade: Color,
    private val pressedShade: Color,
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
        when {
            presses > 0 -> drawRect(pressedShade, size = size)
            hovers > 0 || focuses > 0 -> drawRect(focusedShade, size = size)
        }
    }
}
