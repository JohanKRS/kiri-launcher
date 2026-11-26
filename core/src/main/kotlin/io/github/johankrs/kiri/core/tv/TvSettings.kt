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
) {
    fun bannerOf(item: TvItem): TvBanner? = banners[item.key]
}
