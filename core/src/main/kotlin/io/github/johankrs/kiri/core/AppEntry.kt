package io.github.johankrs.kiri.core

import java.text.Collator
import java.util.Locale

/**
 * One launchable activity of an installed app.
 *
 * [label] is the name shown everywhere: the one the person gave the app, when they renamed it,
 * or else [ownLabel], the name the app came with.
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val ownLabel: String = label,
) {
    /** Stable identity of the activity, used as cache and list key. */
    val key: String = "$packageName/$activityName"

    /** Whether the person gave this app a name of their own. */
    val renamed: Boolean get() = label != ownLabel
}

/**
 * Alphabetical order as a person expects it: case and accents do not matter
 * ("Ágora" sorts with "agenda", not after "Zoom").
 */
fun List<AppEntry>.sortedByLabel(locale: Locale = Locale.getDefault()): List<AppEntry> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return sortedWith(compareBy<AppEntry, String>(collator) { it.label }.thenBy { it.key })
}

/**
 * These apps with the names the person gave them, by [AppEntry.key], in the order of those
 * names: a renamed app is found where its new name falls in the drawer.
 */
fun List<AppEntry>.named(names: Map<String, String>, locale: Locale = Locale.getDefault()): List<AppEntry> {
    if (names.isEmpty()) return this
    return map { app -> names[app.key]?.let { app.copy(label = it) } ?: app }.sortedByLabel(locale)
}
