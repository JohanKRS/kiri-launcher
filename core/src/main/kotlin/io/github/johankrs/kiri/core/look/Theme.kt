package io.github.johankrs.kiri.core.look

/** Whether Kiri's own screens, its settings above all, are light or dark. */
enum class Theme(val key: String) {
    /** As the device is set: dark at night on a phone that turns dark at night. */
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    /** Whether it is dark, with the device's own dark mode [systemDark] on or off. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        const val KEY = "look.theme"

        fun fromKey(key: String?): Theme? = entries.firstOrNull { it.key == key }

        /**
         * The theme when none was chosen: the device's, except on a TV, which hardly ever has a
         * dark mode of its own and is often watched with the lights off.
         */
        fun default(tv: Boolean): Theme = if (tv) DARK else SYSTEM
    }
}
