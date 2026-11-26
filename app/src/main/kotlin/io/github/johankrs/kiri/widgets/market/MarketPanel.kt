package io.github.johankrs.kiri.widgets.market

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.market.MarketLayout
import io.github.johankrs.kiri.market.MarketPlan
import io.github.johankrs.kiri.market.MarketPlanner
import io.github.johankrs.kiri.market.MarketSettings
import io.github.johankrs.kiri.ui.theme.GlassSpec
import io.github.johankrs.kiri.ui.theme.kiriGlass
import io.github.johankrs.kiri.ui.theme.spec

/** Which way a price moved since the previous close. */
enum class Trend { UP, DOWN, FLAT, UNKNOWN }

/** The price of a stock before the open or after the close, already turned into text. */
@Immutable
data class ExtendedRow(
    /** "Pre" or "Post": for where room is short. */
    val label: String,
    /** "Pre-market" or "After hours". */
    val longLabel: String,
    val priceNumber: String,
    /** The change against the regular close it follows, in percent. */
    val changeText: String,
    val trend: Trend,
)

/** One line of the widget, already turned into text. */
@Immutable
data class MarketRow(
    val symbol: String,
    val name: String,
    /** Currency sign, shown apart from the number where there is room for a big number. */
    val priceSign: String,
    val priceNumber: String,
    /** The change in percent, or a short note when there is no price. */
    val changeText: String,
    val trend: Trend,
    /** Heights of the small chart, 0 to 1. Empty when there is no history. */
    val chart: List<Float>,
    /** The code of the currency this line is about, when it is one: tapping it opens the converter. */
    val currency: String? = null,
    /** The price outside regular hours, when there is one right now. */
    val extended: ExtendedRow? = null,
    /** The id of the stock this line is about, when it has a price: tapping it opens its details. */
    val stock: String? = null,
) {
    val priceText: String get() = if (priceSign.isEmpty()) priceNumber else "$priceSign $priceNumber"
}

private val Numbers = TextStyle(fontFamily = MarketFont, fontFeatureSettings = "tnum")
private val Strong = Numbers.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
private val Small = Numbers.copy(fontSize = 12.sp, lineHeight = 16.sp)
private val SmallStrong = Small.copy(fontWeight = FontWeight.Medium)
/** Words, as opposed to numbers: a code like VALE3 is read, not lined up, so its digits sit close. */
private fun TextStyle.words() = copy(fontFeatureSettings = null)

