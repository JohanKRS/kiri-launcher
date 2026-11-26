package io.github.johankrs.kiri.market

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencyConverterTest {
    private val brazil = Locale.forLanguageTag("pt-BR")
    private val rates = CurrencyRates(mapOf("USD" to 5.0, "EUR" to 6.0, "XXX" to 0.0))

    @Test
    fun `converts to and from the real`() {
        assertEquals(500.0, rates.convert(100.0, "USD", "BRL"))
        assertEquals(20.0, rates.convert(100.0, "BRL", "USD"))
        assertEquals(100.0, rates.convert(100.0, "BRL", "BRL"))
    }

    @Test
    fun `converts between two foreign currencies through the real`() {
        assertEquals(100.0, rates.convert(120.0, "USD", "EUR"))
    }

    @Test
    fun `a currency without a price cannot be converted`() {
        assertNull(rates.convert(1.0, "USD", "GBP"))
        assertNull(rates.convert(1.0, "XXX", "BRL"))
        assertEquals(listOf("BRL", "USD", "EUR"), rates.codes)
    }

    @Test
    fun `rates come from the currencies already fetched`() {
        val usd = Instrument(Market.FX, "USD")
        val stock = Instrument(Market.B3, "PETR4")
        val eur = Instrument(Market.FX, "EUR")
        fun ok(instrument: Instrument, price: Double) =
            QuoteResult.Ok(Quote(instrument, "", price, null, "BRL", 0, emptyList()))
        val fetched = CurrencyRates.from(
            mapOf(
                usd to ok(usd, 5.42),
                stock to ok(stock, 38.12),
                eur to QuoteResult.Failed(eur, FailReason.NETWORK),
            ),
        )
        assertEquals(listOf("BRL", "USD"), fetched.codes)
        assertEquals(54.2, fetched.convert(10.0, "USD", "BRL")!!, 1e-9)
    }

    @Test
    fun `typed amounts keep digits, one decimal mark and two decimals`() {
        assertEquals("37,5", ConverterAmount.clean("37,5", brazil))
        assertEquals("37,50", ConverterAmount.clean("37.509", brazil))
        assertEquals("1234", ConverterAmount.clean("1 234abc", brazil))
        assertEquals("0,5", ConverterAmount.clean(",5", brazil))
        assertEquals("1.5", ConverterAmount.clean("1,5", Locale.US))
        assertEquals("", ConverterAmount.clean("abc", brazil))
        assertEquals(37.5, ConverterAmount.parse("37,5"))
        assertEquals(37.0, ConverterAmount.parse("37,"))
        assertEquals(0.0, ConverterAmount.parse(""))
    }

    @Test
    fun `minus and plus move by the chosen step and stop at zero`() {
        assertEquals(110.0, ConverterAmount.stepped(100.0, 10, 1))
        assertEquals(0.0, ConverterAmount.stepped(5.0, 10, -1))
        assertEquals(1037.5, ConverterAmount.stepped(37.5, 1000, 1))
        assertEquals("1037,5", ConverterAmount.format(1037.5, brazil))
        assertEquals("100", ConverterAmount.format(100.0, brazil))
    }
}
