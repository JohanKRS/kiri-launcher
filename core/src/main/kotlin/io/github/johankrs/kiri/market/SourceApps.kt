package io.github.johankrs.kiri.market

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * How the market widget takes prices from another app: the agreement between Kiri and an app
 * installed beside it that serves as a [CompleteSource]. A source kept this way is built, signed
 * and updated apart from Kiri, and Kiri itself holds none of its code (see
 * `docs/fontes-de-cotacoes.md`).
 *
 * Such an app has a content provider with an intent filter for [ACTION] and the meta-data
 * [META_KEY], the name the person's choice of it is saved under. The provider's label is the
 * name the source is offered by, and the optional meta-data [META_NOTE] points to a string
 * resource with a sentence or two on what it covers. Kiri asks with the provider's `call`, method
 * [METHOD_QUOTE] or [METHOD_SERIES], with what is wanted in [EXTRA_INSTRUMENT] and [EXTRA_RANGE];
 * the answer holds in [EXTRA_RESULT] the text that [encodeQuote] or [encodeSeries] make.
 *
 * Nothing here touches Android, so Kiri and the source apps share it and it is tested on the JVM.
 */
object SourceApps {
    const val ACTION = "io.github.johankrs.kiri.action.MARKET_SOURCE"
    const val META_KEY = "io.github.johankrs.kiri.source.KEY"
    const val META_NOTE = "io.github.johankrs.kiri.source.NOTE"

    const val METHOD_QUOTE = "quote"
    const val METHOD_SERIES = "series"

    /** The [Instrument.id] of what is asked for. */
    const val EXTRA_INSTRUMENT = "instrument"

    /** The [ChartRange.key] of the chart asked for, with [METHOD_SERIES]. */
    const val EXTRA_RANGE = "range"

    /** The version of this agreement the side that asks speaks: [VERSION]. */
    const val EXTRA_VERSION = "version"

    /** The answer: text made by [encodeQuote] or [encodeSeries]. */
    const val EXTRA_RESULT = "result"

    const val VERSION = 1

    fun encodeQuote(result: QuoteResult): String = when (result) {
        is QuoteResult.Failed -> failed(result.reason)
        is QuoteResult.Ok -> result.quote.takeIf { it.price.isFinite() }?.let { quote ->
            JSONObject()
                .put("ok", true)
                .put("price", quote.price)
                .putFinite("changePercent", quote.changePercent)
                .put("currency", quote.currency)
                .putOpt("name", quote.name)
                .putOpt("asOf", quote.asOfEpochSeconds)
                .put("history", JSONArray().apply { quote.history.filter { it.isFinite() }.forEach { put(it) } })
                .putOpt(
                    "extended",
                    quote.extended?.takeIf { it.price.isFinite() }?.let { extended ->
                        JSONObject()
                            .put("session", extended.session.name)
                            .put("price", extended.price)
                            .putFinite("changePercent", extended.changePercent)
                            .putOpt("asOf", extended.asOfEpochSeconds)
                    },
                )
                .put(
                    "details",
                    quote.details.let { details ->
                        JSONObject()
                            .putFinite("dayHigh", details.dayHigh)
                            .putFinite("dayLow", details.dayLow)
                            .putFinite("previousClose", details.previousClose)
                            .putOpt("volume", details.volume)
                            .putFinite("yearHigh", details.yearHigh)
                            .putFinite("yearLow", details.yearLow)
                    },
                )
                .toString()
        } ?: failed(FailReason.BAD_RESPONSE)
    }

