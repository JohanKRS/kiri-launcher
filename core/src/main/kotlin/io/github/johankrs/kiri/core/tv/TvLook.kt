package io.github.johankrs.kiri.core.tv

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

/** The picture behind the TV's home screen: one Kiri draws, the TV's own wallpaper, or one the person chose. */
enum class TvWallpaper(val key: String) {
    AURORA("aurora"),
    OCEANO("oceano"),
    GRAFITE("grafite"),
    POR_DO_SOL("por_do_sol"),

    /** What the TV has as its wallpaper. Many TVs have none: then the screen is black. */
    SYSTEM("system"),

    /** A picture the person chose in Kiri. Until there is one, the screen is dark. */
    OWN("own"),
    ;

    companion object {
        fun fromKey(key: String?): TvWallpaper? = entries.firstOrNull { it.key == key }
    }
}

/** How the TV's home screen looks as a whole. */
data class TvLook(
    val arrangement: TvArrangement = TvArrangement.GRID,
    val align: TvAlign = TvAlign.START,
    /** How large the banners are, a level of [SIZES]. */
    val bannerSize: Int = DEFAULT_SIZE,
    val names: TvNameShow = TvNameShow.FOCUS,
    val wallpaper: TvWallpaper = TvWallpaper.AURORA,
) {
    /** How wide a banner is, in dp, on a TV's usual 960 dp wide screen. They are 16:9. */
    val bannerWidthDp: Float get() = BANNER_WIDTHS[bannerSize.coerceIn(SIZES)]

    fun withBannerSize(level: Int): TvLook = copy(bannerSize = level.coerceIn(SIZES))

    companion object {
        val SIZES = 1..5
        const val DEFAULT_SIZE = 2
        private val BANNER_WIDTHS = floatArrayOf(0f, 128f, 148f, 170f, 196f, 226f)

        const val KEY_ARRANGEMENT = "tv.home.arrangement"
        const val KEY_ALIGN = "tv.home.align"
        const val KEY_SIZE = "tv.home.size"
        const val KEY_NAMES = "tv.home.names"
        const val KEY_WALLPAPER = "tv.home.wallpaper"

        fun fromStored(string: (String) -> String?, int: (String) -> Int?): TvLook {
            val default = TvLook()
            return TvLook(
                arrangement = TvArrangement.fromKey(string(KEY_ARRANGEMENT)) ?: default.arrangement,
                align = TvAlign.fromKey(string(KEY_ALIGN)) ?: default.align,
                bannerSize = (int(KEY_SIZE) ?: default.bannerSize).coerceIn(SIZES),
                names = TvNameShow.fromKey(string(KEY_NAMES)) ?: default.names,
                wallpaper = TvWallpaper.fromKey(string(KEY_WALLPAPER)) ?: default.wallpaper,
            )
        }
    }
}
