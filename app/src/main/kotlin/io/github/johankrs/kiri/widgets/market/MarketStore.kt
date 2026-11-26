package io.github.johankrs.kiri.widgets.market

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.backup.BackupPart
import io.github.johankrs.kiri.core.backup.BackupPlan
import io.github.johankrs.kiri.core.backup.StoredValue
import io.github.johankrs.kiri.market.ApiKeys
import io.github.johankrs.kiri.market.MarketSettings
import io.github.johankrs.kiri.market.MarketSource
import io.github.johankrs.kiri.market.Watchlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.marketDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_market")

/** Everything the market widget remembers. */
@Immutable
data class MarketPrefs(
    val settings: MarketSettings = MarketSettings(),
    val watchlist: Watchlist = Watchlist.DEFAULT,
    val keys: ApiKeys = ApiKeys(),
)

/**
 * The market widget's saved state, in its own file so the widget stays self-contained.
 * The person's keys are kept only here, on the device.
 */
@Stable
class MarketStore(context: Context) {
    private val store = context.applicationContext.marketDataStore

    val prefs: Flow<MarketPrefs> = store.data
        .catch { emit(emptyPreferences()) }
        .map { saved ->
            val defaults = MarketSettings()
            MarketPrefs(
                settings = MarketSettings(
                    glass = GlassStyle.fromKey(saved[GLASS]),
                    opacityPercent = saved[OPACITY] ?: GlassStyle.fromKey(saved[GLASS]).defaultOpacityPercent,
                    cornerDp = saved[CORNER] ?: defaults.cornerDp,
                    refreshMinutes = saved[REFRESH] ?: defaults.refreshMinutes,
                    showChart = saved[CHART] ?: defaults.showChart,
                    source = MarketSource.fromKey(saved[SOURCE]),
                ).coerced(),
                watchlist = Watchlist.decode(saved[WATCHLIST]),
                keys = ApiKeys(
                    brapi = saved[KEY_BRAPI].orEmpty(),
                    finnhub = saved[KEY_FINNHUB].orEmpty(),
                    awesome = saved[KEY_AWESOME].orEmpty(),
                    alpacaId = saved[KEY_ALPACA_ID].orEmpty(),
                    alpacaSecret = saved[KEY_ALPACA_SECRET].orEmpty(),
                ),
            )
        }
        .distinctUntilChanged()

    suspend fun save(prefs: MarketPrefs) {
        val settings = prefs.settings.coerced()
        store.edit { saved ->
            saved[GLASS] = settings.glass.key
            saved[OPACITY] = settings.opacityPercent
            saved[CORNER] = settings.cornerDp
            saved[REFRESH] = settings.refreshMinutes
            saved[CHART] = settings.showChart
            // No choice yet stays no choice, so it keeps following what the build offers first.
            val source = settings.source.key
            if (source == null) saved.remove(SOURCE) else saved[SOURCE] = source
            saved[WATCHLIST] = prefs.watchlist.encode()
            saved[KEY_BRAPI] = prefs.keys.brapi.trim()
            saved[KEY_FINNHUB] = prefs.keys.finnhub.trim()
            saved[KEY_AWESOME] = prefs.keys.awesome.trim()
            saved[KEY_ALPACA_ID] = prefs.keys.alpacaId.trim()
            saved[KEY_ALPACA_SECRET] = prefs.keys.alpacaSecret.trim()
        }
    }

    /**
     * Everything saved, for a backup: the settings, and apart from them the person's keys.
     * Null when nothing could be read.
     */
    suspend fun dumpAll(): Pair<Map<String, StoredValue>, Map<String, StoredValue>>? = runCatching {
        val plain = LinkedHashMap<String, StoredValue>()
        val secrets = LinkedHashMap<String, StoredValue>()
        for ((key, value) in store.data.first().asMap()) {
            val stored = StoredValue.of(value) ?: continue
            // An empty key is no key: it is not worth carrying, locked or not.
            if (BackupPart.isMarketSecret(key.name)) {
                if ((stored as? StoredValue.Text)?.value?.isNotBlank() != false) secrets[key.name] = stored
            } else {
                plain[key.name] = stored
            }
        }
        plain to secrets
    }.getOrNull()

    /**
     * Puts back what a backup saved: its settings, unless [backup] is null, when those of now
     * stay; and its keys, unless [secrets] is null, when the keys of now stay.
     */
    suspend fun restore(backup: Map<String, StoredValue>?, secrets: Map<String, StoredValue>?) {
        store.edit { saved ->
            val now = saved.asMap().entries.associate { (key, value) -> key.name to value }
            val current = now.mapNotNull { (name, value) -> StoredValue.of(value)?.let { name to it } }.toMap()
            val next = BackupPlan.restoreMarket(
                current = current,
                backup = backup ?: current.filterKeys { !BackupPart.isMarketSecret(it) },
                secrets = secrets,
            )
            for (key in saved.asMap().keys.toList()) if (key.name !in next) saved.remove(key)
            for ((name, value) in next) {
                if (now[name] == value.raw) continue
                when (value) {
                    is StoredValue.Text -> saved[stringPreferencesKey(name)] = value.value
                    is StoredValue.Whole -> saved[intPreferencesKey(name)] = value.value
                    is StoredValue.Flag -> saved[booleanPreferencesKey(name)] = value.value
                    is StoredValue.Big -> saved[longPreferencesKey(name)] = value.value
                    is StoredValue.Fraction -> saved[floatPreferencesKey(name)] = value.value
                    is StoredValue.Precise -> saved[doublePreferencesKey(name)] = value.value
                    is StoredValue.Texts -> saved[stringSetPreferencesKey(name)] = value.value
                }
            }
        }
    }

    private companion object {
        val GLASS = stringPreferencesKey("glass")
        val OPACITY = intPreferencesKey("opacityPercent")
        val CORNER = intPreferencesKey("cornerDp")
        val REFRESH = intPreferencesKey("refreshMinutes")
        val CHART = booleanPreferencesKey("showChart")
        val WATCHLIST = stringPreferencesKey("watchlist")
        val KEY_BRAPI = stringPreferencesKey("key.brapi")
        val KEY_FINNHUB = stringPreferencesKey("key.finnhub")
        val KEY_AWESOME = stringPreferencesKey("key.awesome")
        val KEY_ALPACA_ID = stringPreferencesKey("key.alpaca.id")
        val KEY_ALPACA_SECRET = stringPreferencesKey("key.alpaca.secret")
        val SOURCE = stringPreferencesKey("source")
    }
}
