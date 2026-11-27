package io.github.johankrs.kiri.market

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SourceAppsTest {
    private val petr4 = Instrument(Market.B3, "PETR4")
    private val aapl = Instrument(Market.US, "AAPL")

    @Test
    fun aQuoteGoesAcrossWhole() {
        val quote = Quote(
            instrument = aapl,
            name = "Apple Inc.",
            price = 231.45,
            changePercent = -0.82,
            currency = "USD",
            asOfEpochSeconds = 1_764_100_800,
            history = listOf(230.1, 230.9, 231.45),
            extended = ExtendedQuote(ExtendedSession.POST, 232.0, 0.24, 1_764_104_400),
            details = QuoteDetails(dayHigh = 233.0, dayLow = 229.5, previousClose = 233.36, volume = 41_200_000, yearHigh = 260.1, yearLow = 164.08),
        )
        val back = SourceApps.decodeQuote(aapl, SourceApps.encodeQuote(QuoteResult.Ok(quote)))
        assertEquals(QuoteResult.Ok(quote), back)
    }

    @Test
    fun whatIsUnknownStaysUnknown() {
        val quote = Quote(petr4, name = null, price = 31.2, changePercent = null, currency = "BRL", asOfEpochSeconds = null)
        val back = assertIs<QuoteResult.Ok>(SourceApps.decodeQuote(petr4, SourceApps.encodeQuote(QuoteResult.Ok(quote)))).quote
        assertEquals(quote, back)
        assertNull(back.extended)
        assertEquals(QuoteDetails(), back.details)
    }

    @Test
    fun numbersJsonCannotHoldAreLeftOut() {
        val quote = Quote(petr4, null, 31.2, Double.NaN, "BRL", null, history = listOf(1.0, Double.POSITIVE_INFINITY, 2.0))
        val back = assertIs<QuoteResult.Ok>(SourceApps.decodeQuote(petr4, SourceApps.encodeQuote(QuoteResult.Ok(quote)))).quote
        assertNull(back.changePercent)
        assertEquals(listOf(1.0, 2.0), back.history)

        val noPrice = quote.copy(price = Double.NaN)
        assertEquals(QuoteResult.Failed(petr4, FailReason.BAD_RESPONSE), SourceApps.decodeQuote(petr4, SourceApps.encodeQuote(QuoteResult.Ok(noPrice))))
    }

    @Test
    fun theAnswerIsAlwaysAboutWhatWasAsked() {
        val other = Quote(aapl, null, 1.0, null, "USD", null)
        val back = assertIs<QuoteResult.Ok>(SourceApps.decodeQuote(petr4, SourceApps.encodeQuote(QuoteResult.Ok(other)))).quote
        assertEquals(petr4, back.instrument)
    }

    @Test
    fun failuresKeepTheirReason() {
        for (reason in FailReason.entries) {
            assertEquals(QuoteResult.Failed(petr4, reason), SourceApps.decodeQuote(petr4, SourceApps.encodeQuote(QuoteResult.Failed(petr4, reason))))
            assertEquals(SeriesResult.Failed(reason), SourceApps.decodeSeries(SourceApps.encodeSeries(SeriesResult.Failed(reason))))
        }
    }

    @Test
    fun brokenAnswersAreBadResponses() {
        val bad = QuoteResult.Failed(petr4, FailReason.BAD_RESPONSE)
        for (text in listOf(null, "", "<html>", "[]", """{"ok":true}""", """{"ok":true,"price":3}""", """{"ok":false,"reason":"SOMETHING_NEW"}""")) {
            assertEquals(bad, SourceApps.decodeQuote(petr4, text), "for $text")
        }
        assertEquals(SeriesResult.Failed(FailReason.BAD_RESPONSE), SourceApps.decodeSeries("""{"ok":true}"""))
        assertEquals(SeriesResult.Failed(FailReason.BAD_RESPONSE), SourceApps.decodeSeries(null))
    }

    @Test
    fun aChartGoesAcrossWhole() {
        val series = SeriesResult.of(listOf(1_764_000_000L to 30.0, 1_764_000_900L to 30.5, 1_764_001_800L to 30.25))
        assertEquals(series, SourceApps.decodeSeries(SourceApps.encodeSeries(series)))
    }

    @Test
    fun aChartOfOnePriceIsNotFound() {
        assertEquals(SeriesResult.Failed(FailReason.NOT_FOUND), SourceApps.decodeSeries("""{"ok":true,"points":[[1764000000,30.0]]}"""))
    }

    @Test
    fun aSourceAppIsUsedOnlyOncePicked() {
        val app = object : CompleteSource {
            override val key = "elsewhere"
            override fun name(locale: Locale) = "Elsewhere"
            override fun note(locale: Locale) = ""
            override fun quote(instrument: Instrument) = QuoteResult.Ok(Quote(instrument, null, 1.0, null, "BRL", null))
            override fun series(instrument: Instrument, range: ChartRange) = SeriesResult.Failed(FailReason.NOT_FOUND)
        }
        var installed = listOf<CompleteSource>(app)
        val repository = MarketRepository({ _, _ -> throw java.io.IOException("offline") }) { installed }

        assertNull(repository.completeSource(MarketSource.DEFAULT), "no choice yet is the official services")
        assertEquals(MarketSource.OFFICIAL, repository.resolve(MarketSource.DEFAULT))
        assertEquals(app, repository.completeSource(MarketSource("elsewhere")))
        assertIs<QuoteResult.Ok>(repository.fetch(petr4, ApiKeys(), MarketSource("elsewhere")))

        installed = emptyList()
        assertNull(repository.completeSource(MarketSource("elsewhere")), "an app that is gone falls back to the official services")
        assertEquals(MarketSource.OFFICIAL, repository.resolve(MarketSource("elsewhere")))
    }
}
