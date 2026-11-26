package io.github.johankrs.kiri.core

/**
 * The names a person gives to apps. A name belongs to the app (`AppEntry.key`), not to one place
 * it is shown in, so the home screen, the drawer and the menus all show the same one.
 */
object AppNames {
    private const val KEY_PREFIX = "name."

    /** The longest name kept. Under an icon it already takes two lines, letters made smaller. */
    const val MAX_LENGTH = 40

    fun storageKey(appKey: String): String = KEY_PREFIX + appKey

    /** The app a saved setting belongs to, or null when the setting is not a name. */
    fun appKeyOf(storageKey: String): String? =
        storageKey.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)?.takeIf { it.isNotEmpty() }

    /**
     * What to save for the name [typed] for an app called [own]: the name without spaces around
     * it, all on one line and no longer than [MAX_LENGTH]. Null when nothing is left, or it is
     * the app's own name again: then nothing is saved and the app follows its own name, also
     * when an update changes it.
     */
    fun clean(typed: String, own: String): String? {
        val name = typed.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH).trim()
        return name.takeIf { it.isNotEmpty() && it != own }
    }
}
