package io.github.johankrs.kiri.core

import io.github.johankrs.kiri.core.backup.BackupPart
import io.github.johankrs.kiri.core.backup.StoredValue
import io.github.johankrs.kiri.core.gesture.Gesture
import io.github.johankrs.kiri.core.gesture.GestureMap
import io.github.johankrs.kiri.core.gesture.LauncherAction
import io.github.johankrs.kiri.core.layout.HomeLayoutCodec
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.withAppsMoved
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.core.tv.TvBanner
import io.github.johankrs.kiri.core.tv.TvHomeItems
import io.github.johankrs.kiri.core.tv.TvItem

/**
 * Apps that are still installed, under another key. An app that offers several icons switches
 * between them by turning one of its entries off and another on (an `activity-alias` each), and
 * an update can rename the screen it opens on: Android then lists the same app under a new
 * `AppEntry.key`. Without this, its shortcut would be taken for an app that is gone and the new
 * entry for an app just installed, which only goes to the drawer.
 *
 * What belongs to the app follows it to the new key: its place on every home screen and in its
 * folder, on the TV's home screen, its icon, size of drawing and background, the name the
 * person gave it, whether it is hidden, the gesture that opens it and its banner on a TV.
 */
object AppMoves {
    /**
     * Where the apps that the settings mention went, old key to new. [saved] are the keys the
     * settings mention (see [appKeysIn]) and [installed] the ones Android lists now.
     *
     * A key that is not installed moves to the entry of the same package that is: the only
     * one, or the only one the settings do not mention yet, or else the one Android opens for
     * the app as a whole ([mainEntry], by package). A package with nothing listed is gone, or
     * in the middle of an update: its keys stay as they are.
     */
    fun find(
        saved: Collection<String>,
        installed: Set<String>,
        mainEntry: (packageName: String) -> String? = { null },
    ): Map<String, String> {
        val missing = saved.filter { it !in installed }
        if (missing.isEmpty()) return emptyMap()
        val mentioned = saved.toHashSet()
        val byPackage = installed.groupBy(::packageOf)
        val moves = LinkedHashMap<String, String>()
        for (old in missing) {
            val entries = byPackage[packageOf(old)] ?: continue
            val to = entries.singleOrNull()
                ?: entries.filter { it !in mentioned }.singleOrNull()
                ?: mainEntry(packageOf(old))?.takeIf { it in entries }
                ?: continue
            moves[old] = to
        }
        return moves
    }

    /**
     * Every app that [settings] mention, by `AppEntry.key`: the ones on a home screen first, in
     * their folders too, then the TV's home screen, the gestures and what was chosen for single
     * apps. When two old keys of one app have something saved, the first one met wins.
     */
    fun appKeysIn(settings: Map<String, StoredValue>): Set<String> {
        val keys = LinkedHashSet<String>()
        for ((name, value) in settings) {
            if (!name.endsWith(BackupPart.LAYOUT_SUFFIX)) continue
            val layout = HomeLayoutCodec.decode(value.text) ?: continue
            for (item in layout.items) {
                when (val content = item.content) {
                    is ItemContent.App -> keys += content.appKey
                    is ItemContent.Folder -> keys += content.appKeys
                    is ItemContent.Widget, is ItemContent.AppWidget -> Unit
                }
            }
        }
        TvHomeItems.decode(settings[TvHomeItems.KEY]?.text)?.items?.forEach { item ->
            if (item is TvItem.App) keys += item.appKey
        }
        for (gesture in Gesture.entries) {
            val action = LauncherAction.fromId(settings[GestureMap.storageKey(gesture)]?.text)
            if (action is LauncherAction.LaunchApp) keys += action.appKey
        }
        for (name in settings.keys) {
            perApp.firstNotNullOfOrNull { it.appKeyOf(name) }?.let { keys += it }
        }
        return keys.filterTo(LinkedHashSet()) { '/' in it }
    }

    /**
     * [settings] with every app of [moves] under its new key. What the new key already has
     * stays as it is: a place it already has on a home screen is not taken twice, and its own
     * icon or name wins over the old one's.
     */
    fun applied(settings: Map<String, StoredValue>, moves: Map<String, String>): Map<String, StoredValue> {
        if (moves.isEmpty()) return settings
        val result = LinkedHashMap(settings)
        for ((name, value) in settings) {
            when {
                name.endsWith(BackupPart.LAYOUT_SUFFIX) -> {
                    val layout = HomeLayoutCodec.decode(value.text) ?: continue
                    val moved = layout.withAppsMoved(moves)
                    if (moved != layout) result[name] = StoredValue.Text(HomeLayoutCodec.encode(moved))
                }

                name == TvHomeItems.KEY -> {
                    val home = TvHomeItems.decode(value.text) ?: continue
                    val moved = home.withAppsMoved(moves)
                    if (moved != home) result[name] = StoredValue.Text(TvHomeItems.encode(moved))
                }
            }
        }
        for (gesture in Gesture.entries) {
            val name = GestureMap.storageKey(gesture)
            val action = LauncherAction.fromId(settings[name]?.text) as? LauncherAction.LaunchApp ?: continue
            val to = moves[action.appKey] ?: continue
            result[name] = StoredValue.Text(LauncherAction.LaunchApp(to).id)
        }
        for ((old, new) in moves) {
            for (kind in perApp) {
                val value = result.remove(kind.storageKey(old)) ?: continue
                result.putIfAbsent(kind.storageKey(new), value)
            }
        }
        return result
    }

    /** The package of the app [appKey], as in `package/activity`. */
    private fun packageOf(appKey: String): String = appKey.substringBefore('/')

    private val StoredValue.text: String? get() = (this as? StoredValue.Text)?.value

    /** A setting kept for one app, under a key made from the app's. */
    private class PerApp(val storageKey: (appKey: String) -> String, val appKeyOf: (storageKey: String) -> String?)

    private val perApp = listOf(
        PerApp(IconChoice::storageKey, IconChoice::appKeyOf),
        PerApp(IconLook::appArtKey, IconLook::appKeyOfArt),
        PerApp(IconLook::appGroundKey, IconLook::appKeyOfGround),
        PerApp(AppNames::storageKey, AppNames::appKeyOf),
        PerApp(HiddenApps::storageKey, HiddenApps::appKeyOf),
        PerApp(
            storageKey = { appKey -> TvBanner.storageKey(TvItem.App(appKey).key) },
            appKeyOf = { name -> TvBanner.itemKeyOf(name)?.let(TvItem::fromKey)?.let { (it as? TvItem.App)?.appKey } },
        ),
    )
}
