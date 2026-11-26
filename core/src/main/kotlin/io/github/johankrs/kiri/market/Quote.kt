package io.github.johankrs.kiri.market

/** The latest known price of an [Instrument]. */
data class Quote(
    val instrument: Instrument,
    /** Company name as the source gives it, when it gives one. */
    val name: String?,
    val price: Double,
    /** Change against the previous close, in percent. */
    val changePercent: Double?,
    /** ISO code of the currency [price] is in. */
    val currency: String,
    /** When the source last updated this price, in seconds since 1970. */
    val asOfEpochSeconds: Long?,
    /** Prices of the last six hours of trading, oldest first, for the small chart. May be empty. */
    val history: List<Double> = emptyList(),
    /** The price outside regular trading hours, when the source has one. */
    val extended: ExtendedQuote? = null,
    /** More about the day and the year, for when a stock is opened. */
    val details: QuoteDetails = QuoteDetails(),
)

/** What else a source says about a stock. Anything it does not say stays null. */
data class QuoteDetails(
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val previousClose: Double? = null,
    /** Shares traded in the day. */
    val volume: Long? = null,
    /** Highest and lowest price of the last 52 weeks. */
    val yearHigh: Double? = null,
    val yearLow: Double? = null,
)

/** The part of the day outside the regular session in which a stock still trades. */
enum class ExtendedSession {
    /** Before the opening bell. */
    PRE,

    /** After the closing bell. */
    POST,
}

/** The latest price outside regular trading hours. */
data class ExtendedQuote(
    val session: ExtendedSession,
    val price: Double,
    /** Change against the regular close it follows, in percent. */
    val changePercent: Double?,
    val asOfEpochSeconds: Long?,
)

enum class FailReason {
    /** The source needs the person's own key and none (or a wrong one) was given. */
    MISSING_KEY,
    NOT_FOUND,
    RATE_LIMITED,
    NETWORK,
    BAD_RESPONSE,
}

sealed interface QuoteResult {
    val instrument: Instrument

    data class Ok(val quote: Quote) : QuoteResult {
        override val instrument: Instrument get() = quote.instrument
    }

    data class Failed(override val instrument: Instrument, val reason: FailReason) : QuoteResult
}

/** The person's own keys for the data sources. A blank key counts as none. */
data class ApiKeys(
    val brapi: String = "",
    val finnhub: String = "",
    val awesome: String = "",
    /** Alpaca gives two halves: the key's id and its secret. Both are needed. */
    val alpacaId: String = "",
    val alpacaSecret: String = "",
) {
    val hasAlpaca: Boolean get() = alpacaId.isNotBlank() && alpacaSecret.isNotBlank()
}
