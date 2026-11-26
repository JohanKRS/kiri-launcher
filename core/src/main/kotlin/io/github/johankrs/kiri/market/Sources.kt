package io.github.johankrs.kiri.market

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

// The helpers below up to [BrapiSource] are public so that a source kept outside this module (see
// [CompleteSource]) reads answers the same way the built-in ones do.

/** Runs one lookup and turns anything that goes wrong into a [QuoteResult.Failed]. */
inline fun guarded(instrument: Instrument, lookup: () -> QuoteResult): QuoteResult = try {
    lookup()
} catch (e: IOException) {
    QuoteResult.Failed(instrument, FailReason.NETWORK)
} catch (e: JSONException) {
    QuoteResult.Failed(instrument, FailReason.BAD_RESPONSE)
} catch (e: RuntimeException) {
    QuoteResult.Failed(instrument, FailReason.BAD_RESPONSE)
}

internal fun failureFor(code: Int): FailReason = when (code) {
    401, 403 -> FailReason.MISSING_KEY
    404 -> FailReason.NOT_FOUND
    402, 429 -> FailReason.RATE_LIMITED
    else -> FailReason.BAD_RESPONSE
}

fun JSONObject.doubleOrNull(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return when (val value = get(name)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }
}

fun JSONObject.textOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotEmpty() } else null

private const val SECONDS_PER_DAY = 86_400L

/** The sources are asked for prices in steps of this many minutes for the widget's chart. */
const val WIDGET_STEP_MINUTES = 15
private const val WIDGET_HOURS = 6

/**
 * The chart every item shows in the widget: the last six hours of trading in [steps] (time in
 * seconds since 1970 to price, oldest first, one for each [WIDGET_STEP_MINUTES] with trades).
 * Hours in which the market was closed do not count, so the chart is as full right after the
 * open, at night or over a weekend as in the middle of a session.
 */
fun lastTradingHoursOf(steps: List<Pair<Long, Double>>): List<Double> =
    steps.takeLast(WIDGET_HOURS * 60 / WIDGET_STEP_MINUTES + 1).map { it.second }

/**
 * The 24 hours that end at the latest of [steps]. Ending at the latest trade instead of at
 * this moment keeps the chart whole over a night, a weekend or a holiday.
 */
fun lastDayOf(steps: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
    val end = steps.lastOrNull()?.first ?: return emptyList()
    return steps.filter { it.first > end - SECONDS_PER_DAY }
}

/** Runs one chart lookup and turns anything that goes wrong into a [SeriesResult.Failed]. */
inline fun guardedSeries(lookup: () -> SeriesResult): SeriesResult = try {
    lookup()
} catch (e: IOException) {
    SeriesResult.Failed(FailReason.NETWORK)
} catch (e: JSONException) {
    SeriesResult.Failed(FailReason.BAD_RESPONSE)
} catch (e: RuntimeException) {
    SeriesResult.Failed(FailReason.BAD_RESPONSE)
}

/**
 * brapi.dev: Brazilian stocks, funds, ETFs and BDRs. A few well-known tickers answer without a
 * key; everything else needs the person's own free token, sent as a header so it never appears
 * in a URL.
 */
class BrapiSource(private val http: HttpFetcher) {
    fun quote(instrument: Instrument, key: String): QuoteResult = guarded(instrument) {
        val headers = if (key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${key.trim()}")
        val url = "$BASE_URL/${instrument.symbol}"
        var response = http.get("$url?range=5d&interval=${WIDGET_STEP_MINUTES}m", headers)
        // A plan that does not include history still gives the price.
        if (response.code == 400) response = http.get(url, headers)
        if (!response.ok) return QuoteResult.Failed(instrument, failureFor(response.code))
        parse(instrument, response.body)
    }

    internal fun parse(instrument: Instrument, body: String): QuoteResult {
        val result = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
            ?: return QuoteResult.Failed(instrument, FailReason.NOT_FOUND)
        val price = result.doubleOrNull("regularMarketPrice")
            ?: return QuoteResult.Failed(instrument, FailReason.NOT_FOUND)

        val steps = steps(result)
        return QuoteResult.Ok(
            Quote(
                instrument = instrument,
                name = result.textOrNull("longName") ?: result.textOrNull("shortName"),
                price = price,
                changePercent = result.doubleOrNull("regularMarketChangePercent"),
                currency = result.textOrNull("currency") ?: "BRL",
                asOfEpochSeconds = result.textOrNull("regularMarketTime")
                    ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() },
                history = lastTradingHoursOf(steps),
                details = QuoteDetails(
                    dayHigh = result.doubleOrNull("regularMarketDayHigh"),
                    dayLow = result.doubleOrNull("regularMarketDayLow"),
                    previousClose = result.doubleOrNull("regularMarketPreviousClose"),
                    volume = result.doubleOrNull("regularMarketVolume")?.toLong(),
                    yearHigh = result.doubleOrNull("fiftyTwoWeekHigh"),
                    yearLow = result.doubleOrNull("fiftyTwoWeekLow"),
                ),
            ),
        )
    }

