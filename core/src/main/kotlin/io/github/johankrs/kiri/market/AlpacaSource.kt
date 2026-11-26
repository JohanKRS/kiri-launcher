package io.github.johankrs.kiri.market

import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What Alpaca knows about a stock right now: its latest trade, at any hour, and the closes of
 * the last two regular sessions.
 */
internal class UsSnapshot(
    val trade: Double,
    val tradeAt: Instant,
    /** The day of the latest regular session Alpaca has a bar for. */
    val sessionDay: LocalDate,
    val sessionClose: Double,
    val previousClose: Double?,
) {
    private val tradeLocal = tradeAt.atZone(NEW_YORK)

    /** The extended session the latest trade belongs to, or null when it was a regular one. */
    val session: ExtendedSession? = when {
        tradeLocal.toLocalTime() < OPEN -> ExtendedSession.PRE
        tradeLocal.toLocalTime() >= CLOSE -> ExtendedSession.POST
        else -> null
    }

    /**
     * The regular close the latest trade is compared with: the one before it. Early in the
     * morning the day's bar may already exist, and then it is the session before that counts.
     */
    private val referenceClose: Double? =
        if (session == ExtendedSession.PRE && !sessionDay.isBefore(tradeLocal.toLocalDate())) {
            previousClose
        } else {
            sessionClose
        }

    fun extended(): ExtendedQuote? {
        val session = session ?: return null
        return ExtendedQuote(
            session = session,
            price = trade,
            changePercent = percent(trade, referenceClose),
            asOfEpochSeconds = tradeAt.epochSecond,
        )
    }

    /** The whole quote, for when Alpaca is the only source of US prices. */
    fun quote(instrument: Instrument): Quote {
        val regular = if (session == null) trade else referenceClose ?: sessionClose
        // Outside regular hours the regular price is a close: its change is against the close before.
        val against = if (session == null || regular == sessionClose) previousClose else null
        return Quote(
            instrument = instrument,
            name = null,
            price = regular,
            changePercent = percent(regular, against),
            currency = "USD",
            asOfEpochSeconds = tradeAt.epochSecond,
            extended = extended(),
        )
    }

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val OPEN: LocalTime = LocalTime.of(9, 30)
        val CLOSE: LocalTime = LocalTime.of(16, 0)

        fun percent(value: Double, against: Double?): Double? =
            if (against == null || against == 0.0) null else (value - against) / against * 100.0
    }
}

/**
 * Alpaca: stocks listed in the United States, including the hours before the open and after
 * the close, which the other sources leave out, and the history behind every chart. Needs the
 * person's own free key (an id and a secret), sent as headers. The free plan sees every exchange
 * with a delay of 15 minutes, or one exchange (IEX) without delay; the first is asked for, the
 * second stands in.
 */
class AlpacaSource(private val http: HttpFetcher, private val now: () -> Instant = Instant::now) {
    /** Set once Alpaca refuses the delayed full feed, so it is not asked for again and again. */
    @Volatile
    private var fullFeedRefused = false

    /** The same for the history: there the full feed simply ends 15 minutes ago. */
    @Volatile
    private var fullHistoryRefused = false

    fun quote(instrument: Instrument, keyId: String, secret: String): QuoteResult = guarded(instrument) {
        val response = snapshot(instrument.symbol, keyId, secret)
        if (!response.ok) return QuoteResult.Failed(instrument, failureFor(response.code))
        val snapshot = parse(response.body) ?: return QuoteResult.Failed(instrument, FailReason.NOT_FOUND)
        QuoteResult.Ok(snapshot.quote(instrument))
    }

