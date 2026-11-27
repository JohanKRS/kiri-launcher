package io.github.johankrs.kiri.core.tv

/**
 * Everything saved about the TV's home screen. [home] is null until it was saved the first
 * time; [banners] are by `TvItem.key`, [inputNames] by the id of the input.
 */
data class TvSettings(
    val look: TvLook = TvLook(),
    val home: TvHomeItems? = null,
    val banners: Map<String, TvBanner> = emptyMap(),
    val inputNames: Map<String, String> = emptyMap(),
    /** The widgets over the home screen, left to right. */
    val widgets: TvWidgets = TvWidgets.DEFAULT,
    /**
     * Whether the person wants Kiri to come in place of the TV's own home screen when the Home
     * button opens that one (through Kiri's accessibility service; see `HomeRoute.TAKE_OVER`).
     */
    val homeButton: Boolean = false,
    /** Whether Kiri has already offered, once, to be what the Home button opens. */
    val homeButtonOffered: Boolean = false,
) {
    fun bannerOf(item: TvItem): TvBanner? = banners[item.key]

    companion object {
        const val KEY_HOME_BUTTON = "tv.button.home"
        const val KEY_HOME_BUTTON_OFFERED = "tv.button.home.offered"
    }
}
