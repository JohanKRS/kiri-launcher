package io.github.johankrs.kiri.core

/** The kind of device the launcher is running on. Decides which UI shell and which defaults apply. */
enum class FormFactor {
    PHONE,
    TABLET,
    TV;

    companion object {
        private const val TABLET_MIN_WIDTH_DP = 600

        fun of(isTelevision: Boolean, smallestWidthDp: Int): FormFactor = when {
            isTelevision -> TV
            smallestWidthDp >= TABLET_MIN_WIDTH_DP -> TABLET
            else -> PHONE
        }
    }
}