    /**
     * Reads what [encodeQuote] made, as the price of [instrument]: the one Kiri asked for,
     * whatever the answer says. Anything that cannot be read is [FailReason.BAD_RESPONSE].
     */
    fun decodeQuote(instrument: Instrument, text: String?): QuoteResult = read(
        text,
        failed = { QuoteResult.Failed(instrument, it) },
    ) { json ->
        val price = json.doubleOrNull("price") ?: return@read QuoteResult.Failed(instrument, FailReason.BAD_RESPONSE)
        val currency = json.textOrNull("currency") ?: return@read QuoteResult.Failed(instrument, FailReason.BAD_RESPONSE)
        val history = json.optJSONArray("history")?.let { array ->
            (0 until array.length()).mapNotNull { array.optDouble(it).takeIf(Double::isFinite) }
        }.orEmpty()
        val extended = json.optJSONObject("extended")?.let { item ->
            val session = ExtendedSession.entries.firstOrNull { it.name == item.optString("session") }
            val at = item.doubleOrNull("price")
            if (session == null || at == null) null else ExtendedQuote(session, at, item.doubleOrNull("changePercent"), item.longOrNull("asOf"))
        }
        val details = json.optJSONObject("details")?.let { item ->
            QuoteDetails(
                dayHigh = item.doubleOrNull("dayHigh"),
                dayLow = item.doubleOrNull("dayLow"),
                previousClose = item.doubleOrNull("previousClose"),
                volume = item.longOrNull("volume"),
                yearHigh = item.doubleOrNull("yearHigh"),
                yearLow = item.doubleOrNull("yearLow"),
            )
        } ?: QuoteDetails()
        QuoteResult.Ok(
            Quote(
                instrument = instrument,
                name = json.textOrNull("name"),
                price = price,
                changePercent = json.doubleOrNull("changePercent"),
                currency = currency,
                asOfEpochSeconds = json.longOrNull("asOf"),
                history = history,
                extended = extended,
                details = details,
            ),
        )
    }

    fun encodeSeries(result: SeriesResult): String = when (result) {
        is SeriesResult.Failed -> failed(result.reason)
        is SeriesResult.Ok -> JSONObject()
            .put("ok", true)
            .put(
                "points",
                JSONArray().apply {
                    result.series.points.filter { it.price.isFinite() }.forEach { put(JSONArray().put(it.atEpochSeconds).put(it.price)) }
                },
            )
            .toString()
    }

    /** Reads what [encodeSeries] made. Anything that cannot be read is [FailReason.BAD_RESPONSE]. */
    fun decodeSeries(text: String?): SeriesResult = read(text, failed = { SeriesResult.Failed(it) }) { json ->
        val points = json.optJSONArray("points") ?: return@read SeriesResult.Failed(FailReason.BAD_RESPONSE)
        val steps = (0 until points.length()).mapNotNull { index ->
            val point = points.optJSONArray(index) ?: return@mapNotNull null
            val price = point.optDouble(1).takeIf(Double::isFinite) ?: return@mapNotNull null
            point.optLong(0, -1).takeIf { it >= 0 }?.let { it to price }
        }
        SeriesResult.of(steps)
    }

    private fun failed(reason: FailReason): String = JSONObject().put("ok", false).put("reason", reason.name).toString()

    /** Reads an answer: [ok] for one that says it worked, [failed] with its reason otherwise. */
    private inline fun <T> read(text: String?, failed: (FailReason) -> T, ok: (JSONObject) -> T): T {
        if (text.isNullOrBlank()) return failed(FailReason.BAD_RESPONSE)
        return try {
            val json = JSONObject(text)
            if (json.optBoolean("ok")) {
                ok(json)
            } else {
                failed(FailReason.entries.firstOrNull { it.name == json.optString("reason") } ?: FailReason.BAD_RESPONSE)
            }
        } catch (e: JSONException) {
            failed(FailReason.BAD_RESPONSE)
        } catch (e: RuntimeException) {
            failed(FailReason.BAD_RESPONSE)
        }
    }

    /** JSON has no room for NaN or infinity: such a number is left out, as unknown. */
    private fun JSONObject.putFinite(name: String, value: Double?): JSONObject =
        if (value != null && value.isFinite()) put(name, value) else this

    private fun JSONObject.longOrNull(name: String): Long? = doubleOrNull(name)?.toLong()
}
