package io.github.johankrs.kiri.ui.tv

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.tv.TvBox
import io.github.johankrs.kiri.core.tv.TvWidget
import io.github.johankrs.kiri.core.tv.TvWidgetKind
import io.github.johankrs.kiri.widgets.market.MarketRow
import io.github.johankrs.kiri.widgets.market.Trend
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * The widgets over the TV's home screen, each in its [boxes] place (units). [focused] and
 * [moving] are the keys of the widget with the focus and of the one being moved, if any.
 * [marketRows] gives the lines of stocks and currencies, kept up to date while they are shown.
 */
@Composable
internal fun TvWidgetsBand(
    widgets: List<TvWidget>,
    boxes: List<TvBox>,
    focused: String?,
    moving: String?,
    marketRows: @Composable () -> List<MarketRow>?,
) {
    widgets.forEachIndexed { index, widget ->
        val box = boxes.getOrNull(index) ?: return@forEachIndexed
        androidx.compose.runtime.key(widget.kind) {
            WidgetSlot(widget, box, focused == widget.key, moving == widget.key, marketRows)
        }
    }
}

@Composable
private fun WidgetSlot(
    widget: TvWidget,
    box: TvBox,
    focused: Boolean,
    moving: Boolean,
    marketRows: @Composable () -> List<MarketRow>?,
) {
    val units = LocalTvUnits.current
    val glide = spring<Float>(dampingRatio = 0.82f, stiffness = 240f)
    val x by animateFloatAsState(box.left, glide, label = "widgetX")
    val width by animateFloatAsState(box.width, spring(dampingRatio = 0.9f, stiffness = 260f), label = "widgetWidth")
    val lift by animateFloatAsState(
        if (moving) 1.06f else if (focused) 1.04f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "widgetLift",
    )
    val ring by animateFloatAsState(if (focused || moving) 1f else 0f, tween(220), label = "widgetRing")
    val ringColor = if (moving) Color(0xFF7FE0D2) else Color.White.copy(alpha = 0.92f)
    val corner = 22f
    val shape = RoundedCornerShape(units.dp(corner))
    Box(
        Modifier
            .offset(units.dp(x), units.dp(box.top))
            .size(units.dp(width), units.dp(box.height))
            .zIndex(if (focused || moving) 3f else 1f)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                translationY = if (moving) -units.px(4f) else 0f
            }
            .drawBehind {
                val r = units.px(corner)
                // A soft shadow, deeper with the focus.
                for (step in 1..4) {
                    val grow = units.px(3f + 4f * ring) * step / 4f
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.05f + 0.03f * ring),
                        topLeft = Offset(-grow, -grow + units.px(4f + 6f * ring)),
                        size = Size(size.width + 2f * grow, size.height + 2f * grow),
                        cornerRadius = CornerRadius(r + grow),
                    )
                }
            }
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .background(WidgetGlass)
            .drawBehind {
                val r = units.px(corner)
                val line = units.px(1f)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.42f),
                        0.25f to Color.White.copy(alpha = 0.22f),
                        1f to Color.White.copy(alpha = 0.16f),
                    ),
                    topLeft = Offset(line / 2f, line / 2f),
                    size = Size(size.width - line, size.height - line),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(line),
                )
            }
            .padding(horizontal = units.dp(20f), vertical = units.dp(16f)),
    ) {
        when (widget.kind) {
            TvWidgetKind.CLOCK -> ClockFace(width)
            TvWidgetKind.MARKET -> MarketFace(marketRows())
        }
    }
    // The ring goes round the outside, over the glass's own edge.
    if (ring > 0f) {
        Box(
            Modifier
                .offset(units.dp(x), units.dp(box.top))
                .size(units.dp(width), units.dp(box.height))
                .zIndex(if (focused || moving) 3.1f else 1.1f)
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    translationY = if (moving) -units.px(4f) else 0f
                }
                .drawBehind {
                    val line = units.px(2.5f)
                    drawRoundRect(
                        color = ringColor.copy(alpha = ringColor.alpha * ring),
                        topLeft = Offset(-line / 2f, -line / 2f),
                        size = Size(size.width + line, size.height + line),
                        cornerRadius = CornerRadius(units.px(corner) + line),
                        style = Stroke(line),
                    )
                },
        )
    }
    if (moving) {
        BasicText(
            text = "◀  ▶",
            style = TextStyle(
                fontFamily = TvFont,
                fontSize = units.sp(12f),
                letterSpacing = units.sp(3f),
                color = Color(0xFFB8F2E8),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .offset(units.dp(x), units.dp(box.bottom + 10f))
                .width(units.dp(width))
                .zIndex(3.2f),
        )
    }
}

/** Glass over the wallpaper, dark enough for white text on any picture. */
private val WidgetGlass = Brush.verticalGradient(listOf(Color(0x660E2027), Color(0x7A0E2027)))

