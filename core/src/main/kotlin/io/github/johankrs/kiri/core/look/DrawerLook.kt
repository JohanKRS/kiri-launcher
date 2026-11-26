package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle

/** How the sheet of the app drawer looks over the wallpaper. */
data class DrawerLook(
    /** The tint: the same glass styles the widgets use. */
    val glass: GlassStyle = GlassStyle.FUME,
    /** How much of the tint covers the wallpaper, from [OPACITY_PERCENT]. */
    val opacityPercent: Int = DEFAULT_OPACITY_PERCENT,
    /** How much the wallpaper behind is blurred, from [BLUR_DP]. Zero is no blur. */
    val blurDp: Int = DEFAULT_BLUR_DP,
) {
    fun withOpacity(percent: Int): DrawerLook = copy(opacityPercent = percent.coerceIn(OPACITY_PERCENT))

    fun withBlur(dp: Int): DrawerLook = copy(blurDp = dp.coerceIn(BLUR_DP))

    companion object {
        val OPACITY_PERCENT = 0..95
        const val OPACITY_STEP = 5
        const val DEFAULT_OPACITY_PERCENT = 55

        val BLUR_DP = 0..60
        const val BLUR_STEP = 4
        const val DEFAULT_BLUR_DP = 24

        const val KEY_GLASS = "drawer.glass"
        const val KEY_OPACITY = "drawer.opacity"
        const val KEY_BLUR = "drawer.blur"

        fun fromStored(string: (key: String) -> String?, int: (key: String) -> Int?): DrawerLook = DrawerLook(
            glass = string(KEY_GLASS)?.let { GlassStyle.fromKey(it) } ?: GlassStyle.FUME,
            opacityPercent = (int(KEY_OPACITY) ?: DEFAULT_OPACITY_PERCENT).coerceIn(OPACITY_PERCENT),
            blurDp = (int(KEY_BLUR) ?: DEFAULT_BLUR_DP).coerceIn(BLUR_DP),
        )
    }
}