private val Title =
    TextStyle(fontFamily = MarketFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

/** The room the change gets at the end of a tight line of the list. */
private val DenseChangeWidth = 64.dp

/** Sizes the price of a single item may take, largest first. The last is the floor. */
private val HeroSizes = listOf(44f, 38f, 32f, 28f, 24f, 20f, 17f)

/** A price that stands alone keeps the digits' own widths; only prices in a column line up. */
private fun heroStyle(size: Float) = TextStyle(
    fontFamily = MarketFont,
    fontSize = size.sp,
    lineHeight = (size * 1.2f).sp,
    fontWeight = FontWeight.SemiBold,
)

/** Up and down are told apart by the arrow and the sign as well, never by colour alone. */
internal fun GlassStyle.trendColor(trend: Trend, glass: GlassSpec): Color = when (trend) {
    Trend.UP -> if (this == GlassStyle.NEVOA) Color(0xFF095436) else Color(0xFF7BE3B0)
    Trend.DOWN -> if (this == GlassStyle.NEVOA) Color(0xFF942B10) else Color(0xFFFFB199)
    Trend.FLAT, Trend.UNKNOWN -> glass.contentMuted
}

/**
 * The market widget itself: a pane of glass with prices, laid out for whatever space it is
 * given. It has no fixed sizes: [MarketPlanner] decides what fits, from one item with a large
 * price to a list in several columns.
 *
 * Text is never cut and never shrinks under its base size: names wrap, every line of a list
 * takes the height of the tallest, and only items that fit whole are shown. Spare space makes
 * everything a little larger instead.
 *
 * A tap on a currency opens the converter and a tap on a stock opens its details; the caller
 * shows them. A tap on the title, where there is room for one, asks for fresh prices, and so
 * does a tap on a line that failed to load. While the prices are on their way they fade a
 * little and the arrows by the title turn.
 *
 * The space must be bounded, and the panel cannot be asked for its intrinsic size.
 */
@Composable
fun MarketPanel(
    settings: MarketSettings,
    rows: List<MarketRow>,
    updatedText: String?,
    modifier: Modifier = Modifier,
    /** Called with the currency's code when a currency is tapped. Null leaves currencies inert. */
    onCurrencyClick: ((String) -> Unit)? = null,
    /** Called with the stock's id when a stock is tapped. Null leaves stocks inert. */
    onStockClick: ((String) -> Unit)? = null,
    /** True while prices asked for by hand are on their way. */
    refreshing: Boolean = false,
    /** Called when the person asks for fresh prices. Null leaves the title inert. */
    onRefresh: (() -> Unit)? = null,
) {
    val glass = settings.glass.spec(settings.opacityPercent)
    val shape = remember(settings.cornerDp) { RoundedCornerShape(settings.cornerDp.dp) }

    BoxWithConstraints(modifier = modifier.kiriGlass(settings.glass, shape, settings.opacityPercent)) {
        if (rows.isEmpty()) {
            BasicText(
                text = stringResource(R.string.market_empty),
                style = Small.copy(color = glass.contentMuted, textAlign = TextAlign.Center),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp),
            )
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        // Larger system text counts as less room, so the same rules hold for every font size.
        val width = (if (constraints.hasBoundedWidth) maxWidth.value else 390f) / density.fontScale
        val height = (if (constraints.hasBoundedHeight) maxHeight.value else 290f) / density.fontScale
        val plan = remember(width, height, rows, settings.showChart, density) {
            MarketPlanner.plan(
                width = width,
                height = height,
                itemCount = rows.size,
                chartWanted = settings.showChart,
                extended = rows.any { it.extended != null },
            ) { nameWidth -> tallestLine(rows, nameWidth, density, measurer) }
        }
        // Everything inside grows together: text, spacing and chart.
        val scaled = remember(density, plan.scalePercent) {
            Density(density.density * plan.scalePercent / 100f, density.fontScale)
        }
        // The prices step back while new ones are on their way, read only during draw.
        val presence = animateFloatAsState(
            targetValue = if (refreshing) 0.5f else 1f,
            animationSpec = tween(durationMillis = 220),
            label = "prices",
        )
        val fading = remember(presence) { Modifier.graphicsLayer { alpha = presence.value } }
        val taps = RowTaps(onCurrencyClick, onStockClick, onRefresh)
        CompositionLocalProvider(LocalDensity provides scaled) {
            when (plan.layout) {
                MarketLayout.SINGLE -> SingleItem(
                    row = rows.first(),
                    showExtended = plan.showExtended,
                    settings = settings,
                    glass = glass,
                    tap = rows.first().tap(taps),
                    fading = fading,
                )
                MarketLayout.TILES -> Tiles(rows, plan, settings, glass, taps, fading)
                MarketLayout.LIST -> QuoteList(rows, plan, settings, glass, updatedText, taps, refreshing, fading)
            }
        }
    }
}

/** What taps on the widget do. Whatever is null leaves its part inert. */
@Immutable
private data class RowTaps(
    val onCurrencyClick: ((String) -> Unit)?,
    val onStockClick: ((String) -> Unit)?,
    val onRefresh: (() -> Unit)?,
)

/**
 * What a tap on this line does: a currency opens the converter, a stock opens its details,
 * and a line without a price asks for it again.
 */
@Composable
private fun MarketRow.tap(taps: RowTaps): Modifier {
    val code = currency
    val id = stock
    val onCurrencyClick = taps.onCurrencyClick
    val onStockClick = taps.onStockClick
    val onRefresh = taps.onRefresh
    return when {
        code != null && onCurrencyClick != null -> Modifier.clickable(
            onClickLabel = stringResource(R.string.market_converter_open, symbol),
            role = Role.Button,
            onClick = { onCurrencyClick(code) },
        )
        id != null && onStockClick != null -> Modifier.clickable(
            onClickLabel = stringResource(R.string.market_detail_open, symbol),
            role = Role.Button,
            onClick = { onStockClick(id) },
        )
        code == null && id == null && onRefresh != null -> Modifier.clickable(
            onClickLabel = stringResource(R.string.market_refresh_now),
            role = Role.Button,
            onClick = onRefresh,
        )
        else -> Modifier
    }
}

/**
 * Height of the tallest line of the list when names have [nameWidth] to wrap in, in the
 * planner's units (dp at the base text size). The line for the price outside regular hours
 * is the planner's to add.
 */
private fun tallestLine(rows: List<MarketRow>, nameWidth: Float, density: Density, measurer: TextMeasurer): Float {
    val widthPx = (nameWidth * density.fontScale * density.density).toInt().coerceAtLeast(1)
    val tallestName = rows.maxOf { row ->
        if (row.name.isEmpty()) {
            0
        } else {
            measurer.measure(row.name, Small, constraints = Constraints(maxWidth = widthPx)).size.height
        }
    }
    val nameHeight = tallestName / density.density / density.fontScale
    // A line is the symbol over the name, or the price over its change, whichever is taller.
    return maxOf(20f + nameHeight, 36f) + 14f
}

@Composable
private fun QuoteList(
    rows: List<MarketRow>,
    plan: MarketPlan,
    settings: MarketSettings,
    glass: GlassSpec,
    updatedText: String?,
    taps: RowTaps,
    refreshing: Boolean,
    fading: Modifier,
) {
    val onRefresh = taps.onRefresh
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MarketPlanner.LIST_PADDING_H.dp, vertical = MarketPlanner.LIST_PADDING_V.dp),
    ) {
        if (plan.showHeader) {
            // The whole title is the button, so it is easy to hit; the arrows say what it does.
            val refresh = if (onRefresh == null) {
                Modifier
            } else {
                Modifier.clickable(
                    onClickLabel = stringResource(R.string.market_refresh_now),
                    role = Role.Button,
                    onClick = onRefresh,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(refresh)
                    .padding(top = 6.dp, bottom = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(text = stringResource(R.string.market_title), style = Title.copy(color = glass.content))
                    if (updatedText != null) {
                        BasicText(text = updatedText, style = Small.copy(color = glass.contentMuted))
                    }
                }
                if (onRefresh != null) {
                    Spacer(Modifier.width(10.dp))
                    RefreshArrows(turning = refreshing, color = glass.contentMuted)
                }
            }
        }
        EqualGrid(
            maxColumns = plan.columns,
            across = false,
            gap = MarketPlanner.COLUMN_GAP.dp,
            lineColor = glass.divider,
            topLine = plan.showHeader,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(fading),
        ) {
            for (row in rows) {
                val extended = row.extended.takeIf { plan.showExtended }
                val tap = row.tap(taps)
                if (plan.dense) {
                    DenseRow(row, plan.showChart, extended, settings, glass, tap)
                } else {
                    QuoteRow(row, plan.showChart, extended, settings, glass, tap)
                }
            }
        }
    }
}

