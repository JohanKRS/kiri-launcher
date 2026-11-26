package io.github.johankrs.kiri.widgets.market

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.johankrs.kiri.market.ApiKeys
import io.github.johankrs.kiri.market.ChartRange
import io.github.johankrs.kiri.market.FailReason
import io.github.johankrs.kiri.market.Instrument
import io.github.johankrs.kiri.market.MarketRepository
import io.github.johankrs.kiri.market.MarketSource
import io.github.johankrs.kiri.market.QuoteResult
import io.github.johankrs.kiri.market.SeriesResult
import io.github.johankrs.kiri.market.isStale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val CHECK_EVERY_MS = 60_000L

/**
 * At most this many prices are asked for at the same time: a little faster than one by one,
 * without the burst that makes a source answer "too many requests".
 */
private const val MAX_CONCURRENT_FETCHES = 2

/** A refresh asked for by hand shows for at least this long, so it is seen to have happened. */
private const val MANUAL_SHOWN_MS = 600L

/** Asking by hand again sooner than this after an update finds nothing new. */
private const val MANUAL_GAP_MS = 5_000L

/** A large chart is kept this long, so going back and forth between ranges asks only once. */
private const val SERIES_KEPT_MS = 5 * 60_000L

/** Holds the latest prices and decides when asking again is worth it. */
@Stable
class MarketController(private val repository: MarketRepository) {
    private val _quotes = MutableStateFlow<Map<Instrument, QuoteResult>>(emptyMap())
    val quotes: StateFlow<Map<Instrument, QuoteResult>> = _quotes

    /** When prices last came in for the visible items, in milliseconds since 1970. */
    private val _updatedAt = MutableStateFlow<Long?>(null)
    val updatedAt: StateFlow<Long?> = _updatedAt

    /**
     * When every visible item was last asked for, whether or not anything came back. The next
     * round waits from here, so a source that refuses (too many requests, a wrong key, a used
     * up quota) is not asked again every minute.
     */
    private var askedAt: Long? = null

    /** True while a refresh asked for by hand is under way. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** The large charts already fetched, with when, by what they are of. */
    private val charts = ConcurrentHashMap<Triple<Instrument, ChartRange, MarketSource>, Pair<Long, SeriesResult>>()

    private val mutex = Mutex()
    private var lastAsked: Pair<MarketSource, ApiKeys>? = null

    /**
     * Brings prices up to date. Asks for everything when they are older than [refreshMinutes]
     * or the source or the keys changed; otherwise only for items that have no price yet.
     */
    suspend fun refresh(instruments: List<Instrument>, source: MarketSource, keys: ApiKeys, refreshMinutes: Int) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val asking = source to keys
            val everything = asking != lastAsked || isStale(askedAt, now, refreshMinutes)
            val wanted = if (everything) instruments else instruments.filter { it !in _quotes.value }
            if (wanted.isEmpty()) return
            val anyOk = load(wanted, source, keys)
            if (everything) askedAt = now
            if (everything && anyOk) _updatedAt.value = now
        }
    }

    /**
     * Asks for everything now, because the person asked. Taps while one is under way, or right
     * after an update, ask for nothing more: they only show that the prices are fresh.
     */
    suspend fun refreshNow(instruments: List<Instrument>, source: MarketSource, keys: ApiKeys) {
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        try {
            val started = System.currentTimeMillis()
            mutex.withLock {
                val now = System.currentTimeMillis()
                val updated = _updatedAt.value
                val fresh = (source to keys) == lastAsked && updated != null && now - updated < MANUAL_GAP_MS
                if (!fresh && instruments.isNotEmpty()) {
                    charts.clear()
                    val anyOk = load(instruments, source, keys)
                    askedAt = now
                    if (anyOk) _updatedAt.value = now
                }
            }
            val remaining = MANUAL_SHOWN_MS - (System.currentTimeMillis() - started)
            if (remaining > 0L) delay(remaining)
        } finally {
            _refreshing.value = false
        }
    }

    /** The prices of [range] for the large chart of [instrument]. Failures are not kept. */
    suspend fun series(instrument: Instrument, range: ChartRange, source: MarketSource, keys: ApiKeys): SeriesResult {
        val what = Triple(instrument, range, source)
        val now = System.currentTimeMillis()
        charts[what]?.let { (at, kept) -> if (now - at < SERIES_KEPT_MS) return kept }
        val fresh = withContext(Dispatchers.IO) { repository.series(instrument, range, keys, source) }
        if (fresh is SeriesResult.Ok) charts[what] = now to fresh
        return fresh
    }

    suspend fun refreshNow(prefs: MarketPrefs) =
        refreshNow(prefs.watchlist.visible, prefs.settings.source, prefs.keys)

    /**
     * Fetches [wanted] concurrently (up to [MAX_CONCURRENT_FETCHES] at a time) and merges what
     * came back into [quotes]. Returns true when at least one quote succeeded. Call with [mutex] held.
     */
    private suspend fun load(wanted: List<Instrument>, source: MarketSource, keys: ApiKeys): Boolean {
        val fresh = withContext(Dispatchers.IO) {
            val limit = Semaphore(MAX_CONCURRENT_FETCHES)
            coroutineScope {
                wanted.map { instrument ->
                    async {
                        limit.withPermit {
                            ensureActive()
                            repository.fetch(instrument, keys, source)
                        }
                    }
                }.awaitAll()
            }
        }
        val merged = _quotes.value.toMutableMap()
        var anyOk = false
        for (result in fresh) {
            if (result is QuoteResult.Ok) anyOk = true
            val known = merged[result.instrument]
            // A passing network problem should not wipe a price that is already on screen.
            val passing = result is QuoteResult.Failed &&
                (result.reason == FailReason.NETWORK || result.reason == FailReason.RATE_LIMITED)
            if (!(passing && known is QuoteResult.Ok)) merged[result.instrument] = result
        }
        _quotes.value = merged
        lastAsked = source to keys
        return anyOk
    }
}

/**
 * Keeps [controller] fresh while the launcher is on screen, and stops asking when it is not.
 */
@Composable
fun MarketAutoRefresh(controller: MarketController, prefs: MarketPrefs) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var onScreen by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onScreen = true
                Lifecycle.Event.ON_STOP -> onScreen = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val instruments = prefs.watchlist.visible
    val keys = prefs.keys
    val source = prefs.settings.source
    val refreshMinutes = prefs.settings.refreshMinutes
    LaunchedEffect(onScreen, instruments, source, keys, refreshMinutes) {
        if (!onScreen) return@LaunchedEffect
        while (true) {
            controller.refresh(instruments, source, keys, refreshMinutes)
            delay(CHECK_EVERY_MS)
        }
    }
}
