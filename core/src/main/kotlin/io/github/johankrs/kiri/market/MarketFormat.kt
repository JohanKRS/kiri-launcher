package io.github.johankrs.kiri.market

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** How prices and changes are written. Kept here so every size of the widget agrees. */
object MarketFormat {
    private const val NO_BREAK_SPACE = ' '
    private const val MINUS = '−'

    fun currencySymbol(code: String): String = when (code) {
        "BRL" -> "R$"
        "USD" -> "US$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> code
    }

    /** `R$ 44,36`: the currency sign, a space that never breaks and [amount]. */
    fun price(value: Double, currency: String, locale: Locale): String =
        currencySymbol(currency) + NO_BREAK_SPACE + amount(value, locale)

    /**
     * `44,36`: the number of a price on its own, for where the currency sign is written apart
     * or not at all. Prices under one unit get four decimals, so a weak currency is still
     * readable.
     */
    fun amount(value: Double, locale: Locale): String = number(value, if (abs(value) < 1.0) 4 else 2, locale)

    /** `+0,77%` or `−0,13%`, always with two decimals. */
    fun percent(value: Double, locale: Locale): String {
        val text = number(abs(value), 2, locale) + "%"
        return when {
            text.all { it == '0' || !it.isDigit() } -> text
            value < 0 -> MINUS + text
            else -> "+$text"
        }
    }

    private fun number(value: Double, decimals: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)
}

/**
 * Turns closing prices into the heights of a small chart: 0 is the lowest price and 1 the
 * highest. Fewer than two prices give no chart.
 */
fun sparkline(history: List<Double>): List<Float> {
    if (history.size < 2) return emptyList()
    val low = history.min()
    val span = history.max() - low
    return history.map { if (span == 0.0) 0.5f else ((it - low) / span).toFloat() }
}