/** Where [EqualGrid] put its cells, so the lines between them can be drawn. */
private data class GridGeometry(
    val columns: Int = 1,
    val rows: Int = 1,
    val shown: Int = 0,
    val cellWidth: Int = 0,
    val cellHeight: Int = 0,
    val gap: Int = 0,
    val top: Int = 0,
)

/**
 * Lays its children out as cells of one size, as tall as the tallest child, and shows only the
 * children that fit whole. Cells share the height evenly, up to a point: a few items in a tall
 * space stay together instead of drifting apart.
 *
 * With [across] the children fill a row before starting the next (tiles); without it they run
 * down a column, in as few columns as show the most children (a list).
 */
@Composable
private fun EqualGrid(
    maxColumns: Int,
    across: Boolean,
    gap: Dp,
    lineColor: Color,
    topLine: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var geometry by remember { mutableStateOf(GridGeometry()) }
    Layout(
        content = content,
        modifier = modifier.drawBehind {
            val g = geometry
            val stroke = 1.dp.toPx()
            for (index in 0 until g.shown) {
                val column = if (across) index % g.columns else index / g.rows
                val row = if (across) index / g.columns else index % g.rows
                val x = column * (g.cellWidth + g.gap).toFloat()
                val y = g.top + row * g.cellHeight.toFloat()
                if (row > 0 || topLine) {
                    drawLine(lineColor, Offset(x, y), Offset(x + g.cellWidth, y), strokeWidth = stroke)
                }
                if (across && column > 0) {
                    drawLine(lineColor, Offset(x, y), Offset(x, y + g.cellHeight), strokeWidth = stroke)
                }
            }
        },
    ) { measurables, constraints ->
        val count = measurables.size
        val width = constraints.maxWidth
        if (count == 0) {
            geometry = GridGeometry()
            return@Layout layout(width, constraints.minHeight) {}
        }
        val gapPx = gap.roundToPx()
        val bounded = constraints.hasBoundedHeight

        val most = minOf(maxColumns, count).coerceAtLeast(1)
        val tallestCache = IntArray(most + 1) { -1 }
        val shownCache = IntArray(most + 1) { -1 }
        fun cellWidth(columns: Int) = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(1)
        fun tallest(columns: Int): Int {
            val cached = tallestCache[columns]
            if (cached >= 0) return cached
            val cw = cellWidth(columns)
            return measurables.maxOf { it.maxIntrinsicHeight(cw) }.coerceAtLeast(1).also { tallestCache[columns] = it }
        }
        fun shown(columns: Int): Int {
            val cached = shownCache[columns]
            if (cached >= 0) return cached
            val rowsFit = if (bounded) (constraints.maxHeight / tallest(columns)).coerceAtLeast(1) else count
            return minOf(count, columns * rowsFit).also { shownCache[columns] = it }
        }

        val columns = if (across) {
            most
        } else {
            val best = (1..most).maxOf { shown(it) }
            (1..most).first { shown(it) == best }
        }
        val shown = shown(columns)
        val rows = (shown + columns - 1) / columns
        val cellWidth = cellWidth(columns)
        val tallest = tallest(columns)
        val cellHeight = if (bounded) {
            maxOf(tallest, minOf(constraints.maxHeight / rows, tallest * 8 / 5))
        } else {
            tallest
        }
        val height = if (bounded) constraints.maxHeight else cellHeight * rows
        // Under a title the cells start at the top; alone they sit in the middle.
        val top = if (topLine) 0 else ((height - cellHeight * rows) / 2).coerceAtLeast(0)
        geometry = GridGeometry(columns, rows, shown, cellWidth, cellHeight, gapPx, top)

        val placeables = measurables.take(shown).map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val column = if (across) index % columns else index / rows
                val row = if (across) index / columns else index % rows
                placeable.place(column * (cellWidth + gapPx), top + row * cellHeight)
            }
        }
    }
}

