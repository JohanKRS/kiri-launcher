package io.github.johankrs.kiri.widgets.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.market.MarketSettings
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import io.github.johankrs.kiri.ui.theme.GlassSpec
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.kiriGlass
import io.github.johankrs.kiri.ui.theme.spec

/** The typeface is the app's, whatever it is: only sizes and weights are chosen here. */
internal val MarketFont = KiriType.body.fontFamily

internal val PaneNumbers = TextStyle(fontFamily = MarketFont, fontFeatureSettings = "tnum")
internal val PaneHeading =
    TextStyle(fontFamily = MarketFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
internal val PaneLabel = TextStyle(fontFamily = MarketFont, fontSize = 12.sp, lineHeight = 16.sp)
internal val PaneStrong = PaneNumbers.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
private val Choice = PaneNumbers.copy(fontSize = 14.sp, lineHeight = 20.sp)

/** The glass of a pane has to stay readable over whatever is behind it. */
internal const val PANE_MIN_OPACITY_PERCENT = 82

private val PaneShape = RoundedCornerShape(PaneCorner)
private val ChoiceShape = RoundedCornerShape(12.dp)

/** The colours of the controls on a pane's glass. */
@Immutable
internal class PaneLook(glass: GlassSpec) {
    val content: Color = glass.content
    val muted: Color = glass.contentMuted
    /** Behind a field where something is typed: the only thing on a pane with a ground of its own. */
    val field: Color = glass.content.copy(alpha = 0.12f)
}

/**
 * What every pane is made of: the glass, a heading that stays in place with the way back and
 * the button that asks for the prices again, and a [body] under it. The pane is as tall as
 * what it holds; where the screen is shorter than that, the body scrolls and the heading stays.
 */
@Composable
internal fun PaneFrame(
    settings: MarketSettings,
    title: String,
    subtitle: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.(glass: GlassSpec, look: PaneLook) -> Unit,
) {
    val opacity = maxOf(settings.opacityPercent, PANE_MIN_OPACITY_PERCENT)
    val glass = settings.glass.spec(opacity)
    val look = remember(glass) { PaneLook(glass) }
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .kiriGlass(settings.glass, PaneShape, opacity),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // A line under the heading while something is scrolled away behind it.
                .drawBehind {
                    if (scroll.value > 0) {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(glass.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        ) {
            val back = stringResource(R.string.action_back)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // The tip of the arrow stands where the text under it begins.
                    .hang(over = (IconTarget - 24.dp) / 2 + 6.7.dp, atStart = true)
                    .size(IconTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = back },
            ) {
                GlyphIcon(Glyph.BACK, glass.content, Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                BasicText(text = title, style = PaneHeading.copy(color = glass.content))
                if (subtitle.isNotEmpty()) {
                    BasicText(text = subtitle, style = PaneLabel.copy(color = glass.contentMuted))
                }
            }
            Spacer(Modifier.width(4.dp))
            val refresh = stringResource(R.string.market_refresh_now)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // The arrows end where the text under them ends.
                    .hang(over = (IconTarget - 18.dp) / 2 + 2.3.dp, atStart = false)
                    .size(IconTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRefresh)
                    .semantics { contentDescription = refresh },
            ) {
                RefreshArrows(turning = refreshing, color = glass.content)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                // All the height the heading leaves, and no more than the body needs.
                .weight(1f, fill = false)
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            body(glass, look)
        }
    }
}

/**
 * Choices on a pane's glass where exactly one is on. There is no button behind them: the one
 * that is on is written heavier and has a short line under it, and the rest are quieter.
 */
@Composable
internal fun <T> ChoiceLine(
    options: List<Pair<T, String>>,
    selected: T,
    look: PaneLook,
    onSelect: (T) -> Unit,
) {
    ChoiceFlow(
        slots = 0,
        gap = 2.dp,
        // The words of the first choice line up with what is above and below them.
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = -ChoiceInset),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(ChoiceShape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = ChoiceInset),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Keeps the room of the heavier writing, so choosing never moves the others.
                    BasicText(
                        text = label,
                        style = Choice.copy(fontWeight = FontWeight.SemiBold, color = Color.Transparent),
                        softWrap = false,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    BasicText(
                        text = label,
                        style = Choice.copy(
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) look.content else look.muted,
                        ),
                        softWrap = false,
                    )
                }
                Box(
                    Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(if (on) look.content else Color.Transparent),
                )
            }
        }
    }
}

private val ChoiceInset = 10.dp

/** The room a finger gets around a mark that has no button drawn behind it. */
internal val IconTarget = 44.dp

/**
 * For a mark with nothing drawn behind it: what is seen of it stands flush with the text around
 * it, and the room for a finger, which is wider than the mark, hangs [over] into the margin
 * instead of pushing the mark inwards. [over] is how far the mark is from the edge of its room.
 */
internal fun Modifier.hang(over: Dp, atStart: Boolean): Modifier = this
    .width(IconTarget - over)
    .wrapContentWidth(if (atStart) Alignment.End else Alignment.Start, unbounded = true)

/**
 * Lays choices side by side so that no word is ever broken in two.
 *
 * With [slots] at zero each choice is as wide as its own words and they follow one another from
 * the start of the line. With [slots] given they fill the line: while every choice fits in an
 * even share of it, all get the same width (the line is split in [slots] shares, so a line with
 * fewer choices matches a full one); when one needs more than its share, each gets the width of
 * its own words and what is left over is shared out. Either way, choices that do not fit in one
 * line go on to the next, whole.
 */
@Composable
internal fun ChoiceFlow(slots: Int, gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) { }
        val gapPx = gap.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            natural.sum() + gapPx * (natural.size - 1)
        }

        val packed = slots <= 0
        val shares = maxOf(slots, measurables.size)
        val even = (width - gapPx * (shares - 1)) / shares
        // Which choices are on each line, and how wide each one is.
        val lines = mutableListOf<List<Int>>()
        val widths = IntArray(measurables.size)
        if (!packed && natural.all { it <= even }) {
            lines += measurables.indices.toList()
            widths.fill(even)
        } else {
            var line = mutableListOf<Int>()
            var used = 0
            for (index in measurables.indices) {
                val needed = natural[index] + if (line.isEmpty()) 0 else gapPx
                if (line.isNotEmpty() && used + needed > width) {
                    lines += line
                    line = mutableListOf()
                    used = 0
                }
                used += natural[index] + if (line.isEmpty()) 0 else gapPx
                line += index
            }
            lines += line
            for (members in lines) {
                val spare = width - members.sumOf { natural[it] } - gapPx * (members.size - 1)
                // A choice wider than the whole line is the only one that has to wrap.
                val extra = if (packed) 0 else spare / members.size
                for (index in members) widths[index] = (natural[index] + extra).coerceAtMost(width)
            }
        }

        val heights = lines.map { members -> members.maxOf { measurables[it].minIntrinsicHeight(widths[it]) } }
        val placeables = lines.mapIndexed { row, members ->
            members.map { measurables[it].measure(Constraints.fixed(widths[it], heights[row])) }
        }
        val height = heights.sum() + gapPx * (lines.size - 1)
        layout(width, height.coerceIn(constraints.minHeight, maxOf(constraints.minHeight, constraints.maxHeight))) {
            var y = 0
            placeables.forEachIndexed { row, members ->
                var x = 0
                for (placeable in members) {
                    placeable.placeRelative(x, y)
                    x += placeable.width + gapPx
                }
                y += heights[row] + gapPx
            }
        }
    }
}