    /** The prices of [range] for the large chart. The free plan may not reach the longest ones. */
    fun series(instrument: Instrument, range: ChartRange, key: String): SeriesResult = guardedSeries {
        val headers = if (key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${key.trim()}")
        val query = when (range) {
            ChartRange.DAY -> "range=5d&interval=5m"
            ChartRange.WEEK -> "range=5d&interval=30m"
            ChartRange.MONTH -> "range=1mo&interval=1d"
            ChartRange.YEAR -> "range=1y&interval=1d"
            ChartRange.FIVE_YEARS -> "range=5y&interval=1wk"
        }
        val response = http.get("$BASE_URL/${instrument.symbol}?$query", headers)
        if (!response.ok) return SeriesResult.Failed(failureFor(response.code))
        val result = JSONObject(response.body).optJSONArray("results")?.optJSONObject(0)
            ?: return SeriesResult.Failed(FailReason.NOT_FOUND)
        val steps = steps(result)
        SeriesResult.of(if (range == ChartRange.DAY) lastDayOf(steps) else steps)
    }

    /** Each step of the history that has a price, with its time, oldest first. */
    private fun steps(result: JSONObject): List<Pair<Long, Double>> {
        val steps = ArrayList<Pair<Long, Double>>()
        result.optJSONArray("historicalDataPrice")?.let { history ->
            for (i in 0 until history.length()) {
                val step = history.optJSONObject(i) ?: continue
                val at = step.doubleOrNull("date")?.toLong() ?: continue
                step.doubleOrNull("close")?.let { steps += at to it }
            }
        }
        return steps
    }

    private companion object {
        const val BASE_URL = "https://brapi.dev/api/quote"
    }
}

/** Finnhub: stocks listed in the United States. Always needs the person's own free key. */
class FinnhubSource(private val http: HttpFetcher) {
    fun quote(instrument: Instrument, key: String): QuoteResult = guarded(instrument) {
        if (key.isBlank()) return QuoteResult.Failed(instrument, FailReason.MISSING_KEY)
        val response = http.get("$BASE_URL/quote?symbol=${instrument.symbol}", headers(key))
        if (!response.ok) return QuoteResult.Failed(instrument, failureFor(response.code))
        parse(instrument, response.body)
    }