/**
 * A line of the list: symbol over name, the chart, price over change. Under them, when the
 * stock is trading outside regular hours, [extended]: that price and its own change.
 */
@Composable
private fun QuoteRow(
    row: MarketRow,
    chart: Boolean,
    extended: ExtendedRow?,
    settings: MarketSettings,
    glass: GlassSpec,
    tap: Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(tap)
            .padding(vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(text = row.symbol, style = Strong.words().copy(color = glass.content))
                if (row.name.isNotEmpty()) {
                    BasicText(text = row.name, style = Small.words().copy(color = glass.contentMuted))
                }
            }
            if (chart) {
                Spacer(Modifier.width(8.dp))
                Sparkline(row.chart, glass.contentMuted, Modifier.size(width = 56.dp, height = 26.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 84.dp)) {
                BasicText(text = row.priceText, style = Strong.copy(color = glass.content, textAlign = TextAlign.End))
                ChangeLine(row.changeText, row.trend, settings.glass, glass)
            }
        }
        if (extended != null) ExtendedWide(extended, 0.dp, settings.glass, glass)
    }
}

/** A line of the list with everything on one row of text, for when height is short. */
@Composable
private fun DenseRow(
    row: MarketRow,
    chart: Boolean,
    extended: ExtendedRow?,
    settings: MarketSettings,
    glass: GlassSpec,
    tap: Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(tap)
            .padding(vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = row.symbol, style = Strong.words().copy(color = glass.content), modifier = Modifier.weight(1f))
            if (chart) {
                Spacer(Modifier.width(8.dp))
                Sparkline(row.chart, glass.contentMuted, Modifier.size(width = 56.dp, height = 20.dp))
            }
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = row.priceText,
                style = Strong.copy(color = glass.content, textAlign = TextAlign.End),
                modifier = Modifier.widthIn(min = 84.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.widthIn(min = DenseChangeWidth)) {
                ChangeLine(row.changeText, row.trend, settings.glass, glass)
            }
        }
        // The same column for the change, so the two prices end one under the other.
        if (extended != null) ExtendedWide(extended, DenseChangeWidth, settings.glass, glass)
    }
}

