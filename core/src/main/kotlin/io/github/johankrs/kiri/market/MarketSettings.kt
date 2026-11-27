package io.github.johankrs.kiri.market

import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.WidgetSpec

/**
 * Where the prices come from, by the name it is saved under: [OFFICIAL], or the key of a
 * [CompleteSource] from another app. [DEFAULT], no choice yet, stands for the official services:
 * a source from another app is used only once the person picks it.
 */
@JvmInline
value class MarketSource(val key: String?) {
    companion object {
        /** The official free services, one per market, most of them with the person's own key. */
        val OFFICIAL = MarketSource("official")
        val DEFAULT = MarketSource(null)

        fun fromKey(key: String?): MarketSource = MarketSource(key?.takeIf { it.isNotBlank() })
    }
}

/** How the market widget looks, where its prices come from and how often it asks for new ones. */
data class MarketSettings(
    val glass: GlassStyle = GlassStyle.DEFAULT,
    val opacityPercent: Int = GlassStyle.DEFAULT.defaultOpacityPercent,
    val cornerDp: Int = 28,
    val refreshMinutes: Int = 30,
    val showChart: Boolean = true,
    val source: MarketSource = MarketSource.DEFAULT,
) {
    fun coerced(): MarketSettings = copy(
        opacityPercent = opacityPercent.coerceIn(GlassStyle.OPACITY_PERCENT),
        cornerDp = cornerDp.coerceIn(CORNER_DP),
        refreshMinutes = REFRESH_MINUTES.minBy { kotlin.math.abs(it - refreshMinutes) },
    )

    /** Changes the glass and starts from the opacity that suits it. */
    fun withGlass(style: GlassStyle): MarketSettings =
        copy(glass = style, opacityPercent = style.defaultOpacityPercent)

    /** The next longer (positive [by]) or shorter refresh interval, or null at the end. */
    fun refreshStepped(by: Int): MarketSettings? {
        val index = REFRESH_MINUTES.indexOf(coerced().refreshMinutes) + by
        return REFRESH_MINUTES.getOrNull(index)?.let { copy(refreshMinutes = it) }
    }

    companion object {
        val CORNER_DP = 8..36

        /** The free Brazilian source updates every 30 minutes, and no source should be asked too often. */
        val REFRESH_MINUTES = listOf(30, 60, 120, 240)
    }
}

/** True when prices fetched at [lastFetchMillis] are older than the chosen interval. */
fun isStale(lastFetchMillis: Long?, nowMillis: Long, refreshMinutes: Int): Boolean =
    lastFetchMillis == null || nowMillis - lastFetchMillis >= refreshMinutes * 60_000L

/**
 * The widget takes any size between the two ends: what it shows is decided by the space it is
 * really given (see [MarketPlanner]), not by a list of fixed sizes.
 */
object MarketWidgetSpec {
    val SPEC = WidgetSpec(
        id = "kiri.market",
        minSize = CellSpan(2, 1),
        maxSize = CellSpan(8, 8),
        defaultSize = CellSpan(4, 3),
    )
}
