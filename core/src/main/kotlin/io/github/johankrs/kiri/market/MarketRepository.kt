package io.github.johankrs.kiri.market

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the price of anything on the list: each market from its own official source, or all of
 * it from a [CompleteSource] the build added, as the person chose.
 *
 * The sources sit behind this class, so one can be swapped without touching the widget.
 * Calls block: run them off the main thread.
 *
 * [extras] makes the complete sources a build adds (none in the public code), over the same
 * connection as the official ones.
 */
class MarketRepository(
    http: HttpFetcher = UrlConnectionFetcher(),
    extras: (HttpFetcher) -> List<CompleteSource> = { emptyList() },
) {
    private val brapi = BrapiSource(http)
    private val finnhub = FinnhubSource(http)
    private val alpaca = AlpacaSource(http)
    private val fx = FxSource(http)

    /** The complete sources this build has, in the order they are offered. */
    val completeSources: List<CompleteSource> = extras(http).filter { it.key != MarketSource.OFFICIAL.key }

    /** Company names of US stocks, looked up once and kept while the app is alive. */
    private val usNames = ConcurrentHashMap<String, String>()

    /**
     * The complete source the saved [choice] stands for, or null for the official services. No
     * choice yet means the first complete source; one this build does not have, the official ones.
     */
    fun completeSource(choice: MarketSource): CompleteSource? = when (choice) {
        MarketSource.OFFICIAL -> null
        MarketSource.DEFAULT -> completeSources.firstOrNull()
        else -> completeSources.firstOrNull { it.key == choice.key }
    }

    /** Where prices really come from for the saved [choice]. */
    fun resolve(choice: MarketSource): MarketSource =
        completeSource(choice)?.let { MarketSource(it.key) } ?: MarketSource.OFFICIAL

    fun fetch(
        instrument: Instrument,
        keys: ApiKeys,
        source: MarketSource = MarketSource.DEFAULT,
        today: LocalDate = LocalDate.now(),
    ): QuoteResult {
        completeSource(source)?.let { return it.quote(instrument) }
        return when (instrument.market) {
            Market.B3 -> brapi.quote(instrument, keys.brapi)
            Market.FX -> fx.quote(instrument, keys.awesome, today)
            Market.US -> us(instrument, keys)
        }
    }

    /**
     * The prices of [range] for the large chart of [instrument]. Among the official sources, US
     * stocks need the Alpaca key, and currencies the AwesomeAPI key for the 24 hours.
     */
    fun series(
        instrument: Instrument,
        range: ChartRange,
        keys: ApiKeys,
        source: MarketSource = MarketSource.DEFAULT,
        today: LocalDate = LocalDate.now(),
    ): SeriesResult {
        completeSource(source)?.let { return it.series(instrument, range) }
        return when (instrument.market) {
            Market.B3 -> brapi.series(instrument, range, keys.brapi)
            Market.FX -> fx.series(instrument, range, keys.awesome, today)
            Market.US ->
                if (keys.hasAlpaca) {
                    alpaca.series(instrument, range, keys.alpacaId, keys.alpacaSecret)
                } else {
                    SeriesResult.Failed(FailReason.MISSING_KEY)
                }
        }
    }

    /**
     * Tries the person's key for [service] by asking for one real price with it, so a wrong key
     * is caught as soon as it is pasted instead of when the widget fails.
     */
    fun check(service: KeyService, keys: ApiKeys): KeyCheck {
        val result = when (service) {
            // Not one of the stocks that answer without a token, or every token would pass.
            KeyService.BRAPI -> brapi.quote(Instrument(Market.B3, KeyService.BRAPI.sample), keys.brapi)
            KeyService.ALPACA -> alpaca.quote(Instrument(Market.US, KeyService.ALPACA.sample), keys.alpacaId, keys.alpacaSecret)
            KeyService.FINNHUB -> finnhub.quote(Instrument(Market.US, KeyService.FINNHUB.sample), keys.finnhub)
            KeyService.AWESOME -> fx.tryKey(Instrument(Market.FX, KeyService.AWESOME.sample), keys.awesome)
        }
        return KeyCheck.of(result)
    }

    /**
     * US stocks: Finnhub for the price of the regular session, and Alpaca, when the person
     * gave its keys, for the price before the open and after the close and for the chart. With
     * Alpaca's keys alone it gives the regular price as well, so one of the two is enough.
     */
    private fun us(instrument: Instrument, keys: ApiKeys): QuoteResult {
        val quote = if (keys.finnhub.isBlank() && keys.hasAlpaca) {
            alpaca.quote(instrument, keys.alpacaId, keys.alpacaSecret)
        } else {
            val regular = withUsName(finnhub.quote(instrument, keys.finnhub), keys.finnhub)
            if (regular !is QuoteResult.Ok || !keys.hasAlpaca) return regular
            val extended = alpaca.extended(instrument, keys.alpacaId, keys.alpacaSecret)
            QuoteResult.Ok(regular.quote.copy(extended = extended ?: regular.quote.extended))
        }
        if (quote !is QuoteResult.Ok) return quote
        val history = alpaca.history(instrument, keys.alpacaId, keys.alpacaSecret)
        return if (history.isEmpty()) quote else QuoteResult.Ok(quote.quote.copy(history = history))
    }

    private fun withUsName(result: QuoteResult, key: String): QuoteResult {
        if (result !is QuoteResult.Ok) return result
        val symbol = result.quote.instrument.symbol
        val name = usNames[symbol]
            ?: finnhub.companyName(symbol, key)?.also { usNames[symbol] = it }
        return QuoteResult.Ok(result.quote.copy(name = name))
    }
}

/** A service that takes the person's own key, with the stock or currency its key is tried on. */
enum class KeyService(val sample: String) {
    BRAPI("BBAS3"),
    ALPACA("AAPL"),
    FINNHUB("AAPL"),
    AWESOME("USD"),
}

/** What came of trying a key. */
sealed interface KeyCheck {
    /** The key works: [sample] is the price it brought. */
    data class Works(val sample: Quote) : KeyCheck

    /** The service does not take this key. */
    data object Refused : KeyCheck

    /** The key is right, but its plan has no requests left for now. */
    data object OutOfRequests : KeyCheck

    /** The service could not be reached, so the key could not be tried. */
    data object Offline : KeyCheck

    /** The service answered something else. */
    data object Unclear : KeyCheck

    companion object {
        fun of(result: QuoteResult): KeyCheck = when (result) {
            is QuoteResult.Ok -> Works(result.quote)
            is QuoteResult.Failed -> when (result.reason) {
                FailReason.MISSING_KEY -> Refused
                FailReason.RATE_LIMITED -> OutOfRequests
                FailReason.NETWORK -> Offline
                FailReason.NOT_FOUND, FailReason.BAD_RESPONSE -> Unclear
            }
        }
    }
}
