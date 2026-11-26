package io.github.johankrs.kiri.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import io.github.johankrs.kiri.ui.settings.StepValueRoom
import io.github.johankrs.kiri.ui.theme.GlassSpec
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.VIVID_GLASS_OPACITY
import io.github.johankrs.kiri.ui.theme.kiriGlass
import io.github.johankrs.kiri.ui.theme.spec
import kotlin.math.roundToInt

/** One thing that can be done with an item, shown as a row of an [ItemMenu]. */
@Immutable
class MenuAction(val label: String, val glyph: Glyph? = null, val onClick: () -> Unit)

/**
 * A value changed a step at a time, shown at the top of an [ItemMenu]: an amount with minus and
 * plus (the width of a widget), or, when [named], one of a few names gone through with arrows
 * (the glass of a folder). A null step means that end was reached.
 *
 * [values] are all it can show. The value takes the width of the widest of them, in every
 * stepper of the menu, so nothing moves as it changes.
 */
@Immutable
class MenuStepper(
    val label: String,
    val valueText: String,
    val onDecrease: (() -> Unit)?,
    val onIncrease: (() -> Unit)?,
    val values: List<String> = emptyList(),
    val named: Boolean = false,
)

private val MenuGlass = GlassStyle.FUME

/** Vivid glass: the menu takes on the colour of the wallpaper behind it. */
private const val MENU_OPACITY_PERCENT = VIVID_GLASS_OPACITY
private val MenuShape = RoundedCornerShape(24.dp)
private val ActionShape = RoundedCornerShape(16.dp)

/**
 * The menu that opens with a long press on an item: a small pane of glass right next to the
 * item, above it when there is room and below it otherwise. A tap anywhere else, or Back,
 * closes it. New actions are new rows.
 *
 * [anchor] is where the item is on screen, in root coordinates; the menu must fill the same
 * root. [header] draws the item's picture next to [title]. [quickActions] go side by side in a
 * row at the bottom, a glyph over a short name each: what the system does with an app, kept
 * apart from what Kiri does with it.
 *
 * It grows out of the item when it appears. To close it, turn [visible] off: it shrinks back,
 * stops answering touches meanwhile, and reports [onHidden] when it is gone and can be removed.
 */