/** The time, large, and the day under it. The wider the widget, the larger the time. */
@Composable
private fun ClockFace(width: Float) {
    val units = LocalTvUnits.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = Date()
        }
    }
    val time = remember(now, context) { DateFormat.getTimeFormat(context).format(now) }
    val day = remember(now, dayFormat) { dayFormat.format(now) }
    val big = (50f + (width - 210f) / 120f * 12f).coerceIn(50f, 62f)
    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        BasicText(
            text = time,
            maxLines = 1,
            style = TextStyle(
                fontFamily = TvFont,
                fontWeight = FontWeight(300),
                fontSize = units.sp(big),
                lineHeight = units.sp(big),
                letterSpacing = (-0.02f).em,
                color = Color.White,
                fontFeatureSettings = "tnum",
            ),
        )
        Spacer(Modifier.height(units.dp(8f)))
        BasicText(
            text = day,
            maxLines = 2,
            style = TextStyle(
                fontFamily = TvFont,
                fontSize = units.sp(14f),
                lineHeight = units.sp(18f),
                color = Color.White.copy(alpha = 0.78f),
                // Two even lines rather than a long one and a word left alone.
                lineBreak = LineBreak.Heading,
            ),
        )
    }
}

/**
 * Stocks and currencies: the name of the widget, then as many lines as fit, each with what it
 * is, its price and how it changed today.
 */
@Composable
private fun MarketFace(rows: List<MarketRow>?) {
    val units = LocalTvUnits.current
    val title = TextStyle(
        fontFamily = TvFont,
        fontWeight = FontWeight(500),
        fontSize = units.sp(12f),
        letterSpacing = units.sp(1f),
        color = Color.White.copy(alpha = 0.6f),
    )
    val line = TextStyle(fontFamily = TvFont, fontSize = units.sp(15f), lineHeight = units.sp(19f), color = Color.White, fontFeatureSettings = "tnum")
    val shown = rows.orEmpty().take(MARKET_LINES)
    val labels = shortLabels(shown)
    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        BasicText(stringResource(R.string.market_title).uppercase(), maxLines = 1, style = title)
        Spacer(Modifier.height(units.dp(8f)))
        if (rows == null) return@Column
        shown.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(units.dp(5f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    labels[index],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = line.copy(color = Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier.weight(1f),
                )
                BasicText(row.priceNumber, maxLines = 1, style = line)
                Box(Modifier.width(units.dp(62f)), contentAlignment = Alignment.CenterEnd) {
                    BasicText(
                        row.changeText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = line.copy(
                            fontSize = units.sp(13f),
                            color = when (row.trend) {
                                Trend.UP -> Color(0xFF8FE3B0)
                                Trend.DOWN -> Color(0xFFFFB3A6)
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** How many lines of stocks and currencies fit in a widget as high as the TV's are. */
private const val MARKET_LINES = 3

/**
 * What each line is called where room is short: a currency by the first word of its name
 * ("Dólar"), a stock by its symbol. When two currencies would read the same, both keep their code.
 */
private fun shortLabels(rows: List<MarketRow>): List<String> {
    val short = rows.map { row ->
        if (row.currency != null) row.name.substringBefore(' ').ifBlank { row.symbol } else row.symbol
    }
    return short.mapIndexed { i, label ->
        if (rows[i].currency != null && short.count { it == label } > 1) rows[i].symbol else label
    }
}

/** The name of a widget, in the person's language. */
@Composable
internal fun widgetName(kind: TvWidgetKind): String = stringResource(
    when (kind) {
        TvWidgetKind.CLOCK -> R.string.tv_widget_clock
        TvWidgetKind.MARKET -> R.string.market_title
    },
)

/** The name of a widget's size: small, medium or large. */
@Composable
internal fun widgetSizeName(level: Int): String = stringResource(
    when (level) {
        0 -> R.string.tv_widget_size_small
        1 -> R.string.tv_widget_size_medium
        else -> R.string.tv_widget_size_large
    },
)

/** A small drawing of a widget, for the head of its menu. */
@Composable
internal fun WidgetBadge(kind: TvWidgetKind, width: androidx.compose.ui.unit.Dp) {
    val units = LocalTvUnits.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width, width * (9f / 16f))
            .graphicsLayer {
                shape = RoundedCornerShape(units.dp(7f))
                clip = true
            }
            .background(Brush.linearGradient(listOf(Color(0xFF2B5566), Color(0xFF1C3540)))),
    ) {
        TvLineIcon(
            when (kind) {
                TvWidgetKind.CLOCK -> TvIcons.CLOCK
                TvWidgetKind.MARKET -> TvIcons.CHART
            },
            Color.White,
            Modifier.size(units.dp(20f)),
        )
    }
}
