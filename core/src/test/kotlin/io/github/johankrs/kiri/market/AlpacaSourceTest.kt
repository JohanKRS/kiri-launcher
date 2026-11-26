package io.github.johankrs.kiri.market

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlpacaSourceTest {
    private val aapl = Instrument(Market.US, "AAPL")

    /** Answers by URL fragment, and remembers what was asked. */
    private class FakeHttp(private val answers: Map<String, HttpResponse>) : HttpFetcher {
        val asked = mutableListOf<String>()
        var headers: Map<String, String> = emptyMap()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            asked += url
            this.headers = headers
            return answers.entries.firstOrNull { url.contains(it.key) }?.value ?: HttpResponse(404, "")
        }
    }

    /** 2026-11-17 is a Tuesday; New York is at UTC-5. The session is 14:30 to 21:00 UTC. */
    private fun snapshot(tradeAt: String, trade: Double, day: String = "2026-11-17", close: Double = 350.0) = """
        {"symbol":"AAPL",
         "latestTrade":{"t":"$tradeAt","x":"Q","p":$trade,"s":100,"c":["@","T"],"i":1,"z":"C"},
         "latestQuote":{"t":"$tradeAt","ap":$trade,"bp":$trade},
         "minuteBar":{"t":"$tradeAt","o":1,"h":1,"l":1,"c":1,"v":1},
         "dailyBar":{"t":"${day}T05:00:00Z","o":348.0,"h":353.0,"l":347.5,"c":$close,"v":1000},
         "prevDailyBar":{"t":"2026-11-16T05:00:00Z","o":340.0,"h":346.0,"l":339.0,"c":345.0,"v":900}}
    """.trimIndent()

    @Test
    fun `a trade after the close is the post-market price, against that day's close`() {
        val parsed = AlpacaSource(FakeHttp(emptyMap())).parse(snapshot("2026-11-17T22:15:00.435997104Z", 353.5))
        val extended = assertNotNull(parsed?.extended())
        assertEquals(ExtendedSession.POST, extended.session)
        assertEquals(353.5, extended.price)
        assertEquals(1.0, extended.changePercent!!, 1e-9)
    }

    @Test
    fun `a trade before the open is the pre-market price, against the last close`() {
        // Wednesday 08:00 in New York; the latest regular session is still Tuesday's.
        val parsed = AlpacaSource(FakeHttp(emptyMap())).parse(snapshot("2026-11-18T13:00:00Z", 346.5))
        val extended = assertNotNull(parsed?.extended())
        assertEquals(ExtendedSession.PRE, extended.session)
        assertEquals(-1.0, extended.changePercent!!, 1e-9)
    }

    @Test
    fun `when the day's bar already exists before the open, the close before it counts`() {
        val parsed = AlpacaSource(FakeHttp(emptyMap())).parse(snapshot("2026-11-17T13:00:00Z", 348.45))
        val extended = assertNotNull(parsed?.extended())
        assertEquals(ExtendedSession.PRE, extended.session)
        assertEquals(1.0, extended.changePercent!!, 1e-9)
    }

    @Test
    fun `a trade during the session has no extended price`() {
        val parsed = AlpacaSource(FakeHttp(emptyMap())).parse(snapshot("2026-11-17T15:00:00Z", 351.0))
        assertNull(parsed?.extended())
        val quote = parsed!!.quote(aapl)
        assertEquals(351.0, quote.price)
        assertEquals((351.0 - 345.0) / 345.0 * 100, quote.changePercent!!, 1e-9)
        assertNull(quote.extended)
    }

    @Test
    fun `alone, Alpaca gives the regular close with the extended price beside it`() {
        val http = FakeHttp(mapOf("AAPL/snapshot" to HttpResponse(200, snapshot("2026-11-17T22:15:00Z", 353.5))))
        val result = MarketRepository(http).fetch(aapl, ApiKeys(alpacaId = " id ", alpacaSecret = "secret"), MarketSource.OFFICIAL)
        val quote = assertIs<QuoteResult.Ok>(result).quote
        assertEquals(350.0, quote.price)
        assertEquals((350.0 - 345.0) / 345.0 * 100, quote.changePercent!!, 1e-9)
        assertEquals(ExtendedSession.POST, quote.extended?.session)
        assertEquals(mapOf("APCA-API-KEY-ID" to "id", "APCA-API-SECRET-KEY" to "secret"), http.headers)
        assertEquals(true, http.asked.first().endsWith("feed=delayed_sip"))
        // No history came, so there is no chart, but the price is all there.
        assertEquals(emptyList<Double>(), quote.history)
    }

    /** Bars as Alpaca writes them, a quarter of an hour apart, closing at [closes]. */
    private fun bars(vararg closes: Double, from: String = "2026-11-17T20:00:00Z") = """
        {"bars":[${closes.mapIndexed { i, c ->
            """{"t":"${java.time.Instant.parse(from).plusSeconds(900L * i)}","o":1,"h":1,"l":1,"c":$c,"v":10,"n":2,"vw":1}"""
        }.joinToString(",")}],"symbol":"AAPL","next_page_token":null}
    """.trimIndent()

    private val now = java.time.Instant.parse("2026-11-18T01:00:00Z")

    @Test
    fun `with Alpaca's keys a US stock has its chart in the widget and in its pane`() {
        val http = FakeHttp(
            mapOf(
                "AAPL/snapshot" to HttpResponse(200, snapshot("2026-11-17T22:15:00Z", 353.5)),
                "AAPL/bars" to HttpResponse(200, bars(351.0, 352.0, 353.5)),
            ),
        )
        val repository = MarketRepository(http)
        val keys = ApiKeys(alpacaId = "id", alpacaSecret = "secret")
        val quote = assertIs<QuoteResult.Ok>(repository.fetch(aapl, keys, MarketSource.OFFICIAL)).quote
        assertEquals(listOf(351.0, 352.0, 353.5), quote.history)
        assertEquals(true, http.asked.last().contains("timeframe=15Min&"))

        val day = assertIs<SeriesResult.Ok>(repository.series(aapl, ChartRange.DAY, keys, MarketSource.OFFICIAL)).series
        assertEquals(listOf(351.0, 352.0, 353.5), day.points.map { it.price })
        assertEquals(1794945600L, day.points.first().atEpochSeconds)
    }

    @Test
    fun `each range asks for its own steps, from far enough back, on the full feed first`() {
        val http = object : HttpFetcher {
            val asked = mutableListOf<String>()
            override fun get(url: String, headers: Map<String, String>): HttpResponse {
                asked += url
                // The history of the full feed is refused once, as a plan without it would.
                return if (url.endsWith("feed=sip") && asked.size == 1) HttpResponse(403, "{}") else HttpResponse(200, bars(1.0, 2.0))
            }
        }
        val source = AlpacaSource(http) { now }
        for (range in ChartRange.entries) assertIs<SeriesResult.Ok>(source.series(aapl, range, "id", "secret"))
        val asked = http.asked.map { it.substringAfter("bars?") }
        assertEquals(
            listOf(
                "timeframe=5Min&start=2026-11-13T01:00:00Z&limit=10000&adjustment=split&sort=asc&feed=sip",
                "timeframe=5Min&start=2026-11-13T01:00:00Z&limit=10000&adjustment=split&sort=asc&feed=iex",
                "timeframe=30Min&start=2026-11-11T01:00:00Z&limit=10000&adjustment=split&sort=asc&feed=iex",
                "timeframe=1Hour&start=2026-10-18T01:00:00Z&limit=10000&adjustment=split&sort=asc&feed=iex",
                "timeframe=1Day&start=2025-11-17T01:00:00Z&limit=10000&adjustment=split&sort=asc&feed=iex",
                "timeframe=1Week&start=2021-11-14T01:00:00Z&limit=10000&adjustment=split&sort=asc&feed=iex",
            ),
            asked,
        )
        assertEquals(SeriesResult.Failed(FailReason.NOT_FOUND), AlpacaSource(FakeHttp(mapOf("bars" to HttpResponse(200, """{"bars":null}""")))).series(aapl, ChartRange.YEAR, "i", "s"))
    }

    @Test
    fun `with both keys Finnhub gives the regular price and Alpaca adds the extended one`() {
        val http = FakeHttp(
            mapOf(
                "finnhub.io/api/v1/quote" to HttpResponse(200, """{"c":350.2,"d":5.2,"dp":1.5072,"pc":345.0,"t":1794950400}"""),
                "profile2" to HttpResponse(200, """{"name":"Apple Inc"}"""),
                "AAPL/snapshot" to HttpResponse(200, snapshot("2026-11-17T22:15:00Z", 353.5)),
            ),
        )
        val keys = ApiKeys(finnhub = "f", alpacaId = "id", alpacaSecret = "secret")
        val quote = assertIs<QuoteResult.Ok>(MarketRepository(http).fetch(aapl, keys, MarketSource.OFFICIAL)).quote
        assertEquals(350.2, quote.price)
        assertEquals("Apple Inc", quote.name)
        assertEquals(353.5, quote.extended?.price)
    }

    @Test
    fun `a plan without the full feed falls back to the single exchange, and remembers it`() {
        val http = object : HttpFetcher {
            val asked = mutableListOf<String>()
            override fun get(url: String, headers: Map<String, String>): HttpResponse {
                asked += url
                return if (url.endsWith("delayed_sip")) {
                    HttpResponse(403, """{"message":"subscription does not permit querying recent SIP data"}""")
                } else {
                    HttpResponse(200, snapshot("2026-11-17T22:15:00Z", 353.5))
                }
            }
        }
        val source = AlpacaSource(http)
        assertNotNull(source.extended(aapl, "id", "secret"))
        assertNotNull(source.extended(aapl, "id", "secret"))
        assertEquals(listOf("delayed_sip", "iex", "iex"), http.asked.map { it.substringAfter("feed=") })
    }

    @Test
    fun `trouble at Alpaca never takes the regular price away`() {
        val http = FakeHttp(
            mapOf(
                "finnhub.io/api/v1/quote" to HttpResponse(200, """{"c":350.2,"dp":1.5,"pc":345.0,"t":1794950400}"""),
                "AAPL/snapshot" to HttpResponse(401, """{"message":"unauthorized"}"""),
            ),
        )
        val keys = ApiKeys(finnhub = "f", alpacaId = "id", alpacaSecret = "wrong")
        val quote = assertIs<QuoteResult.Ok>(MarketRepository(http).fetch(aapl, keys, MarketSource.OFFICIAL)).quote
        assertEquals(350.2, quote.price)
        assertNull(quote.extended)
        // Wrong keys with Alpaca alone are reported as such.
        val alone = MarketRepository(http).fetch(aapl, ApiKeys(alpacaId = "id", alpacaSecret = "wrong"), MarketSource.OFFICIAL)
        assertEquals(FailReason.MISSING_KEY, assertIs<QuoteResult.Failed>(alone).reason)
    }

    @Test
    fun `an answer without a trade or a daily bar is not a quote`() {
        val source = AlpacaSource(FakeHttp(emptyMap()))
        assertNull(source.parse("""{"symbol":"ZZZZ","latestTrade":null,"dailyBar":null}"""))
        assertNull(source.parse("""{"symbol":"ZZZZ"}"""))
    }
}
