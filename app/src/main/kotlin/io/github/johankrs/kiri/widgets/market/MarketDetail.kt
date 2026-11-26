package io.github.johankrs.kiri.widgets.market

import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.market.ChartRange
import io.github.johankrs.kiri.market.FailReason
import io.github.johankrs.kiri.market.Instrument
import io.github.johankrs.kiri.market.MarketFormat
import io.github.johankrs.kiri.market.MarketSettings
import io.github.johankrs.kiri.market.PriceSeries
import io.github.johankrs.kiri.market.Quote
import io.github.johankrs.kiri.market.QuoteResult
import io.github.johankrs.kiri.market.SeriesResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

/** The large price stands alone, so its digits keep their own widths instead of lining up in columns. */
private val Price =
    TextStyle(fontFamily = MarketFont, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)

/** Stands in for the words under the chart while there is none, only to keep their room. */
private val NoChartYet = DetailChart(emptyList(), "0", Trend.FLAT, "0", "0", "0", "0")

/** The large chart of a stock, already turned into heights and text. */
@Immutable
data class DetailChart(
    /** Heights of the line, 0 to 1, oldest first. */
    val heights: List<Float>,
    /** How much the price moved over the whole chart. */
    val changeText: String,
    val trend: Trend,
    val lowText: String,
    val highText: String,
    /** When the chart starts and ends, written for its range. */
    val startText: String,
    val endText: String,
)

/** What the place of the chart shows. */
@Immutable
sealed interface DetailChartState {
    data object Loading : DetailChartState

    data class Ready(val chart: DetailChart) : DetailChartState

    /** There is no chart, and [note] says why. */
    data class Failed(val note: String) : DetailChartState
}

/**
 * The details of a stock: a pane of glass that grows out of the widget when the stock is
 * tapped (see [MarketOverlay]). It shows the price large, the price outside regular hours,
 * a chart of the last 24 hours that can look further back, and what else the source says
 * about the day and the year.
 */
@Composable
fun MarketDetailPane(
    instrument: Instrument,
    controller: MarketController,
    prefs: MarketPrefs,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val single = remember(instrument) { listOf(instrument) }
    val row = marketRows(single, controller).first()
    val quotes by controller.quotes.collectAsState()
    val quote = (quotes[instrument] as? QuoteResult.Ok)?.quote
    val refreshing by controller.refreshing.collectAsState()
    val updatedAt by controller.updatedAt.collectAsState()

    var range by rememberSaveable { mutableStateOf(ChartRange.DEFAULT) }
    // The chart that was last fetched, with the range it is of.
    var fetched by remember { mutableStateOf<Pair<ChartRange, SeriesResult>?>(null) }
    val source = prefs.settings.source
    val keys = prefs.keys
    // Asked again when the prices are refreshed, so the chart ends where the price is.
    LaunchedEffect(instrument, range, source, keys, updatedAt) {
        fetched = range to controller.series(instrument, range, source, keys)
    }

    val noteNetwork = stringResource(R.string.market_note_network)
    val noteRateLimited = stringResource(R.string.market_note_rate_limited)
    val noteMissingKey = stringResource(R.string.market_note_missing_key)
    val notes = remember(noteNetwork, noteRateLimited, noteMissingKey) {
        mapOf(
            FailReason.NETWORK to noteNetwork,
            FailReason.RATE_LIMITED to noteRateLimited,
            FailReason.MISSING_KEY to noteMissingKey,
        )
    }
    val noChart = stringResource(R.string.market_detail_no_chart)
    val result = fetched?.takeIf { it.first == range }?.second
    val chart = remember(result, range, locale, notes, noChart) {
        when (result) {
            null -> DetailChartState.Loading
            is SeriesResult.Failed -> DetailChartState.Failed(notes[result.reason] ?: noChart)
            is SeriesResult.Ok -> DetailChartState.Ready(toDetailChart(result.series, range, locale, context))
        }
    }

    DetailPane(
        row = row,
        facts = detailFacts(quote, locale),
        range = range,
        chart = chart,
        settings = prefs.settings,
        updatedText = updatedText(controller),
        refreshing = refreshing,
        onRange = { range = it },
        onRefresh = { scope.launch { controller.refreshNow(prefs) } },
        onClose = onClose,
        modifier = modifier,
    )
}