    /** The price outside regular hours, or null when there is none. Never fails the quote. */
    fun extended(instrument: Instrument, keyId: String, secret: String): ExtendedQuote? = try {
        val response = snapshot(instrument.symbol, keyId, secret)
        if (response.ok) parse(response.body)?.extended() else null
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    /**
     * The last six hours of trading, before the open and after the close included, for the
     * widget's chart. Empty when they cannot be had: the chart is never worth failing a price.
     */
    fun history(instrument: Instrument, keyId: String, secret: String): List<Double> = try {
        val response = bars(instrument.symbol, "${WIDGET_STEP_MINUTES}Min", Duration.ofDays(5), keyId, secret)
        if (response.ok) lastTradingHoursOf(parseBars(response.body)) else emptyList()
    } catch (e: IOException) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** The prices of [range] for the large chart, from the same history. */
    fun series(instrument: Instrument, range: ChartRange, keyId: String, secret: String): SeriesResult = guardedSeries {
        val (step, back) = when (range) {
            // Five days, so the 24 hours are there after a weekend too; every trade counts.
            ChartRange.DAY -> "5Min" to Duration.ofDays(5)
            ChartRange.WEEK -> "30Min" to Duration.ofDays(7)
            ChartRange.MONTH -> "1Hour" to Duration.ofDays(31)
            ChartRange.YEAR -> "1Day" to Duration.ofDays(366)
            ChartRange.FIVE_YEARS -> "1Week" to Duration.ofDays(5 * 366L)
        }
        val response = bars(instrument.symbol, step, back, keyId, secret)
        if (!response.ok) return SeriesResult.Failed(failureFor(response.code))
        val steps = parseBars(response.body)
        SeriesResult.of(if (range == ChartRange.DAY) lastDayOf(steps) else steps)
    }

    private fun headers(keyId: String, secret: String) =
        mapOf("APCA-API-KEY-ID" to keyId.trim(), "APCA-API-SECRET-KEY" to secret.trim())

    private fun snapshot(symbol: String, keyId: String, secret: String): HttpResponse {
        val headers = headers(keyId, secret)
        val url = "$BASE_URL/$symbol/snapshot?feed="
        if (!fullFeedRefused) {
            val response = http.get(url + FULL_DELAYED, headers)
            // 403: the keys are fine, but the plan does not include this feed.
            if (response.code != 403) return response
            fullFeedRefused = true
        }
        return http.get(url + SINGLE_EXCHANGE, headers)
    }

    /** Bars of [step] from [back] ago until now (or until 15 minutes ago, on the free plan). */
    private fun bars(symbol: String, step: String, back: Duration, keyId: String, secret: String): HttpResponse {
        val headers = headers(keyId, secret)
        val start = now().minus(back).truncatedTo(ChronoUnit.MINUTES)
        val url = "$BASE_URL/$symbol/bars?timeframe=$step&start=$start&limit=$MAX_BARS&adjustment=split&sort=asc&feed="
        if (!fullHistoryRefused) {
            val response = http.get(url + FULL, headers)
            if (response.code != 403) return response
            fullHistoryRefused = true
        }
        return http.get(url + SINGLE_EXCHANGE, headers)
    }

    /** Each bar's time and closing price, oldest first. */
    internal fun parseBars(body: String): List<Pair<Long, Double>> {
        val bars = JSONObject(body).optJSONArray("bars") ?: return emptyList()
        val steps = ArrayList<Pair<Long, Double>>(bars.length())
        for (i in 0 until bars.length()) {
            val bar = bars.optJSONObject(i) ?: continue
            val at = bar.textOrNull("t")?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() } ?: continue
            val close = bar.doubleOrNull("c")?.takeIf { it > 0.0 } ?: continue
            steps += at to close
        }
        return steps.sortedBy { it.first }
    }

    internal fun parse(body: String): UsSnapshot? {
        val json = JSONObject(body)
        val trade = json.optJSONObject("latestTrade") ?: return null
        val day = json.optJSONObject("dailyBar") ?: return null
        val price = trade.doubleOrNull("p")?.takeIf { it > 0.0 } ?: return null
        val close = day.doubleOrNull("c")?.takeIf { it > 0.0 } ?: return null
        val tradeAt = Instant.parse(trade.textOrNull("t") ?: return null)
        val dayStart = Instant.parse(day.textOrNull("t") ?: return null)
        return UsSnapshot(
            trade = price,
            tradeAt = tradeAt,
            sessionDay = dayStart.atZone(ZoneId.of("America/New_York")).toLocalDate(),
            sessionClose = close,
            previousClose = json.optJSONObject("prevDailyBar")?.doubleOrNull("c")?.takeIf { it > 0.0 },
        )
    }

    private companion object {
        const val BASE_URL = "https://data.alpaca.markets/v2/stocks"
        const val FULL_DELAYED = "delayed_sip"
        const val FULL = "sip"
        const val SINGLE_EXCHANGE = "iex"

        /** Five days of five-minute bars around the clock are under two thousand. */
        const val MAX_BARS = 10_000
    }
}