@Composable
fun ItemMenu(
    title: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    anchor: Rect? = null,
    steppers: List<MenuStepper> = emptyList(),
    quickActions: List<MenuAction> = emptyList(),
    visible: Boolean = true,
    onHidden: () -> Unit = {},
    speed: Float = 1f,
    header: (@Composable () -> Unit)? = null,
) {
    val glass = MenuGlass.spec(MENU_OPACITY_PERCENT)
    val dismiss by rememberUpdatedState(onDismiss)
    val hidden by rememberUpdatedState(onHidden)
    BackHandler(enabled = visible, onBack = onDismiss)

    val appear = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        val stiffness = (if (visible) Spring.StiffnessMediumLow else Spring.StiffnessMedium) * speed
        appear.animateTo(if (visible) 1f else 0f, spring(stiffness = stiffness, visibilityThreshold = 0.001f))
        if (!visible) hidden()
    }
    // 1 when the menu sits above the item (it then grows upwards from its bottom edge), 0 below.
    var growsFrom by remember { mutableFloatStateOf(1f) }
    val safeArea = WindowInsets.safeDrawing
    val layoutDirection = LocalLayoutDirection.current

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = 0.22f))
            .then(if (visible) Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } } else Modifier),
        content = {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        val scale = 0.86f + 0.14f * appear.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, growsFrom)
                    }
                    .kiriGlass(MenuGlass, MenuShape, MENU_OPACITY_PERCENT, vivid = true)
                    // Taps on the pane itself must not reach the "close" area behind it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (header != null) {
                        Box(Modifier.size(32.dp)) { header() }
                        Spacer(Modifier.width(10.dp))
                    }
                    BasicText(text = title, style = KiriType.body.copy(color = glass.content))
                }

                if (steppers.isNotEmpty()) {
                    // One width for the values of every stepper: the widest any of them can show.
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val valueStyle = KiriType.value
                    val valueWidth = remember(steppers, measurer, density) {
                        val widest = steppers.flatMap { it.values + it.valueText }.maxOfOrNull { text ->
                            measurer.measure(text, valueStyle, maxLines = 1).size.width
                        } ?: 0
                        with(density) { widest.toDp() }.coerceAtLeast(StepValueRoom)
                    }
                    Spacer(Modifier.height(4.dp))
                    for (stepper in steppers) {
                        StepperRow(stepper, glass, valueWidth, enabled = visible)
                    }
                }

                MenuDivider(glass)

                for (action in actions) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(ActionShape)
                            .clickable(enabled = visible, role = Role.Button, onClick = action.onClick)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        val glyph = action.glyph
                        if (glyph != null) {
                            GlyphIcon(glyph, glass.contentMuted)
                            Spacer(Modifier.width(12.dp))
                        }
                        BasicText(text = action.label, style = KiriType.body.copy(color = glass.content))
                    }
                }

                if (quickActions.isNotEmpty()) {
                    if (actions.isNotEmpty()) MenuDivider(glass)
                    Row(Modifier.fillMaxWidth()) {
                        for (action in quickActions) {
                            QuickAction(action, glass, enabled = visible, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        // The part of the screen the menu may use: clear of the system bars, with a small margin.
        val left = safeArea.getLeft(this, layoutDirection) + margin
        val top = safeArea.getTop(this) + margin
        val right = constraints.maxWidth - safeArea.getRight(this, layoutDirection) - margin
        val bottom = constraints.maxHeight - safeArea.getBottom(this) - margin
        val roomWidth = (right - left).coerceAtLeast(0)
        val roomHeight = (bottom - top).coerceAtLeast(0)

        val pane = measurables.first().measure(
            Constraints(
                minWidth = minOf(220.dp.roundToPx(), roomWidth),
                maxWidth = minOf(320.dp.roundToPx(), roomWidth),
                maxHeight = roomHeight,
            ),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val item = anchor ?: Rect(
                left = constraints.maxWidth / 2f,
                top = bottom.toFloat(),
                right = constraints.maxWidth / 2f,
                bottom = bottom.toFloat(),
            )
            val x = (item.center.x - pane.width / 2f).roundToInt()
                .coerceIn(left, (right - pane.width).coerceAtLeast(left))
            val above = item.top.roundToInt() - gap - pane.height
            val below = item.bottom.roundToInt() + gap
            val y = when {
                above >= top -> above
                below + pane.height <= bottom -> below
                // No room on either side of the item: stay on screen, as near to it as possible.
                else -> above.coerceIn(top, (bottom - pane.height).coerceAtLeast(top))
            }
            growsFrom = if (y == above) 1f else 0f
            pane.place(x, y)
        }
    }
}

/** The faint line between the parts of the menu. */
@Composable
private fun MenuDivider(glass: GlassSpec) {
    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(glass.divider),
    )
}

/** One of the actions side by side at the bottom of the menu: its glyph over a short name. */
@Composable
private fun QuickAction(action: MenuAction, glass: GlassSpec, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(ActionShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        val glyph = action.glyph
        if (glyph != null) {
            GlyphIcon(glyph, glass.content)
            Spacer(Modifier.height(6.dp))
        }
        BasicText(
            text = action.label,
            style = KiriType.caption.copy(color = glass.content, textAlign = TextAlign.Center),
        )
    }
}


/**
 * A label, then minus, the value and plus; or, for names, an arrow either side of the value.
 * The value is [valueWidth] wide and centred in it.
 */
@Composable
private fun StepperRow(stepper: MenuStepper, glass: GlassSpec, valueWidth: Dp, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
    ) {
        BasicText(
            text = stepper.label,
            style = KiriType.body.copy(color = glass.content),
            modifier = Modifier.weight(1f),
        )
        MenuStepButton(
            glyph = if (stepper.named) Glyph.BACK else Glyph.MINUS,
            description = stringResource(
                if (stepper.named) R.string.action_previous else R.string.action_decrease,
                stepper.label,
            ),
            glass = glass,
            onStep = stepper.onDecrease.takeIf { enabled },
        )
        BasicText(
            text = stepper.valueText,
            style = KiriType.value.copy(color = glass.content),
            maxLines = 1,
            modifier = Modifier.width(valueWidth),
        )
        MenuStepButton(
            glyph = if (stepper.named) Glyph.FORWARD else Glyph.PLUS,
            description = stringResource(
                if (stepper.named) R.string.action_next else R.string.action_increase,
                stepper.label,
            ),
            glass = glass,
            onStep = stepper.onIncrease.takeIf { enabled },
        )
    }
}

@Composable
private fun MenuStepButton(glyph: Glyph, description: String, glass: GlassSpec, onStep: (() -> Unit)?) {
    val step by rememberUpdatedState(onStep)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = onStep != null, role = Role.Button) { step?.invoke() }
            .semantics { contentDescription = description },
    ) {
        GlyphIcon(glyph, if (onStep != null) glass.content else glass.contentMuted.copy(alpha = 0.35f))
    }
}