/**
 * The price outside regular hours across a whole line of the list: what it is on the left,
 * the price and its change on the right, under the regular ones. The change takes at least
 * [changeWidth].
 */
@Composable
private fun ExtendedWide(extended: ExtendedRow, changeWidth: Dp, style: GlassStyle, glass: GlassSpec) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = (MarketPlanner.EXTENDED_LINE - 16f).dp),
    ) {
        BasicText(
            text = extended.longLabel,
            style = Small.copy(color = glass.contentMuted),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(text = extended.priceNumber, style = SmallStrong.copy(color = glass.content))
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.widthIn(min = changeWidth)) {
            ChangeLine(extended.changeText, extended.trend, style, glass)
        }
    }
}

/**
 * The price outside regular hours where room is short: "Pre", an arrow for the way it moved
 * and the price. With [change] its percentage follows. In a space too narrow for all of it
 * the percentage goes first and then the word, so the number always stands whole.
 */
@Composable
private fun ExtendedShort(extended: ExtendedRow, change: Boolean, style: GlassStyle, glass: GlassSpec) {
    val withChange = change && extended.changeText.isNotEmpty()
    FirstThatFits {
        if (withChange) ExtendedShortLine(extended, label = true, change = true, style, glass)
        ExtendedShortLine(extended, label = true, change = false, style, glass)
        ExtendedShortLine(extended, label = false, change = false, style, glass)
    }
}

@Composable
private fun ExtendedShortLine(
    extended: ExtendedRow,
    label: Boolean,
    change: Boolean,
    style: GlassStyle,
    glass: GlassSpec,
) {
    val color = style.trendColor(extended.trend, glass)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label) {
            BasicText(text = extended.label, style = Small.copy(color = glass.contentMuted), softWrap = false)
            Spacer(Modifier.width(5.dp))
        }
        if (extended.trend == Trend.UP || extended.trend == Trend.DOWN) {
            TrendArrow(up = extended.trend == Trend.UP, color = color)
            Spacer(Modifier.width(4.dp))
        }
        BasicText(text = extended.priceNumber, style = SmallStrong.copy(color = glass.content), softWrap = false)
        if (change) {
            Spacer(Modifier.width(6.dp))
            BasicText(text = extended.changeText, style = SmallStrong.copy(color = color), softWrap = false)
        }
    }
}

/**
 * Shows the first of its children that fits the width on one line, so that a number is never
 * broken in two: what is written gets shorter instead. The children go from the fullest to
 * the barest. If even the barest is too wide, it is drawn a little smaller, whole.
 */
@Composable
internal fun FirstThatFits(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) { }
        val room = constraints.maxWidth
        val chosen = measurables.firstOrNull { it.maxIntrinsicWidth(Constraints.Infinity) <= room }
            ?: measurables.last()
        val placeable = chosen.measure(Constraints())
        val scale = if (placeable.width > room) room / placeable.width.toFloat() else 1f
        layout(minOf(placeable.width, room), placeable.height) {
            if (scale == 1f) {
                placeable.place(0, 0)
            } else {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            }
        }
    }
}