/** The details without their data, so they can be drawn anywhere. */
@Composable
fun DetailPane(
    row: MarketRow,
    /** What else is known, as label and value, in reading order. */
    facts: List<Pair<String, String>>,
    range: ChartRange,
    chart: DetailChartState,
    settings: MarketSettings,
    updatedText: String?,
    refreshing: Boolean,
    onRange: (ChartRange) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PaneFrame(
        settings = settings,
        title = row.symbol,
        subtitle = row.name,
        refreshing = refreshing,
        onRefresh = onRefresh,
        onClose = onClose,
        modifier = modifier,
    ) { glass, look ->
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    text = row.priceText,
                    style = Price.copy(color = glass.content),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.padding(bottom = 7.dp)) {
                    ChangeLine(row.changeText, row.trend, settings.glass, glass)
                }
            }
            val extended = row.extended
            if (extended != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    BasicText(text = extended.longLabel, style = PaneLabel.copy(color = glass.contentMuted))
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = extended.priceNumber,
                        style = PaneLabel.merge(PaneNumbers).copy(color = glass.content, fontWeight = FontWeight.Medium),
                    )
                    Spacer(Modifier.width(8.dp))
                    ChangeLine(extended.changeText, extended.trend, settings.glass, glass)
                }
            }
        }

        ChoiceLine(
            options = listOf(
                ChartRange.DAY to stringResource(R.string.market_range_day),
                ChartRange.WEEK to stringResource(R.string.market_range_week),
                ChartRange.MONTH to stringResource(R.string.market_range_month),
                ChartRange.YEAR to stringResource(R.string.market_range_year),
                ChartRange.FIVE_YEARS to stringResource(R.string.market_range_five_years),
            ),
            selected = range,
            look = look,
            onSelect = onRange,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                when (chart) {
                    is DetailChartState.Ready -> {
                        val trend = chart.chart.trend
                        // The line takes the colour of the way the price went over the chart.
                        val moved = trend == Trend.UP || trend == Trend.DOWN
                        val color = if (moved) settings.glass.trendColor(trend, glass) else glass.content
                        LargeChart(chart.chart.heights, color, glass.divider)
                    }

                    DetailChartState.Loading -> BasicText(
                        text = stringResource(R.string.market_detail_loading),
                        style = PaneLabel.copy(color = glass.contentMuted, textAlign = TextAlign.Center),
                    )

                    is DetailChartState.Failed -> BasicText(
                        text = chart.note,
                        style = PaneLabel.copy(color = glass.contentMuted, textAlign = TextAlign.Center),
                    )
                }
            }
            // What is written under the chart keeps its room while there is no chart, so the
            // pane is the same height before and after the chart arrives.
            val ready = (chart as? DetailChartState.Ready)?.chart
            val written = ready ?: NoChartYet
            val shown = animateFloatAsState(if (ready != null) 1f else 0f, tween(durationMillis = 250), label = "chart")
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .graphicsLayer { alpha = shown.value }
                    .then(if (ready == null) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                Row {
                    BasicText(
                        text = written.startText,
                        style = PaneLabel.merge(PaneNumbers).copy(color = glass.contentMuted),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = written.endText,
                        style = PaneLabel.merge(PaneNumbers).copy(color = glass.contentMuted, textAlign = TextAlign.End),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = stringResource(R.string.market_detail_period),
                        style = PaneLabel.copy(color = glass.contentMuted),
                    )
                    Spacer(Modifier.width(8.dp))
                    ChangeLine(written.changeText, written.trend, settings.glass, glass)
                }
                // On a line of its own: beside the change it would wrap on a narrow phone.
                BasicText(
                    text = stringResource(R.string.market_detail_low_high, written.lowText, written.highText),
                    style = PaneLabel.merge(PaneNumbers).copy(color = glass.contentMuted),
                )
            }
        }

        if (facts.isNotEmpty()) {
            Column {
                // Two to a line, each with its label over its value.
                for (line in facts.chunked(2)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(glass.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        for ((label, value) in line) {
                            Column(Modifier.weight(1f)) {
                                BasicText(text = label, style = PaneLabel.copy(color = glass.contentMuted))
                                BasicText(text = value, style = PaneStrong.copy(color = glass.content))
                            }
                        }
                        if (line.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (updatedText != null) {
            BasicText(text = updatedText, style = PaneLabel.copy(color = glass.contentMuted))
        }
    }
}

/** The chart as a line over a soft shade, drawn from left to right when it arrives. */
@Composable
private fun LargeChart(heights: List<Float>, color: Color, lineColor: Color) {
    val shown = remember(heights) { Animatable(0f) }
    LaunchedEffect(heights) {
        shown.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    Spacer(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .drawWithCache {
                if (heights.size < 2) {
                    return@drawWithCache onDrawBehind { }
                }
                val stroke = 2.dp.toPx()
                val ruleStroke = 1.dp.toPx()
                val top = stroke
                val height = size.height - stroke * 2
                val line = Path()
                heights.forEachIndexed { index, value ->
                    val x = size.width * index / (heights.size - 1)
                    val y = top + height * (1f - value)
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                val shade = Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                val gradient = Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)))
                val lineStyle = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    // The lowest and the highest price of the chart, as two faint lines.
                    drawLine(lineColor, Offset(0f, top), Offset(size.width, top), strokeWidth = ruleStroke)
                    drawLine(lineColor, Offset(0f, top + height), Offset(size.width, top + height), strokeWidth = ruleStroke)
                    clipRect(right = size.width * shown.value) {
                        drawPath(shade, gradient)
                        drawPath(line, color, style = lineStyle)
                    }
                }
            },
    )
}

/** What the source says about the day and the year, in the person's words and numbers. */
@Composable
private fun detailFacts(quote: Quote?, locale: Locale): List<Pair<String, String>> {
    val details = quote?.details ?: return emptyList()
    val dayHighLabel = stringResource(R.string.market_detail_day_high)
    val dayLowLabel = stringResource(R.string.market_detail_day_low)
    val prevCloseLabel = stringResource(R.string.market_detail_previous_close)
    val volumeLabel = stringResource(R.string.market_detail_volume)
    val yearHighLabel = stringResource(R.string.market_detail_year_high)
    val yearLowLabel = stringResource(R.string.market_detail_year_low)
    val compact = remember(locale) {
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
            .apply { maximumFractionDigits = 1 }
    }
    return remember(
        details,
        quote.currency,
        locale,
        compact,
        dayHighLabel,
        dayLowLabel,
        prevCloseLabel,
        volumeLabel,
        yearHighLabel,
        yearLowLabel,
    ) {
        fun money(value: Double?) = value?.let { MarketFormat.price(it, quote.currency, locale) }
        val volume = details.volume?.let { compact.format(it) }
        listOfNotNull(
            money(details.dayHigh)?.let { dayHighLabel to it },
            money(details.dayLow)?.let { dayLowLabel to it },
            money(details.previousClose)?.let { prevCloseLabel to it },
            volume?.let { volumeLabel to it },
            money(details.yearHigh)?.let { yearHighLabel to it },
            money(details.yearLow)?.let { yearLowLabel to it },
        )
    }
}

private fun toDetailChart(
    series: PriceSeries,
    range: ChartRange,
    locale: Locale,
    context: Context,
): DetailChart {
    val change = series.changePercent
    return DetailChart(
        heights = series.heights,
        changeText = change?.let { MarketFormat.percent(it, locale) }.orEmpty(),
        trend = when {
            change == null -> Trend.UNKNOWN
            abs(change) < 0.005 -> Trend.FLAT
            change > 0 -> Trend.UP
            else -> Trend.DOWN
        },
        lowText = MarketFormat.amount(series.low, locale),
        highText = MarketFormat.amount(series.high, locale),
        startText = chartMoment(series.points.first().atEpochSeconds, range, locale, context),
        endText = chartMoment(series.points.last().atEpochSeconds, range, locale, context),
    )
}

/** A moment of the chart, as much of it as its range needs: the hour, the day or the month. */
private fun chartMoment(epochSeconds: Long, range: ChartRange, locale: Locale, context: Context): String {
    val date = Date(epochSeconds * 1000L)
    fun written(skeleton: String) =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
    return when (range) {
        ChartRange.DAY -> "${written("EEE")} ${DateFormat.getTimeFormat(context).format(date)}"
        ChartRange.WEEK, ChartRange.MONTH -> written("dMMM")
        ChartRange.YEAR, ChartRange.FIVE_YEARS -> written("MMMyyyy")
    }
}
