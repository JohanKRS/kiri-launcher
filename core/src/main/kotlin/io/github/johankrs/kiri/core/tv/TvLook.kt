package io.github.johankrs.kiri.core.tv

import io.github.johankrs.kiri.core.look.GlassLevel
import io.github.johankrs.kiri.core.look.LiveColor
import io.github.johankrs.kiri.core.look.LiveLook
import io.github.johankrs.kiri.core.look.LiveMotion
import io.github.johankrs.kiri.core.look.LiveScene

/** How the banners of the TV's home screen are laid out. */
enum class TvArrangement(val key: String) {
    /** Rows of banners, as many to a row as fit, under the top of the screen. */
    GRID("grid"),

    /** One row low on the screen that slides along, so the wallpaper shows above it. */
    CAROUSEL("carousel"),
    ;

    companion object {
        fun fromKey(key: String?): TvArrangement? = entries.firstOrNull { it.key == key }
    }
}

/** Where the banners sit across the screen. */
enum class TvAlign(val key: String) {
    START("start"),
    CENTER("center"),

    /** From one side of the screen to the other, with a small margin: the gaps share what is left. */
    SPREAD("spread"),
    ;

    companion object {
        fun fromKey(key: String?): TvAlign? = entries.firstOrNull { it.key == key }
    }
}

/** When the name of a banner shows under it. */
enum class TvNameShow(val key: String) {
    ALWAYS("always"),

    /** Only under the banner that has the focus. */
    FOCUS("focus"),
    NEVER("never"),
    ;

    companion object {
        fun fromKey(key: String?): TvNameShow? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The picture behind the TV's home screen: one Kiri draws, moving ([live]) or still, the TV's
 * own wallpaper, or one the person chose.
 */
enum class TvWallpaper(val key: String, val live: LiveScene? = null) {
    LIVE_AURORA("live_aurora", LiveScene.AURORA),
    LIVE_BRUMA("live_bruma", LiveScene.BRUMA),
    LIVE_FIOS("live_fios", LiveScene.FIOS),
    LIVE_PASTILHAS("live_pastilhas", LiveScene.PASTILHAS),
    LIVE_SERRA("live_serra", LiveScene.SERRA),
    LIVE_FLORES("live_flores", LiveScene.FLORES),
    AURORA("aurora_still"),
    OCEANO("oceano"),
    GRAFITE("grafite"),
    POR_DO_SOL("por_do_sol"),

    /** What the TV has as its wallpaper. Many TVs have none: then the screen is black. */
    SYSTEM("system"),

    /** A picture the person chose in Kiri. Until there is one, the screen is dark. */
    OWN("own"),
    ;

    companion object {
        /**
         * What the still aurora was kept as when it was the default, before there were live
         * ones. Kept by nearly everyone without choosing it, it now means the default again.
         */
        private const val OLD_DEFAULT = "aurora"

        fun fromKey(key: String?): TvWallpaper? =
            if (key == OLD_DEFAULT) LIVE_AURORA else entries.firstOrNull { it.key == key }
    }
}

/** How the TV's home screen looks as a whole. */
data class TvLook(
    val arrangement: TvArrangement = TvArrangement.GRID,
    val align: TvAlign = TvAlign.START,
    /** How large the banners are, a level of [SIZES]. */
    val bannerSize: Int = DEFAULT_SIZE,
    val names: TvNameShow = TvNameShow.FOCUS,
    val wallpaper: TvWallpaper = TvWallpaper.LIVE_AURORA,
    /** The color of a [live][TvWallpaper.live] wallpaper. */
    val liveColor: LiveColor = LiveColor.LILAC,
    /** How fast a [live][TvWallpaper.live] wallpaper moves. */
    val liveMotion: LiveMotion = LiveMotion.NORMAL,
    /** How much room there is between the banners, a level of [GAPS]. */
    val gap: Int = DEFAULT_GAP,
    /** How dark the soft shadow around the banners and the widgets is. */
    val shadow: GlassLevel = GlassLevel.MEDIUM,
) {
    /** How wide a banner is, in dp, on a TV's usual 960 dp wide screen. They are 16:9. */
    val bannerWidthDp: Float get() = BANNER_WIDTHS[bannerSize.coerceIn(SIZES)]

    /** How much the room between banners is of the usual: 1 at [DEFAULT_GAP]. */
    val gapScale: Float get() = GAP_SCALES[gap.coerceIn(GAPS)]

    /** How dark the shadow is, as a share of the usual one: 0 is none. */
    val shadowAmount: Float get() = when (shadow) {
        GlassLevel.OFF -> 0f
        GlassLevel.LIGHT -> 0.5f
        GlassLevel.MEDIUM -> 1f
        GlassLevel.STRONG -> 1.7f
    }

    /** The moving wallpaper, when the one chosen moves. */
    val live: LiveLook? get() = wallpaper.live?.let { LiveLook(it, liveColor, liveMotion) }

    fun withBannerSize(level: Int): TvLook = copy(bannerSize = level.coerceIn(SIZES))

    fun withGap(level: Int): TvLook = copy(gap = level.coerceIn(GAPS))

    companion object {
        val SIZES = 1..5
        const val DEFAULT_SIZE = 2
        private val BANNER_WIDTHS = floatArrayOf(0f, 128f, 148f, 170f, 196f, 226f)

        /** The room between banners goes from close together to far apart; the middle is the usual. */
        val GAPS = 1..7
        const val DEFAULT_GAP = 4
        private val GAP_SCALES = floatArrayOf(0f, 0.35f, 0.6f, 0.8f, 1f, 1.25f, 1.55f, 1.9f)

        const val KEY_ARRANGEMENT = "tv.home.arrangement"
        const val KEY_ALIGN = "tv.home.align"
        const val KEY_SIZE = "tv.home.size"
        const val KEY_NAMES = "tv.home.names"
        const val KEY_WALLPAPER = "tv.home.wallpaper"
        const val KEY_GAP = "tv.home.gap"
        const val KEY_SHADOW = "tv.home.shadow"
        const val KEY_LIVE_COLOR = "tv.home.live.color"
        const val KEY_LIVE_MOTION = "tv.home.live.motion"

        fun fromStored(string: (String) -> String?, int: (String) -> Int?): TvLook {
            val default = TvLook()
            return TvLook(
                arrangement = TvArrangement.fromKey(string(KEY_ARRANGEMENT)) ?: default.arrangement,
                align = TvAlign.fromKey(string(KEY_ALIGN)) ?: default.align,
                bannerSize = (int(KEY_SIZE) ?: default.bannerSize).coerceIn(SIZES),
                names = TvNameShow.fromKey(string(KEY_NAMES)) ?: default.names,
                wallpaper = TvWallpaper.fromKey(string(KEY_WALLPAPER)) ?: default.wallpaper,
                gap = (int(KEY_GAP) ?: default.gap).coerceIn(GAPS),
                shadow = GlassLevel.fromKey(string(KEY_SHADOW)) ?: default.shadow,
                liveColor = LiveColor.fromKey(string(KEY_LIVE_COLOR)) ?: default.liveColor,
                liveMotion = LiveMotion.fromKey(string(KEY_LIVE_MOTION)) ?: default.liveMotion,
            )
        }
    }
}