    /** The company's name, or null when it cannot be had. Never fails the quote. */
    fun companyName(symbol: String, key: String): String? = try {
        val response = http.get("$BASE_URL/stock/profile2?symbol=$symbol", headers(key))
        if (response.ok) JSONObject(response.body).textOrNull("name") else null
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    internal fun parse(instrument: Instrument, body: String): QuoteResult {
        val json = JSONObject(body)
        val price = json.doubleOrNull("c")
        // Finnhub answers an unknown symbol with zeros instead of an error.
        if (price == null || price == 0.0) return QuoteResult.Failed(instrument, FailReason.NOT_FOUND)
        return QuoteResult.Ok(
            Quote(
                instrument = instrument,
                name = null,
                price = price,
                changePercent = json.doubleOrNull("dp"),
                currency = "USD",
                asOfEpochSeconds = json.doubleOrNull("t")?.toLong()?.takeIf { it > 0 },
                details = QuoteDetails(
                    dayHigh = json.doubleOrNull("h")?.takeIf { it > 0.0 },
                    dayLow = json.doubleOrNull("l")?.takeIf { it > 0.0 },
                    previousClose = json.doubleOrNull("pc")?.takeIf { it > 0.0 },
                ),
            ),
        )
    }

    private fun headers(key: String) = mapOf("X-Finnhub-Token" to key.trim())

    private companion object {
        const val BASE_URL = "https://finnhub.io/api/v1"
    }
}

/**
 * Currencies, priced in reais, from two sources. AwesomeAPI gives the price of the moment and,
 * with the person's own free key, the minutes before it, which make the chart of the last hours.
 * Frankfurter (reference rates, once a day, no key) gives the longer charts, the chart of the
 * last days when there is no key, and stands in when the first one is unavailable.
 */
class FxSource(private val http: HttpFetcher) {
    fun quote(instrument: Instrument, awesomeKey: String, today: LocalDate): QuoteResult {
        if (awesomeKey.isNotBlank()) {
            // With a key, one answer has the price of the moment and the hours before it.
            val minutes = minutes(instrument, awesomeKey, WIDGET_MINUTES)
            if (minutes is QuoteResult.Ok) return minutes
        }
        val live = live(instrument, awesomeKey)
        val days = rates(instrument.symbol, today.minusDays(HISTORY_DAYS), weekly = false).map { it.second }
        if (live is QuoteResult.Ok) return QuoteResult.Ok(live.quote.copy(history = days.takeLast(SPARK_DAYS)))
        if (days.isEmpty()) return live

        val last = days.last()
        val previous = days.getOrNull(days.size - 2)
        return QuoteResult.Ok(
            Quote(
                instrument = instrument,
                name = null,
                price = last,
                changePercent = previous?.takeIf { it != 0.0 }?.let { (last / it - 1.0) * 100.0 },
                currency = Instrument.BASE_CURRENCY,
                asOfEpochSeconds = null,
                history = days.takeLast(SPARK_DAYS),
            ),
        )
    }

    /**
     * The prices of [range] for the large chart. The 24 hours need the AwesomeAPI key; the
     * longer ranges are reference rates, one a day (one a week for five years).
     */
    fun series(instrument: Instrument, range: ChartRange, awesomeKey: String, today: LocalDate): SeriesResult = guardedSeries {
        if (range == ChartRange.DAY) {
            if (awesomeKey.isBlank()) return SeriesResult.Failed(FailReason.MISSING_KEY)
            val response = http.get("$AWESOME_URL/${pair(instrument)}/$MAX_MINUTES", awesomeHeaders(awesomeKey))
            if (!response.ok) return SeriesResult.Failed(failureFor(response.code))
            return SeriesResult.of(lastDayOf(parseMinutes(response.body)))
        }
        val from = when (range) {
            ChartRange.WEEK -> today.minusDays(7)
            ChartRange.MONTH -> today.minusMonths(1)
            ChartRange.YEAR -> today.minusYears(1)
            else -> today.minusYears(5)
        }
        val response = http.get(ratesUrl(instrument.symbol, from, weekly = range == ChartRange.FIVE_YEARS), emptyMap())
        if (!response.ok) return SeriesResult.Failed(failureFor(response.code))
        SeriesResult.of(parseRates(response.body))
    }

    private fun live(instrument: Instrument, key: String): QuoteResult = guarded(instrument) {
        val response = http.get("$AWESOME_URL/json/last/${pair(instrument)}", awesomeHeaders(key))
        if (!response.ok) return QuoteResult.Failed(instrument, failureFor(response.code))
        parseLive(instrument, response.body)
    }

    internal fun parseLive(instrument: Instrument, body: String): QuoteResult {
        val json = JSONObject(body)
        val entry = json.optJSONObject(instrument.symbol + Instrument.BASE_CURRENCY)
            ?: json.keys().asSequence().firstNotNullOfOrNull { json.optJSONObject(it) }
            ?: return QuoteResult.Failed(instrument, FailReason.NOT_FOUND)
        val price = entry.doubleOrNull("bid")
            ?: return QuoteResult.Failed(instrument, FailReason.BAD_RESPONSE)
        return QuoteResult.Ok(
            Quote(
                instrument = instrument,
                name = null,
                price = price,
                changePercent = entry.doubleOrNull("pctChange"),
                currency = Instrument.BASE_CURRENCY,
                asOfEpochSeconds = entry.doubleOrNull("timestamp")?.toLong()?.let(::seconds)?.takeIf { it > 0 },
            ),
        )
    }

    /** The price of the moment asked for with [key] alone, with no other source standing in. */
    fun tryKey(instrument: Instrument, key: String): QuoteResult =
        if (key.isBlank()) QuoteResult.Failed(instrument, FailReason.MISSING_KEY) else minutes(instrument, key, 1)

