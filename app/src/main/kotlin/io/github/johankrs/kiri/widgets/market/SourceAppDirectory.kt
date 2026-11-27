package io.github.johankrs.kiri.widgets.market

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Bundle
import android.os.SystemClock
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.market.ChartRange
import io.github.johankrs.kiri.market.CompleteSource
import io.github.johankrs.kiri.market.FailReason
import io.github.johankrs.kiri.market.Instrument
import io.github.johankrs.kiri.market.QuoteResult
import io.github.johankrs.kiri.market.SeriesResult
import io.github.johankrs.kiri.market.SourceApps
import java.util.Locale

/**
 * The complete sources that other apps offer (see [SourceApps]): every app with a content
 * provider for [SourceApps.ACTION]. Kiri holds none of their code and gives them nothing but
 * what it asks the price of; each one is used only once the person picks it.
 */
class SourceAppDirectory(context: Context) {
    private val app = context.applicationContext

    @Volatile
    private var found: Pair<Long, List<CompleteSource>>? = null

    /**
     * The source apps installed now. The list is looked up again after a few seconds, so one
     * installed or removed while Kiri runs is offered or dropped without a restart.
     */
    fun list(): List<CompleteSource> {
        val now = SystemClock.elapsedRealtime()
        found?.let { (at, sources) -> if (now - at < LOOKUP_MILLIS) return sources }
        return lookUp().also { found = now to it }
    }

    private fun lookUp(): List<CompleteSource> {
        val pm = app.packageManager
        val providers = runCatching {
            pm.queryIntentContentProviders(Intent(SourceApps.ACTION), PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        return providers
            .mapNotNull { resolved ->
                val provider = resolved.providerInfo ?: return@mapNotNull null
                if (!provider.exported || provider.packageName == app.packageName) return@mapNotNull null
                val key = provider.metaData?.getString(SourceApps.META_KEY)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val authority = provider.authority?.split(';')?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                SourceApp(
                    context = app,
                    key = key,
                    authority = authority,
                    label = resolved.loadLabel(pm).toString().ifBlank { key },
                    appLabel = provider.applicationInfo.loadLabel(pm).toString(),
                    provider = provider,
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val LOOKUP_MILLIS = 5_000L
    }
}

/** One source app, asked through its content provider. */
private class SourceApp(
    private val context: Context,
    override val key: String,
    private val authority: String,
    val label: String,
    private val appLabel: String,
    private val provider: ProviderInfo,
) : CompleteSource {
    override fun name(locale: Locale): String = label

    /** The app's own words on what it covers, then where it comes from, in Kiri's words. */
    override fun note(locale: Locale): String {
        val own = provider.metaData?.getInt(SourceApps.META_NOTE)?.takeIf { it != 0 }?.let { id ->
            runCatching {
                context.packageManager.getResourcesForApplication(provider.applicationInfo).getString(id)
            }.getOrNull()
        }
        val origin = context.getString(R.string.market_source_app_note, appLabel)
        return if (own.isNullOrBlank()) origin else "$own $origin"
    }

    override fun quote(instrument: Instrument): QuoteResult {
        val answer = ask(SourceApps.METHOD_QUOTE, instrument)
            ?: return QuoteResult.Failed(instrument, FailReason.NETWORK)
        return SourceApps.decodeQuote(instrument, answer.getString(SourceApps.EXTRA_RESULT))
    }

    override fun series(instrument: Instrument, range: ChartRange): SeriesResult {
        val answer = ask(SourceApps.METHOD_SERIES, instrument) { putString(SourceApps.EXTRA_RANGE, range.key) }
            ?: return SeriesResult.Failed(FailReason.NETWORK)
        return SourceApps.decodeSeries(answer.getString(SourceApps.EXTRA_RESULT))
    }

    /**
     * Blocking, as every source is: the app is started if it is not running, and answers once it
     * has the price. Null when it cannot be reached (removed, or stopped in the middle).
     */
    private fun ask(method: String, instrument: Instrument, more: Bundle.() -> Unit = {}): Bundle? {
        val extras = Bundle().apply {
            putInt(SourceApps.EXTRA_VERSION, SourceApps.VERSION)
            putString(SourceApps.EXTRA_INSTRUMENT, instrument.id)
            more()
        }
        return try {
            context.contentResolver.call(authority, method, null, extras)
        } catch (e: RuntimeException) {
            null
        }
    }
}
