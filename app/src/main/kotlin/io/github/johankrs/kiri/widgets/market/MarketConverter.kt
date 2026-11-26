package io.github.johankrs.kiri.widgets.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.market.ConverterAmount
import io.github.johankrs.kiri.market.CurrencyRates
import io.github.johankrs.kiri.market.Instrument
import io.github.johankrs.kiri.market.MarketFormat
import io.github.johankrs.kiri.market.MarketSettings
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import java.text.NumberFormat
import java.util.Locale

private val Amount = PaneNumbers.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
private val Result = PaneNumbers.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)

/** The rates the converter works with: the prices the widget already has. */
@Composable
fun converterRates(controller: MarketController): CurrencyRates {
    val quotes by controller.quotes.collectAsState()
    return remember(quotes) { CurrencyRates.from(quotes) }
}

/**
 * The currency converter: a pane of glass that grows out of the widget when a currency is
 * tapped (see [MarketOverlay]). It converts between the real and the currencies the person
 * follows, with the prices the widget already has, so opening it asks the network for nothing.
 */
@Composable
fun ConverterPane(
    initialFrom: String,
    rates: CurrencyRates,
    settings: MarketSettings,
    updatedText: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]

    var from by rememberSaveable { mutableStateOf(initialFrom) }
    var amountText by rememberSaveable { mutableStateOf(ConverterAmount.format(1.0, locale)) }
    var step by rememberSaveable { mutableIntStateOf(ConverterAmount.DEFAULT_STEP) }
    // A currency that lost its price falls back to the real, which always has one.
    val source = if (from in rates.codes) from else Instrument.BASE_CURRENCY
    val amount = ConverterAmount.parse(amountText)
    val changeBy: (Int) -> Unit = { by ->
        amountText = ConverterAmount.format(ConverterAmount.stepped(amount, step, by), locale)
    }

    PaneFrame(
        settings = settings,
        title = stringResource(R.string.market_converter_title),
        subtitle = "",
        refreshing = refreshing,
        onRefresh = onRefresh,
        onClose = onClose,
        modifier = modifier,
    ) { glass, look ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                text = stringResource(R.string.market_converter_from),
                style = PaneLabel.copy(color = glass.contentMuted),
            )
            ChoiceLine(
                options = rates.codes.map { it to it },
                selected = source,
                look = look,
                onSelect = { from = it },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val amountLabel = stringResource(R.string.market_converter_amount, currencyTitle(source, locale))
            BasicText(text = amountLabel, style = PaneLabel.copy(color = glass.contentMuted))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepButton(Glyph.MINUS, stringResource(R.string.market_converter_decrease), amount > 0.0, true, look) {
                    changeBy(-1)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(look.field)
                        .padding(horizontal = 12.dp),
                ) {
                    BasicText(
                        text = MarketFormat.currencySymbol(source),
                        style = PaneStrong.copy(color = glass.contentMuted),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = ConverterAmount.clean(it, locale) },
                        singleLine = true,
                        textStyle = Amount.copy(color = glass.content, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(glass.content),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = amountLabel },
                    )
                }
                StepButton(Glyph.PLUS, stringResource(R.string.market_converter_increase), true, false, look) {
                    changeBy(1)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                text = stringResource(R.string.market_converter_step),
                style = PaneLabel.copy(color = glass.contentMuted),
            )
            val grouped = remember(locale) { NumberFormat.getIntegerInstance(locale) }
            ChoiceLine(
                options = ConverterAmount.STEPS.map { it to grouped.format(it) },
                selected = step,
                look = look,
                onSelect = { step = it },
            )
        }

        Column {
            for (target in rates.codes.filter { it != source }) {
                val converted = rates.convert(amount, source, target)
                val one = rates.convert(1.0, source, target)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(glass.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(text = currencyTitle(target, locale), style = PaneStrong.copy(color = glass.content))
                        if (one != null) {
                            BasicText(
                                text = stringResource(
                                    R.string.market_converter_rate,
                                    source,
                                    MarketFormat.price(one, target, locale),
                                ),
                                style = PaneLabel.merge(PaneNumbers).copy(color = glass.contentMuted),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = converted?.let { MarketFormat.price(it, target, locale) }
                            ?: stringResource(R.string.market_converter_no_rate),
                        style = Result.copy(color = glass.content, textAlign = TextAlign.End),
                    )
                }
            }
            if (rates.codes.size < 2) {
                BasicText(
                    text = stringResource(R.string.market_converter_empty),
                    style = PaneLabel.copy(color = glass.contentMuted),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            if (updatedText != null) {
                BasicText(
                    text = updatedText,
                    style = PaneLabel.copy(color = glass.contentMuted),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** "Dólar americano (USD)", or just the code when the name is unknown. */
private fun currencyTitle(code: String, locale: Locale): String {
    val name = currencyName(code, locale)
    return if (name.isEmpty() || name == code) code else "$name ($code)"
}

@Composable
private fun StepButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    atStart: Boolean,
    look: PaneLook,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // The line of the minus begins, and that of the plus ends, where the text does.
            .hang(over = (IconTarget - 22.dp) / 2 + 3.4.dp, atStart = atStart)
            .size(IconTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        GlyphIcon(glyph, if (enabled) look.content else look.muted.copy(alpha = 0.4f), Modifier.size(22.dp))
    }
}
