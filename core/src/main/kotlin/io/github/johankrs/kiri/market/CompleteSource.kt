package io.github.johankrs.kiri.market

import java.util.Locale

/**
 * A source that gives every market by itself, without the person's keys: stocks of both
 * exchanges, currencies and their charts.
 *
 * None is part of this code. They come from other apps installed beside Kiri (see
 * [SourceApps]), and are then offered beside the official services. Without any, the app knows
 * only the official services.
 */
interface CompleteSource {
    /** The name it is saved under in the settings; never the one of [MarketSource.OFFICIAL]. */
    val key: String

    /** Its name among the choices of where prices come from, in the language of [locale]. */
    fun name(locale: Locale): String

    /** A sentence or two on what it covers and what to expect of it, in the language of [locale]. */
    fun note(locale: Locale): String

    /** Blocking, like everything in [MarketRepository]: run it off the main thread. */
    fun quote(instrument: Instrument): QuoteResult

    fun series(instrument: Instrument, range: ChartRange): SeriesResult
}
