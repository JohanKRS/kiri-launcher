package io.github.johankrs.kiri.widgets.market

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.WidgetSpec
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.market.ExtendedQuote
import io.github.johankrs.kiri.market.ExtendedSession
import io.github.johankrs.kiri.market.FailReason
import io.github.johankrs.kiri.market.Instrument
import io.github.johankrs.kiri.market.Market
import io.github.johankrs.kiri.market.MarketFormat
import io.github.johankrs.kiri.market.MarketRepository
import io.github.johankrs.kiri.market.MarketWidgetSpec
import io.github.johankrs.kiri.market.QuoteResult
import io.github.johankrs.kiri.market.extra.ExtraSources
import io.github.johankrs.kiri.market.sparkline
import io.github.johankrs.kiri.widgets.KiriWidget
import io.github.johankrs.kiri.widgets.SnapScroll
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Stocks and currencies a person follows, on a pane of glass. */
@Stable
class MarketWidget(context: Context) : KiriWidget {
    private val store = MarketStore(context)
    private val repository = MarketRepository(extras = ExtraSources::create)
    private val controller = MarketController(repository)

    override val spec: WidgetSpec = MarketWidgetSpec.SPEC
    override val titleRes: Int = R.string.market_title
    override val summaryRes: Int = R.string.market_summary
    override val hasSettings: Boolean = true

    @Composable
    override fun Content(size: CellSpan, modifier: Modifier) {
        val stored by store.prefs.collectAsState(initial = null)
        val prefs = stored
        if (prefs == null) {
            Box(modifier)
            return
        }
        MarketAutoRefresh(controller, prefs)
        MarketWithPanes(controller, prefs, modifier)
    }

    /**
     * The lines of the widget for a screen that draws them itself, as the TV's home screen
     * does, kept up to date while they are shown. Null while the settings are being read.
     */
    @Composable
    fun liveRows(): List<MarketRow>? {
        val prefs = store.prefs.collectAsState(initial = null).value ?: return null
        MarketAutoRefresh(controller, prefs)
        return marketRows(prefs.watchlist.visible, controller)
    }

    @Composable
    override fun Settings(onBack: () -> Unit) {
        MarketSettingsScreen(store = store, controller = controller, repository = repository, onBack = onBack)
    }
}

/**
 * The widget with everything a tap on it opens: the converter for a currency, the details for
 * a stock, both growing out of the widget's own glass and going back into it.
 */
@Composable
fun MarketWithPanes(controller: MarketController, prefs: MarketPrefs, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val rows = marketRows(prefs.watchlist.visible, controller)
    val updated = updatedText(controller)
    val refreshing by controller.refreshing.collectAsState()
    // The id of the item whose pane is open, kept across a rotation.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept outside observed state so page turns and drags never recompose the widget.
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var originBounds by remember { mutableStateOf(Rect.Zero) }
    var covered by remember { mutableStateOf(false) }
    // The picture of the widget a pane grows out of shows the same items as the widget.
    val scroll = remember { SnapScroll() }

    fun openInstrument(id: String) {
        val current = coords[0]?.takeIf { it.isAttached }
        originBounds = if (current != null) Rect(current.positionOnScreen(), current.size.toSize()) else Rect.Zero
        opened = id
    }

    // The panel reads the space it really gets, so it follows any size the grid gives it.
    MarketPanel(
        settings = prefs.settings,
        rows = rows,
        updatedText = updated,
        modifier = modifier
            .onPlaced { coords[0] = it }
            .graphicsLayer { alpha = if (covered) 0f else 1f },
        onCurrencyClick = { openInstrument(Instrument(Market.FX, it).id) },
        onStockClick = { openInstrument(it) },
        refreshing = refreshing,
        onRefresh = { scope.launch { controller.refreshNow(prefs) } },
        scroll = scroll,
    )

    val instrument = opened?.let(Instrument::parse) ?: return
    MarketOverlay(
        origin = originBounds,
        originCorner = prefs.settings.cornerDp.dp,
        onCover = { covered = it },
        onClosed = { opened = null },
        start = { MarketPanel(prefs.settings, rows, updated, Modifier.fillMaxSize(), scroll = scroll) },
    ) { close ->
        if (instrument.market == Market.FX) {
            ConverterPane(
                initialFrom = instrument.symbol,
                rates = converterRates(controller),
                settings = prefs.settings,
                updatedText = updated,
                refreshing = refreshing,
                onRefresh = { scope.launch { controller.refreshNow(prefs) } },
                onClose = close,
            )
        } else {
            MarketDetailPane(instrument, controller, prefs, onClose = close)
        }
    }
}

