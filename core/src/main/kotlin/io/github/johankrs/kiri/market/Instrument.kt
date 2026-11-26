package io.github.johankrs.kiri.market

/** Where a followed item is quoted. Each market has its own data source. */
enum class Market(val prefix: String) {
    /** Brazilian exchange: stocks, funds, ETFs and BDRs. */
    B3("B3"),

    /** Stocks listed in the United States. */
    US("US"),

    /** A currency, priced in [Instrument.BASE_CURRENCY]. */
    FX("FX"),
}

/** One thing a person follows: a stock or a currency. */
data class Instrument(val market: Market, val symbol: String) {
    /** Stable text form, used for storage and as a map key. */
    val id: String = "${market.prefix}:$symbol"

    companion object {
        /** Currencies are quoted in reais. */
        const val BASE_CURRENCY = "BRL"

        private val B3_SYMBOL = Regex("[A-Z0-9]{4}[0-9]{1,2}F?")
        private val US_SYMBOL = Regex("[A-Z]{1,5}([.-][A-Z]{1,2})?")
        private val FX_SYMBOL = Regex("[A-Z]{3}")

        /**
         * Builds an instrument from what a person typed, or null when it cannot be a code of
         * that market. Spaces and letter case are forgiven.
         */
        fun fromInput(market: Market, typed: String): Instrument? {
            val symbol = typed.trim().uppercase()
            val valid = when (market) {
                Market.B3 -> B3_SYMBOL.matches(symbol)
                Market.US -> US_SYMBOL.matches(symbol)
                Market.FX -> FX_SYMBOL.matches(symbol) && symbol != BASE_CURRENCY
            }
            return if (valid) Instrument(market, symbol) else null
        }

        /** Reads back an [id]. Returns null for anything that is not one. */
        fun parse(id: String): Instrument? {
            val parts = id.split(':', limit = 2)
            if (parts.size != 2) return null
            val market = Market.entries.firstOrNull { it.prefix == parts[0] } ?: return null
            return fromInput(market, parts[1])
        }
    }
}

/** An item of the list, shown or hidden without losing its place. */
data class WatchEntry(val instrument: Instrument, val enabled: Boolean = true)

/** The ordered list of what a person follows. Stocks and currencies share one list. */
data class Watchlist(val entries: List<WatchEntry>) {
    val visible: List<Instrument> = entries.filter { it.enabled }.map { it.instrument }

    /** Adds [instrument] at the end. Adding something already listed just shows it again. */
    fun add(instrument: Instrument): Watchlist {
        val index = entries.indexOfFirst { it.instrument == instrument }
        return if (index >= 0) setEnabled(index, true) else Watchlist(entries + WatchEntry(instrument))
    }

    fun removeAt(index: Int): Watchlist =
        if (index in entries.indices) Watchlist(entries.filterIndexed { i, _ -> i != index }) else this

    fun setEnabled(index: Int, enabled: Boolean): Watchlist =
        if (index in entries.indices) {
            Watchlist(entries.mapIndexed { i, entry -> if (i == index) entry.copy(enabled = enabled) else entry })
        } else {
            this
        }

    /** Moves the entry at [index] up (negative [by]) or down. Out-of-range moves do nothing. */
    fun move(index: Int, by: Int): Watchlist {
        val target = index + by
        if (index !in entries.indices || target !in entries.indices) return this
        val moved = entries.toMutableList()
        moved.add(target, moved.removeAt(index))
        return Watchlist(moved)
    }

    /** Text form for storage: `FX:USD=1,B3:PETR4=1,US:AAPL=0`. */
    fun encode(): String = entries.joinToString(",") { "${it.instrument.id}=${if (it.enabled) 1 else 0}" }

    companion object {
        /**
         * The first list: the dollar and three stocks that the Brazilian source serves without
         * a key, so the widget shows real numbers before any setup.
         */
        val DEFAULT = Watchlist(
            listOf(
                WatchEntry(Instrument(Market.FX, "USD")),
                WatchEntry(Instrument(Market.B3, "PETR4")),
                WatchEntry(Instrument(Market.B3, "VALE3")),
                WatchEntry(Instrument(Market.B3, "ITUB4")),
            ),
        )

        /** Reads [encode]'s output. Broken or repeated items are dropped; null means "never saved". */
        fun decode(stored: String?): Watchlist {
            if (stored == null) return DEFAULT
            val seen = HashSet<Instrument>()
            val entries = stored.split(',').mapNotNull { item ->
                val parts = item.split('=', limit = 2)
                val instrument = Instrument.parse(parts[0]) ?: return@mapNotNull null
                if (!seen.add(instrument)) return@mapNotNull null
                WatchEntry(instrument, enabled = parts.getOrNull(1) != "0")
            }
            return Watchlist(entries)
        }
    }
}
