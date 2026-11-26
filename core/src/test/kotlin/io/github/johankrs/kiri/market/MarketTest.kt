package io.github.johankrs.kiri.market

import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.GlassStyle
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketTest {
    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val today = LocalDate.of(2026, 11, 17)

    private val petr4 = Instrument(Market.B3, "PETR4")
    private val aapl = Instrument(Market.US, "AAPL")
    private val usd = Instrument(Market.FX, "USD")

    /** Answers by the first matching part of the URL and records what was asked. */
    private class FakeHttp(private val answers: List<Pair<String, HttpResponse>>) : HttpFetcher {
        val requests = ArrayList<Pair<String, Map<String, String>>>()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            requests += url to headers
            return answers.firstOrNull { url.contains(it.first) }?.second ?: throw IOException("offline")
        }
    }

    private fun ok(body: String) = HttpResponse(200, body)

    private val brapiBody = """
        {"results":[{"symbol":"PETR4","shortName":"PETR4","longName":"Petroleo Brasileiro SA Pfd",
        "currency":"BRL","regularMarketPrice":44.36,"regularMarketChange":0.34,
        "regularMarketChangePercent":0.77,"regularMarketTime":"2026-11-17T13:27:30.000Z",
        "historicalDataPrice":[{"date":1,"close":44.02},{"date":2,"close":null},{"date":3,"close":44.36}]}],
        "requestedAt":"2026-11-17T13:26:48.179Z","took":0}
    """.trimIndent()

    private val awesomeBody = """
        {"USDBRL":{"code":"USD","codein":"BRL","name":"Dólar Americano/Real Brasileiro","high":"5.72",
        "low":"5.68","varBid":"0.0123","pctChange":"0.21","bid":"5.7101","ask":"5.7111",
        "timestamp":"1794922000","create_date":"2026-11-17 10:26:40"}}
    """.trimIndent()

    private val frankfurterBody = """
        [{"date":"2026-11-16","base":"USD","quote":"BRL","rate":5.7052},
         {"date":"2026-11-12","base":"USD","quote":"BRL","rate":5.617},
         {"date":"2026-11-13","base":"USD","quote":"BRL","rate":5.6638}]
    """.trimIndent()

    /** AwesomeAPI's minutes, newest first, the first with the whole entry; times as it writes them. */
    private val minutesBody = """
        [{"code":"USD","codein":"BRL","name":"Dólar Americano/Real Brasileiro","high":"5.72","low":"5.68",
          "varBid":"0.0123","pctChange":"0.21","bid":"5.7101","ask":"5.7111","timestamp":"1794922080",
          "create_date":"2026-11-17 10:28:00"},
         {"high":"5.72","low":"5.68","varBid":"0.01","pctChange":"0.19","bid":"5.7090","ask":"5.71","timestamp":"1794922020"},
         {"high":"5.72","low":"5.68","varBid":"0.01","pctChange":"0.15","bid":"5.7050","ask":"5.71","timestamp":"1794921000000"},
         {"high":"5.72","low":"5.68","varBid":"0.01","pctChange":"0.10","bid":"5.7000","ask":"5.70","timestamp":"1794920000"}]
    """.trimIndent()

    // --- What a person follows -------------------------------------------------------------

    @Test
    fun `typed codes are tidied and checked per market`() {
        assertEquals(petr4, Instrument.fromInput(Market.B3, " petr4 "))
        assertEquals(Instrument(Market.B3, "BOVA11"), Instrument.fromInput(Market.B3, "bova11"))
        assertEquals(Instrument(Market.US, "BRK.B"), Instrument.fromInput(Market.US, "brk.b"))
        assertNull(Instrument.fromInput(Market.B3, "AAPL"))
        assertNull(Instrument.fromInput(Market.US, "PETR4"))
        assertNull(Instrument.fromInput(Market.FX, "BRL"), "the real is the base, not something to follow")
        assertNull(Instrument.fromInput(Market.FX, "dollar"))
    }

    @Test
    fun `an instrument survives being stored as text`() {
        for (instrument in listOf(petr4, aapl, usd)) {
            assertEquals(instrument, Instrument.parse(instrument.id))
        }
        assertNull(Instrument.parse("PETR4"))
        assertNull(Instrument.parse("XX:PETR4"))
    }

    @Test
    fun `the list keeps order, hidden items and drops rubbish`() {
        val list = Watchlist.DEFAULT.add(aapl).setEnabled(1, false).move(4, -4)
        assertEquals(listOf(aapl, usd, Instrument(Market.B3, "VALE3"), Instrument(Market.B3, "ITUB4")), list.visible)
        assertEquals(list, Watchlist.decode(list.encode()))

        val decoded = Watchlist.decode("FX:USD=1,nonsense,B3:PETR4=0,FX:USD=1,US:AAPL")
        assertEquals(listOf(WatchEntry(usd), WatchEntry(petr4, enabled = false), WatchEntry(aapl)), decoded.entries)
        assertEquals(Watchlist.DEFAULT, Watchlist.decode(null))
        assertEquals(Watchlist(emptyList()), Watchlist.decode(""), "an emptied list stays empty")
    }

    @Test
    fun `adding something already listed shows it again instead of repeating it`() {
        val hidden = Watchlist.DEFAULT.setEnabled(0, false)
        assertEquals(Watchlist.DEFAULT, hidden.add(usd))
        assertEquals(Watchlist.DEFAULT, Watchlist.DEFAULT.move(0, -1), "moving past the top does nothing")
        assertEquals(3, Watchlist.DEFAULT.removeAt(0).entries.size)
    }

    // --- Brazilian stocks ------------------------------------------------------------------

    @Test
    fun `a brazilian quote is read with the chart of its last hours`() {
        val http = FakeHttp(listOf("brapi.dev" to ok(brapiBody)))
        val result = MarketRepository(http).fetch(petr4, ApiKeys(brapi = " my-token "), MarketSource.OFFICIAL, today)

        val quote = assertIs<QuoteResult.Ok>(result).quote
        assertEquals(44.36, quote.price)
        assertEquals(0.77, quote.changePercent)
        assertEquals("BRL", quote.currency)
        assertEquals("Petroleo Brasileiro SA Pfd", quote.name)
        assertEquals(listOf(44.02, 44.36), quote.history, "steps without a close are skipped")
        assertEquals(1794922050L, quote.asOfEpochSeconds)

        val (url, headers) = http.requests.single()
        assertTrue(url.endsWith("/PETR4?range=5d&interval=15m"))
        assertEquals("Bearer my-token", headers["Authorization"])
        assertTrue("my-token" !in url, "the key must never travel in the URL")
    }

    @Test
    fun `the widget's chart is of the last six hours of trading, whenever they were`() {
        // Thirty steps of a quarter of an hour: the end of one session and the start of the next.
        val steps = List(30) { index -> (if (index < 20) 1000L else 60_000L) + index * 900L to index.toDouble() }
        val chart = lastTradingHoursOf(steps)
        // Six hours of steps and the one under way; the night between the sessions does not count.
        assertEquals(25, chart.size)
        assertEquals(5.0, chart.first())
        assertEquals(29.0, chart.last())
        assertEquals(listOf(0.0, 1.0), lastTradingHoursOf(steps.take(2)))
        assertEquals(emptyList(), lastTradingHoursOf(emptyList()))
    }

    @Test
    fun `the large chart's day is the 24 hours that end at the latest price`() {
        val day = 86_400L
        val steps = listOf(1000L to 1.0, 1000L + day to 2.0, 1900L + day to 3.0, 1000L + 2 * day to 4.0)
        // The step exactly 24 hours before the last one is already out.
        assertEquals(listOf(3.0, 4.0), lastDayOf(steps).map { it.second })
        assertEquals(emptyList(), lastDayOf(emptyList()))
    }

    @Test
    fun `a brazilian chart is read for each range, and says more about the day`() {
        val body = """
            {"results":[{"symbol":"PETR4","currency":"BRL","regularMarketPrice":44.0,
            "regularMarketDayHigh":44.75,"regularMarketDayLow":43.9,"regularMarketPreviousClose":44.02,
            "regularMarketVolume":20904100,"fiftyTwoWeekHigh":59.79,"fiftyTwoWeekLow":29.53,
            "historicalDataPrice":[{"date":1794855600,"close":43.1},{"date":1794859200,"close":43.5},
            {"date":1794942000,"close":null},{"date":1794943800,"close":44.01}]}]}
        """.trimIndent()
        val details = assertIs<QuoteResult.Ok>(BrapiSource(FakeHttp(emptyList())).parse(petr4, body)).quote.details
        assertEquals(QuoteDetails(44.75, 43.9, 44.02, 20904100L, 59.79, 29.53), details)

        val http = FakeHttp(listOf("brapi.dev" to ok(body)))
        val repository = MarketRepository(http)
        val week = assertIs<SeriesResult.Ok>(repository.series(petr4, ChartRange.WEEK, ApiKeys(), MarketSource.OFFICIAL)).series
        assertEquals(listOf(43.1, 43.5, 44.01), week.points.map { it.price })
        assertEquals(43.1, week.low)
        assertEquals(44.01, week.high)
        assertEquals((44.01 / 43.1 - 1.0) * 100.0, week.changePercent!!, 1e-9)
        assertEquals(listOf(0f, 1f), listOf(week.heights.first(), week.heights.last()))
        // The day is yesterday from the same hour on, then today.
        val day = assertIs<SeriesResult.Ok>(repository.series(petr4, ChartRange.DAY, ApiKeys(), MarketSource.OFFICIAL)).series
        assertEquals(listOf(43.5, 44.01), day.points.map { it.price })
        assertTrue(http.requests.first().first.endsWith("/PETR4?range=5d&interval=30m"))
        assertTrue(http.requests.last().first.endsWith("/PETR4?range=5d&interval=5m"))

        // US stocks have their chart from Alpaca: without its key, nobody is asked.
        val offline = MarketRepository(FakeHttp(emptyList()))
        assertEquals(SeriesResult.Failed(FailReason.MISSING_KEY), offline.series(aapl, ChartRange.YEAR, ApiKeys(), MarketSource.OFFICIAL))
        assertEquals(SeriesResult.Failed(FailReason.NETWORK), offline.series(petr4, ChartRange.YEAR, ApiKeys(), MarketSource.OFFICIAL))
    }

    @Test
    fun `brazilian failures say what is wrong`() {
        fun fail(code: Int) = MarketRepository(FakeHttp(listOf("brapi.dev" to HttpResponse(code, "{}"))))
            .fetch(petr4, ApiKeys(), MarketSource.OFFICIAL, today)

        assertEquals(QuoteResult.Failed(petr4, FailReason.MISSING_KEY), fail(401))
        assertEquals(QuoteResult.Failed(petr4, FailReason.NOT_FOUND), fail(404))
        assertEquals(QuoteResult.Failed(petr4, FailReason.RATE_LIMITED), fail(429))
        assertEquals(QuoteResult.Failed(petr4, FailReason.NETWORK), MarketRepository(FakeHttp(emptyList())).fetch(petr4, ApiKeys(), MarketSource.OFFICIAL, today))
        assertEquals(
            QuoteResult.Failed(petr4, FailReason.BAD_RESPONSE),
            MarketRepository(FakeHttp(listOf("brapi.dev" to ok("<html>")))).fetch(petr4, ApiKeys(), MarketSource.OFFICIAL, today),
        )
    }

    @Test
    fun `no key is sent when there is none`() {
        val http = FakeHttp(listOf("brapi.dev" to ok(brapiBody)))
        MarketRepository(http).fetch(petr4, ApiKeys(), MarketSource.OFFICIAL, today)
        assertTrue(http.requests.single().second.isEmpty())
    }

    // --- US stocks -------------------------------------------------------------------------

    @Test
    fun `a us quote needs the person's key and picks up the company name once`() {
        assertEquals(
            QuoteResult.Failed(aapl, FailReason.MISSING_KEY),
            MarketRepository(FakeHttp(emptyList())).fetch(aapl, ApiKeys(), MarketSource.OFFICIAL, today),
        )

        val http = FakeHttp(
            listOf(
                "/quote?symbol=AAPL" to ok("""{"c":352.78,"d":-3.49,"dp":-0.9796,"h":356.1,"l":351.2,"o":355.9,"pc":356.27,"t":1794873600}"""),
                "/stock/profile2?symbol=AAPL" to ok("""{"country":"US","name":"Apple Inc","ticker":"AAPL"}"""),
            ),
        )
        val repository = MarketRepository(http)
        val quote = assertIs<QuoteResult.Ok>(repository.fetch(aapl, ApiKeys(finnhub = "k"), MarketSource.OFFICIAL, today)).quote
        assertEquals(352.78, quote.price)
        assertEquals(-0.9796, quote.changePercent)
        assertEquals("USD", quote.currency)
        assertEquals("Apple Inc", quote.name)
        assertEquals("k", http.requests.first().second["X-Finnhub-Token"])

        repository.fetch(aapl, ApiKeys(finnhub = "k"), MarketSource.OFFICIAL, today)
        assertEquals(1, http.requests.count { "profile2" in it.first }, "the name is asked for only once")
    }

    @Test
    fun `an unknown us symbol comes back as zeros and is reported as not found`() {
        val http = FakeHttp(listOf("/quote" to ok("""{"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}""")))
        assertEquals(
            QuoteResult.Failed(Instrument(Market.US, "ZZZZ"), FailReason.NOT_FOUND),
            MarketRepository(http).fetch(Instrument(Market.US, "ZZZZ"), ApiKeys(finnhub = "k"), MarketSource.OFFICIAL, today),
        )
    }

    // --- Currencies ------------------------------------------------------------------------

    @Test
    fun `without a key a currency takes the live price, with the last days as its chart`() {
        val http = FakeHttp(listOf("awesomeapi" to ok(awesomeBody), "frankfurter" to ok(frankfurterBody)))
        val quote = assertIs<QuoteResult.Ok>(MarketRepository(http).fetch(usd, ApiKeys(), MarketSource.OFFICIAL, today)).quote

        assertEquals(5.7101, quote.price)
        assertEquals(0.21, quote.changePercent)
        assertEquals("BRL", quote.currency)
        assertEquals(listOf(5.617, 5.6638, 5.7052), quote.history, "days come out oldest first")
        assertTrue(http.requests.first().first.endsWith("/json/last/USD-BRL"))
        assertTrue(http.requests.last().first.endsWith("/v2/rates?from=2026-10-27&base=USD&quotes=BRL"))
        assertTrue(http.requests.all { it.second.isEmpty() })
    }

    @Test
    fun `with the AwesomeAPI key one answer gives the price and the last hours`() {
        val http = FakeHttp(listOf("awesomeapi" to ok(minutesBody)))
        val quote = assertIs<QuoteResult.Ok>(MarketRepository(http).fetch(usd, ApiKeys(awesome = " k "), MarketSource.OFFICIAL, today)).quote

        assertEquals(5.7101, quote.price)
        assertEquals(0.21, quote.changePercent)
        assertEquals(1794922080L, quote.asOfEpochSeconds)
        // One price for each quarter of an hour, the last of it; a time in milliseconds counts too.
        assertEquals(listOf(5.7, 5.705, 5.7101), quote.history)
        assertEquals("https://economia.awesomeapi.com.br/USD-BRL/480" to mapOf("x-api-key" to "k"), http.requests.single())
    }

    @Test
    fun `the large chart of a currency needs the key for the day and none for the rest`() {
        val http = FakeHttp(listOf("awesomeapi" to ok(minutesBody), "frankfurter" to ok(frankfurterBody)))
        val repository = MarketRepository(http)
        fun series(range: ChartRange, keys: ApiKeys = ApiKeys()) = repository.series(usd, range, keys, MarketSource.OFFICIAL, today)

        assertEquals(SeriesResult.Failed(FailReason.MISSING_KEY), series(ChartRange.DAY))
        assertTrue(http.requests.isEmpty())
        val day = assertIs<SeriesResult.Ok>(series(ChartRange.DAY, ApiKeys(awesome = "k"))).series
        assertEquals(listOf(5.7, 5.705, 5.709, 5.7101), day.points.map { it.price })
        assertTrue(http.requests.last().first.endsWith("/USD-BRL/1500"))

        val month = assertIs<SeriesResult.Ok>(series(ChartRange.MONTH)).series
        assertEquals(listOf(5.617, 5.6638, 5.7052), month.points.map { it.price })
        series(ChartRange.FIVE_YEARS)
        assertEquals(
            listOf("from=2026-10-17&base=USD&quotes=BRL", "from=2021-11-17&base=USD&quotes=BRL&group=week"),
            http.requests.takeLast(2).map { it.first.substringAfter('?') },
        )
    }

    @Test
    fun `when the live source is over its limit the last daily rate stands in`() {
        val http = FakeHttp(listOf("awesomeapi" to HttpResponse(429, "{}"), "frankfurter" to ok(frankfurterBody)))
        val quote = assertIs<QuoteResult.Ok>(MarketRepository(http).fetch(usd, ApiKeys(), MarketSource.OFFICIAL, today)).quote

        assertEquals(5.7052, quote.price, "days come out oldest first")
        assertEquals(0.73, quote.changePercent!!, absoluteTolerance = 0.005)
        assertEquals(listOf(5.617, 5.6638, 5.7052), quote.history)
    }

    @Test
    fun `a currency with no source at all reports the live failure`() {
        val http = FakeHttp(listOf("awesomeapi" to HttpResponse(429, "{}")))
        assertEquals(QuoteResult.Failed(usd, FailReason.RATE_LIMITED), MarketRepository(http).fetch(usd, ApiKeys(), MarketSource.OFFICIAL, today))
    }

    // --- Trying a key ----------------------------------------------------------------------

    @Test
    fun `a pasted key is tried on a real price, and a wrong one is told apart from other trouble`() {
        fun check(service: KeyService, keys: ApiKeys, vararg answers: Pair<String, HttpResponse>) =
            MarketRepository(FakeHttp(answers.toList())).check(service, keys)

        val works = assertIs<KeyCheck.Works>(check(KeyService.BRAPI, ApiKeys(brapi = "t"), "brapi.dev" to ok(brapiBody)))
        assertEquals(44.36, works.sample.price)
        assertEquals(KeyCheck.Refused, check(KeyService.BRAPI, ApiKeys(brapi = "t"), "brapi.dev" to HttpResponse(401, "{}")))
        assertEquals(KeyCheck.OutOfRequests, check(KeyService.FINNHUB, ApiKeys(finnhub = "f"), "finnhub" to HttpResponse(429, "{}")))
        assertEquals(KeyCheck.Offline, check(KeyService.ALPACA, ApiKeys(alpacaId = "i", alpacaSecret = "s")))
        // A refused AwesomeAPI key is not hidden by the reference rates, which need none.
        val awesome = check(
            KeyService.AWESOME,
            ApiKeys(awesome = "wrong"),
            "awesomeapi" to HttpResponse(401, "{}"),
            "frankfurter" to ok(frankfurterBody),
        )
        assertEquals(KeyCheck.Refused, awesome)
        assertIs<KeyCheck.Works>(check(KeyService.AWESOME, ApiKeys(awesome = "k"), "awesomeapi" to ok(minutesBody)))

        // The brapi token is tried on a stock that does not answer without one.
        val http = FakeHttp(listOf("brapi.dev" to ok(brapiBody)))
        MarketRepository(http).check(KeyService.BRAPI, ApiKeys(brapi = "t"))
        assertTrue(http.requests.first().first.contains("/BBAS3"))
    }

    // --- Writing numbers -------------------------------------------------------------------

    @Test
    fun `prices and changes are written the brazilian way`() {
        assertEquals("R$ 44,36", MarketFormat.price(44.36, "BRL", ptBr))
        assertEquals("US$ 1.352,78", MarketFormat.price(1352.78, "USD", ptBr))
        assertEquals("R$ 0,0357", MarketFormat.price(0.0357, "BRL", ptBr))
        assertEquals("384,61", MarketFormat.amount(384.61, ptBr), "the number alone carries no currency sign")
        assertEquals("0,0357", MarketFormat.amount(0.0357, ptBr))
        assertEquals(
            MarketFormat.price(384.61, "USD", ptBr),
            MarketFormat.currencySymbol("USD") + "\u00a0" + MarketFormat.amount(384.61, ptBr),
            "sign and number are joined by a space that never breaks",
        )
        assertEquals("+0,77%", MarketFormat.percent(0.77, ptBr))
        assertEquals("−0,13%", MarketFormat.percent(-0.13, ptBr))
        assertEquals("0,00%", MarketFormat.percent(-0.001, ptBr), "a change that rounds to zero has no sign")
        assertEquals("$44.36", MarketFormat.price(44.36, "BRL", Locale.US).replace("R", "").replace(" ", ""))
    }

    @Test
    fun `the small chart spans from lowest to highest`() {
        assertEquals(listOf(0f, 1f, 0.5f), sparkline(listOf(10.0, 20.0, 15.0)))
        assertEquals(listOf(0.5f, 0.5f), sparkline(listOf(7.0, 7.0)))
        assertEquals(emptyList(), sparkline(listOf(7.0)))
    }

    // --- Settings and sizes ----------------------------------------------------------------

    @Test
    fun `settings stay in range and each glass starts at its own opacity`() {
        val wild = MarketSettings(opacityPercent = 500, cornerDp = 0, refreshMinutes = 45).coerced()
        assertEquals(92, wild.opacityPercent)
        assertEquals(8, wild.cornerDp)
        assertEquals(30, wild.refreshMinutes)

        assertEquals(GlassStyle.FUME, MarketSettings().glass)
        assertEquals(36, MarketSettings().withGlass(GlassStyle.CRISTAL).opacityPercent)
        assertEquals(60, MarketSettings().refreshStepped(1)?.refreshMinutes)
        assertNull(MarketSettings().refreshStepped(-1))
        assertEquals(GlassStyle.FUME, GlassStyle.fromKey("something old"))
    }

    @Test
    fun `prices go stale after the chosen interval`() {
        assertTrue(isStale(null, nowMillis = 0, refreshMinutes = 30))
        assertTrue(!isStale(1_000, nowMillis = 1_000 + 29 * 60_000L, refreshMinutes = 30))
        assertTrue(isStale(1_000, nowMillis = 1_000 + 30 * 60_000L, refreshMinutes = 30))
    }

    @Test
    fun `the widget accepts any size between its ends`() {
        assertEquals(CellSpan(8, 8), MarketWidgetSpec.SPEC.coerce(CellSpan(12, 9)))
        assertEquals(CellSpan(2, 1), MarketWidgetSpec.SPEC.coerce(CellSpan(1, 1)))
        assertEquals(CellSpan(5, 2), MarketWidgetSpec.SPEC.coerce(CellSpan(5, 2)))
    }
}