/** The lines to show for [instruments], in order, with whatever is known about each so far. */
@Composable
fun marketRows(instruments: List<Instrument>, controller: MarketController): List<MarketRow> {
    val quotes by controller.quotes.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val noteMissingKey = stringResource(R.string.market_note_missing_key)
    val noteNotFound = stringResource(R.string.market_note_not_found)
    val noteRateLimited = stringResource(R.string.market_note_rate_limited)
    val noteNetwork = stringResource(R.string.market_note_network)
    val noteBadResponse = stringResource(R.string.market_note_bad_response)
    val preShort = stringResource(R.string.market_extended_pre)
    val preLong = stringResource(R.string.market_extended_pre_long)
    val postShort = stringResource(R.string.market_extended_post)
    val postLong = stringResource(R.string.market_extended_post_long)
    val notes = remember(noteMissingKey, noteNotFound, noteRateLimited, noteNetwork, noteBadResponse) {
        mapOf(
            FailReason.MISSING_KEY to noteMissingKey,
            FailReason.NOT_FOUND to noteNotFound,
            FailReason.RATE_LIMITED to noteRateLimited,
            FailReason.NETWORK to noteNetwork,
            FailReason.BAD_RESPONSE to noteBadResponse,
        )
    }
    val sessions = remember(preShort, preLong, postShort, postLong) {
        mapOf(
            ExtendedSession.PRE to (preShort to preLong),
            ExtendedSession.POST to (postShort to postLong),
        )
    }
    return remember(instruments, quotes, locale, notes, sessions) {
        instruments.map { toRow(it, quotes[it], locale, notes, sessions) }
    }
}

/** "Updated at 10:28", or a note that the first prices are on their way. */
@Composable
fun updatedText(controller: MarketController): String {
    val updatedAt by controller.updatedAt.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val at = updatedAt.takeUnless { refreshing } ?: return stringResource(R.string.market_updating)
    val formatted = remember(at, context, locale) { DateFormat.getTimeFormat(context).format(Date(at)) }
    return stringResource(R.string.market_updated_at, formatted)
}

private fun toRow(
    instrument: Instrument,
    result: QuoteResult?,
    locale: Locale,
    notes: Map<FailReason, String>,
    sessions: Map<ExtendedSession, Pair<String, String>>,
): MarketRow {
    val name = when (instrument.market) {
        Market.FX -> currencyName(instrument.symbol, locale)
        Market.B3, Market.US -> (result as? QuoteResult.Ok)?.quote?.name.orEmpty()
    }
    return when (result) {
        null -> MarketRow(instrument.symbol, name, "", "…", "", Trend.UNKNOWN, emptyList())

        is QuoteResult.Failed ->
            MarketRow(instrument.symbol, name, "", "—", notes.getValue(result.reason), Trend.UNKNOWN, emptyList())

        is QuoteResult.Ok -> {
            val quote = result.quote
            val change = quote.changePercent
            MarketRow(
                symbol = instrument.symbol,
                name = name,
                priceSign = MarketFormat.currencySymbol(quote.currency),
                priceNumber = MarketFormat.amount(quote.price, locale),
                changeText = change?.let { MarketFormat.percent(it, locale) }.orEmpty(),
                trend = trendOf(change),
                chart = sparkline(quote.history),
                // Only a currency with a price can be converted, and only a stock with one opened.
                currency = instrument.symbol.takeIf { instrument.market == Market.FX },
                stock = instrument.id.takeIf { instrument.market != Market.FX },
                extended = quote.extended?.let { toExtendedRow(it, locale, sessions) },
            )
        }
    }
}

private fun toExtendedRow(
    extended: ExtendedQuote,
    locale: Locale,
    sessions: Map<ExtendedSession, Pair<String, String>>,
): ExtendedRow {
    val (label, longLabel) = sessions.getValue(extended.session)
    return ExtendedRow(
        label = label,
        longLabel = longLabel,
        // The currency sign is on the regular price, right above.
        priceNumber = MarketFormat.amount(extended.price, locale),
        changeText = extended.changePercent?.let { MarketFormat.percent(it, locale) }.orEmpty(),
        trend = trendOf(extended.changePercent),
    )
}

private fun trendOf(change: Double?): Trend = when {
    change == null -> Trend.UNKNOWN
    abs(change) < 0.005 -> Trend.FLAT
    change > 0 -> Trend.UP
    else -> Trend.DOWN
}

/** "Dólar americano" for USD, in the person's language. Empty when the code is not a currency. */
internal fun currencyName(code: String, locale: Locale): String =
    runCatching { Currency.getInstance(code).getDisplayName(locale) }
        .getOrNull()
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        .orEmpty()