/** Small blocks side by side in a short space, or stacked in a narrow one. */
@Composable
private fun Tiles(
    rows: List<MarketRow>,
    plan: MarketPlan,
    settings: MarketSettings,
    glass: GlassSpec,
    taps: RowTaps,
    fading: Modifier,
) {
    EqualGrid(
        maxColumns = plan.columns,
        across = true,
        gap = 0.dp,
        lineColor = glass.divider,
        topLine = false,
        modifier = Modifier
            .fillMaxSize()
            .padding(MarketPlanner.TILES_PADDING.dp)
            .then(fading),
    ) {
        for (row in rows) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = row.tap(taps).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                if (plan.dense) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(text = row.symbol, style = Small.words().copy(color = glass.contentMuted))
                        Spacer(Modifier.width(6.dp))
                        ChangeLine(row.changeText, row.trend, settings.glass, glass)
                    }
                } else {
                    BasicText(text = row.symbol, style = Small.words().copy(color = glass.contentMuted))
                }
                // The narrowest tile may not hold a long price with its sign: the number comes first.
                FirstThatFits {
                    BasicText(text = row.priceText, style = Strong.copy(color = glass.content), softWrap = false)
                    BasicText(text = row.priceNumber, style = Strong.copy(color = glass.content), softWrap = false)
                }
                if (!plan.dense) ChangeLine(row.changeText, row.trend, settings.glass, glass)
                val extended = row.extended
                // A tile is narrow: the arrow says which way, the percentage stays out.
                if (plan.showExtended && extended != null) ExtendedShort(extended, false, settings.glass, glass)
            }
        }
    }
}

/** One item with its price as large as the space allows, down to a readable floor. */
@Composable
private fun SingleItem(
    row: MarketRow,
    showExtended: Boolean,
    settings: MarketSettings,
    glass: GlassSpec,
    tap: Modifier,
    fading: Modifier,
) {
    val extended = row.extended.takeIf { showExtended }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(tap)
            .then(fading)
            .padding(14.dp),
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val (showName, priceSize) = remember(row, extended, widthPx, heightPx, density) {
            fun line(sp: Float) = with(density) { sp.sp.toPx() }
            val nameHeight = if (row.name.isEmpty()) {
                0
            } else {
                measurer.measure(row.name, Small, constraints = Constraints(maxWidth = widthPx)).size.height
            }
            // The symbol, the currency sign and the change, plus a little air between the blocks.
            val fixed = line(20f) + (if (row.priceSign.isEmpty()) 0f else line(16f)) + line(16f) +
                (if (extended == null) 0f else line(MarketPlanner.EXTENDED_TILE_LINE)) +
                with(density) { 8.dp.toPx() }
            // The name gives way before the price drops under its floor.
            val showName = nameHeight > 0 && heightPx - fixed - nameHeight >= line(HeroSizes.last() * 1.2f)
            val room = heightPx - fixed - (if (showName) nameHeight else 0)
            val size = HeroSizes.firstOrNull { size ->
                val measured = measurer.measure(row.priceNumber, heroStyle(size), softWrap = false).size
                measured.width <= widthPx && measured.height <= room
            } ?: HeroSizes.last()
            showName to size
        }
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Column {
                BasicText(text = row.symbol, style = Strong.words().copy(color = glass.content))
                if (showName) {
                    BasicText(text = row.name, style = Small.words().copy(color = glass.contentMuted))
                }
            }
            Column {
                if (row.priceSign.isNotEmpty()) {
                    BasicText(text = row.priceSign, style = Small.copy(color = glass.contentMuted))
                }
                // Narrower than the smallest size needs: the number shrinks before it breaks.
                FirstThatFits {
                    BasicText(
                        text = row.priceNumber,
                        style = heroStyle(priceSize).copy(color = glass.content),
                        softWrap = false,
                    )
                }
            }
            Column {
                ChangeLine(row.changeText, row.trend, settings.glass, glass)
                if (extended != null) ExtendedShort(extended, true, settings.glass, glass)
            }
        }
    }
}

