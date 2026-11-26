package io.github.johankrs.kiri.core

/**
 * The looks a glass panel can have over the wallpaper. Colours live in the app's theme; here is
 * only what gets stored and adjusted.
 */
enum class GlassStyle(val key: String, val defaultOpacityPercent: Int) {
    /** Dark glass with light text. */
    FUME("fume", 58),

    /** Nearly clear glass with a bright edge. */
    CRISTAL("cristal", 36),

    /** Light frosted glass with dark text. */
    NEVOA("nevoa", 72);

    companion object {
        val DEFAULT = FUME
        val OPACITY_PERCENT = 20..92

        fun fromKey(key: String?): GlassStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
