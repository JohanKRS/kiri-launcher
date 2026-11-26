package io.github.johankrs.kiri.market

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Converts between the real and the currencies a person follows. Each currency is known by
 * its price in reais, which is enough to go from any one of them to any other.
 */
class CurrencyRates(pricesInBase: Map<String, Double>) {
    private val inBase: Map<String, Double> =
        mapOf(Instrument.BASE_CURRENCY to 1.0) + pricesInBase.filterValues { it > 0.0 && it.isFinite() }

    /** The currencies that can be converted, the real first, then in the order given. */
    val codes: List<String> = inBase.keys.toList()

    /** [amount] of [from] in [to], or null when one of them has no price yet. */
    fun convert(amount: Double, from: String, to: String): Double? {
        val source = inBase[from] ?: return null
        val target = inBase[to] ?: return null
        return amount * source / target
    }

    companion object {
        /** Takes the currencies out of what the widget already fetched. */
        fun from(quotes: Map<Instrument, QuoteResult>): CurrencyRates = CurrencyRates(
            quotes.entries
                .filter { it.key.market == Market.FX }
                .mapNotNull { (instrument, result) ->
                    (result as? QuoteResult.Ok)?.let { instrument.symbol to it.quote.price }
                }
                .toMap(),
        )
    }
}

/** The amount being converted: typed freely, or changed by steps with minus and plus. */
object ConverterAmount {
    /** How much one tap on minus or plus can change the amount. */
    val STEPS = listOf(1, 10, 100, 1000)
    const val DEFAULT_STEP = 1
    const val MAX = 999_999_999.0
    private const val MAX_TYPED = 12

    /** Keeps what makes sense of typed text: digits and one decimal mark, up to two decimals. */
    fun clean(typed: String, locale: Locale): String {
        val mark = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val out = StringBuilder()
        var decimals = -1
        for (char in typed) {
            when {
                char.isDigit() && decimals < 2 -> {
                    out.append(char)
                    if (decimals >= 0) decimals++
                }
                (char == ',' || char == '.') && decimals < 0 -> {
                    if (out.isEmpty()) out.append('0')
                    out.append(mark)
                    decimals = 0
                }
            }
            if (out.length >= MAX_TYPED) break
        }
        return out.toString()
    }

    /** The number in [text], zero when there is none. */
    fun parse(text: String): Double =
        text.replace(',', '.').trimEnd('.').toDoubleOrNull()?.coerceIn(0.0, MAX) ?: 0.0

    /** `100` or `37,5` as the text of the field: no grouping, decimals only when needed. */
    fun format(amount: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }.format(amount.coerceIn(0.0, MAX))

    /** One tap on plus ([by] = 1) or minus (-1). Never under zero. */
    fun stepped(amount: Double, step: Int, by: Int): Double = (amount + step * by).coerceIn(0.0, MAX)
}
