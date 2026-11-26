package io.github.johankrs.kiri.core.look

/**
 * The icon a person picked for an app: a drawable of an installed icon pack.
 *
 * It belongs to the app, not to one shortcut, so it is saved under `AppEntry.key` and every
 * place that shows the app (home screen, drawer, menus) shows the same icon.
 */
data class IconChoice(val pack: String, val drawable: String) {
    /** The text it is saved as. */
    val stored: String get() = "$pack$SEPARATOR$drawable"

    companion object {
        private const val SEPARATOR = "\t"
        private const val KEY_PREFIX = "icon."

        fun storageKey(appKey: String): String = KEY_PREFIX + appKey

        /** The app a saved setting belongs to, or null when the setting is not an icon choice. */
        fun appKeyOf(storageKey: String): String? =
            storageKey.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)?.takeIf { it.isNotEmpty() }

        fun fromStored(text: String?): IconChoice? {
            val parts = text?.split(SEPARATOR) ?: return null
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return IconChoice(pack = parts[0], drawable = parts[1])
        }
    }
}