    /** The last [count] prices a minute apart: the newest is the quote, all of them its chart. */
    private fun minutes(instrument: Instrument, key: String, count: Int): QuoteResult = guarded(instrument) {
        val response = http.get("$AWESOME_URL/${pair(instrument)}/$count", awesomeHeaders(key))
        if (!response.ok) return QuoteResult.Failed(instrument, failureFor(response.code))
        val newest = newestOf(response.body) ?: return QuoteResult.Failed(instrument, FailReason.NOT_FOUND)
        val price = newest.doubleOrNull("bid") ?: return QuoteResult.Failed(instrument, FailReason.BAD_RESPONSE)
        QuoteResult.Ok(
            Quote(
                instrument = instrument,
                name = null,
                price = price,
                changePercent = newest.doubleOrNull("pctChange"),
                currency = Instrument.BASE_CURRENCY,
                asOfEpochSeconds = newest.doubleOrNull("timestamp")?.toLong()?.let(::seconds),
                history = lastTradingHoursOf(inSteps(parseMinutes(response.body))),
            ),
        )
    }

    private fun newestOf(body: String): JSONObject? {
        val list = JSONArray(body)
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            .maxByOrNull { it.doubleOrNull("timestamp")?.toLong()?.let(::seconds) ?: 0L }
    }

    /** Each minute's price with its time in seconds, oldest first. */
    internal fun parseMinutes(body: String): List<Pair<Long, Double>> {
        val list = JSONArray(body)
        val steps = ArrayList<Pair<Long, Double>>(list.length())
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            val at = entry.doubleOrNull("timestamp")?.toLong()?.let(::seconds) ?: continue
            val price = entry.doubleOrNull("bid")?.takeIf { it > 0.0 } ?: continue
            steps += at to price
        }
        return steps.sortedBy { it.first }
    }

    /** Reference rates from [from] until today, time to rate, oldest first. Empty when they cannot be had. */
    private fun rates(symbol: String, from: LocalDate, weekly: Boolean): List<Pair<Long, Double>> = try {
        val response = http.get(ratesUrl(symbol, from, weekly), emptyMap())
        if (response.ok) parseRates(response.body) else emptyList()
    } catch (e: IOException) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }

    private fun ratesUrl(symbol: String, from: LocalDate, weekly: Boolean): String =
        "$FRANKFURTER_URL?from=$from&base=$symbol&quotes=${Instrument.BASE_CURRENCY}" + if (weekly) "&group=week" else ""

    /** One rate a day (or a week), at the start of its day, oldest first. */
    internal fun parseRates(body: String): List<Pair<Long, Double>> {
        val list = JSONArray(body)
        val steps = ArrayList<Pair<Long, Double>>(list.length())
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            if (entry.textOrNull("quote")?.equals(Instrument.BASE_CURRENCY, ignoreCase = true) == false) continue
            val day = entry.textOrNull("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val rate = entry.doubleOrNull("rate")?.takeIf { it > 0.0 } ?: continue
            steps += day.toEpochDay() * SECONDS_PER_DAY to rate
        }
        return steps.sortedBy { it.first }
    }

    private fun pair(instrument: Instrument) = "${instrument.symbol}-${Instrument.BASE_CURRENCY}"

    private fun awesomeHeaders(key: String) = if (key.isBlank()) emptyMap() else mapOf("x-api-key" to key.trim())

    private companion object {
        const val AWESOME_URL = "https://economia.awesomeapi.com.br"
        const val FRANKFURTER_URL = "https://api.frankfurter.dev/v2/rates"
        const val HISTORY_DAYS = 21L

        /** The widget shows the last ten days when it has no hours to show. */
        const val SPARK_DAYS = 10

        /** Enough minutes for six hours of trading, with room for the gaps between them. */
        const val WIDGET_MINUTES = 480

        /** The most AwesomeAPI gives with a key: a little more than a day. */
        const val MAX_MINUTES = 1500

        /** AwesomeAPI writes some times in milliseconds. */
        fun seconds(time: Long): Long = if (time > 100_000_000_000L) time / 1000 else time

        /** Prices a minute apart, in the widget's steps: the last price of each step. */
        fun inSteps(minutes: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
            val step = WIDGET_STEP_MINUTES * 60L
            return minutes.groupBy { it.first / step }.toSortedMap().map { (index, prices) -> index * step to prices.last().second }
        }
    }
}