@Composable
internal fun ChangeLine(changeText: String, trend: Trend, style: GlassStyle, glass: GlassSpec) {
    val color = style.trendColor(trend, glass)
    val line = @Composable { number: Boolean, arrow: Boolean ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (arrow && (trend == Trend.UP || trend == Trend.DOWN)) {
                TrendArrow(up = trend == Trend.UP, color = color)
                Spacer(Modifier.width(4.dp))
            }
            if (changeText.isNotEmpty()) {
                BasicText(text = changeText, style = SmallStrong.copy(color = color), softWrap = !number)
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (trend == Trend.UNKNOWN) {
        // Without a trend the text is a note in words ("No connection"), which wraps like any other.
        line(false, false)
    } else {
        // A percentage is a number: where it does not fit, the arrow goes (the sign still says
        // which way) and then it gets smaller, but it is never broken in two.
        FirstThatFits {
            line(true, true)
            if (changeText.isNotEmpty()) line(true, false)
        }
    }
}

/**
 * Two arrows chasing each other in a circle: the sign for "ask again". While [turning] they
 * go round, and when it ends they finish the turn they are in instead of stopping short.
 */
@Composable
internal fun RefreshArrows(turning: Boolean, color: Color) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(turning) {
        if (turning) {
            while (true) {
                angle.animateTo(angle.value + 360f, tween(durationMillis = 900, easing = LinearEasing))
            }
        } else {
            val rest = (360f - angle.value % 360f) % 360f
            if (rest > 0f) {
                val millis = (900 * rest / 360f).toInt().coerceAtLeast(1)
                angle.animateTo(angle.value + rest, tween(durationMillis = millis, easing = LinearOutSlowInEasing))
            }
            angle.snapTo(0f)
        }
    }
    Spacer(
        Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = angle.value }
            .clearAndSetSemantics { }
            .drawWithCache {
                val stroke = 1.7.dp.toPx()
                val head = 3.2.dp.toPx()
                val radius = size.minDimension / 2f - head
                val middle = Offset(size.width / 2f, size.height / 2f)
                val arcTopLeft = Offset(middle.x - radius, middle.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                val arcStroke = Stroke(width = stroke, cap = StrokeCap.Round)
                val sweep = 140f
                val starts = floatArrayOf(-70f, 110f)
                val heads = starts.map { start ->
                    val end = Math.toRadians((start + sweep).toDouble())
                    val out = Offset(kotlin.math.cos(end).toFloat(), kotlin.math.sin(end).toFloat())
                    // Clockwise is the way the arrow travels.
                    val along = Offset(-out.y, out.x)
                    val at = middle + out * radius
                    Path().apply {
                        moveTo(at.x + along.x * head, at.y + along.y * head)
                        lineTo(at.x + out.x * head, at.y + out.y * head)
                        lineTo(at.x - out.x * head, at.y - out.y * head)
                        close()
                    }
                }
                onDrawBehind {
                    // Each arrow is a little under half the circle, with its head where it ends.
                    for (i in starts.indices) {
                        drawArc(
                            color = color,
                            startAngle = starts[i],
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = arcStroke,
                        )
                        drawPath(heads[i], color)
                    }
                }
            },
    )
}

@Composable
internal fun TrendArrow(up: Boolean, color: Color) {
    Spacer(
        Modifier
            .size(9.dp)
            .clearAndSetSemantics { }
            .drawWithCache {
                val path = Path().apply {
                    if (up) {
                        moveTo(size.width / 2f, size.height * 0.1f)
                        lineTo(size.width, size.height * 0.9f)
                        lineTo(0f, size.height * 0.9f)
                    } else {
                        moveTo(0f, size.height * 0.1f)
                        lineTo(size.width, size.height * 0.1f)
                        lineTo(size.width / 2f, size.height * 0.9f)
                    }
                    close()
                }
                onDrawBehind { drawPath(path, color) }
            },
    )
}

/** The month (or week) of closing prices as one thin line. Draws nothing without history. */
@Composable
private fun Sparkline(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                if (points.size < 2) {
                    return@drawWithCache onDrawBehind { }
                }
                val inset = 2.dp.toPx()
                val width = size.width - inset * 2
                val height = size.height - inset * 2
                val path = Path()
                points.forEachIndexed { index, value ->
                    val x = inset + width * index / (points.size - 1)
                    val y = inset + height * (1f - value)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    drawPath(path = path, color = color, style = stroke)
                }
            },
    )
}
