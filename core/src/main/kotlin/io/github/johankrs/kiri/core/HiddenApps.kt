package io.github.johankrs.kiri.core

/**
 * The apps a person chose not to see. A hidden app is still installed and still opens from a
 * gesture, but it is in no drawer, on no home screen and in no folder. Only the list of the
 * settings shows it, to bring it back.
 *
 * Like a name ([AppNames]), being hidden belongs to the app (`AppEntry.key`), not to one place.
 */
object HiddenApps {
    private const val KEY_PREFIX = "hidden."

    fun storageKey(appKey: String): String = KEY_PREFIX + appKey

    /** The app a saved setting belongs to, or null when the setting is not about a hidden app. */
    fun appKeyOf(storageKey: String): String? =
        storageKey.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)?.takeIf { it.isNotEmpty() }
}

/** These apps without the [hidden] ones (by [AppEntry.key]), in the same order. */
fun List<AppEntry>.shown(hidden: Set<String>): List<AppEntry> =
    if (hidden.isEmpty()) this else filterNot { it.key in hidden }

/**
 * The apps of the list that hides and shows them: every app, in the order of its name, and
 * apart, the ones hidden now. A hidden app that is no longer installed is not there, so the
 * count never takes in apps that are gone.
 */
data class HiddenAppsList(val hidden: List<AppEntry>, val all: List<AppEntry>) {
    companion object {
        fun of(apps: List<AppEntry>, hidden: Set<String>): HiddenAppsList =
            HiddenAppsList(hidden = apps.filter { it.key in hidden }, all = apps)
    }
}
