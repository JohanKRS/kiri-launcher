package io.github.johankrs.kiri.core.backup

import io.github.johankrs.kiri.core.AppNames
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.HiddenApps
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.HomeLayoutCodec
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.market.Watchlist

/** What one home screen holds, counted. */
data class HomeCount(val pages: Int, val apps: Int, val folders: Int, val widgets: Int)

/**
 * What a set of launcher settings holds, counted for the person to see before saving or
 * restoring it.
 */
data class BackupSummary(
    /** The home screen of the kind of device asked about, or of any; null with none saved. */
    val home: HomeCount?,
    /** Apps with an icon, a size of drawing or a background of their own. */
    val icons: Int,
    /** Apps with a name of their own. */
    val names: Int,
    val hidden: Int,
    /** The things the market widget follows, or null when its list was never changed. */
    val watching: Int?,
) {
    companion object {
        fun of(
            settings: Map<String, StoredValue>,
            market: Map<String, StoredValue>,
            formFactor: FormFactor,
        ): BackupSummary {
            val home = homeLayoutOf(settings, formFactor)?.let { layout ->
                val apps = HashSet<String>()
                var folders = 0
                var widgets = 0
                for (item in layout.items) {
                    when (val content = item.content) {
                        is ItemContent.App -> apps += content.appKey
                        is ItemContent.Folder -> {
                            folders++
                            apps += content.appKeys
                        }
                        is ItemContent.Widget, is ItemContent.AppWidget -> widgets++
                    }
                }
                HomeCount(pages = layout.pageCount, apps = apps.size, folders = folders, widgets = widgets)
            }
            val iconApps = HashSet<String>()
            var names = 0
            var hidden = 0
            for ((key, value) in settings) {
                (IconChoice.appKeyOf(key) ?: IconLook.appKeyOfArt(key) ?: IconLook.appKeyOfGround(key))?.let { iconApps += it }
                if (AppNames.appKeyOf(key) != null) names++
                if (HiddenApps.appKeyOf(key) != null && value == StoredValue.Flag(true)) hidden++
            }
            val watching = (market[MARKET_WATCHLIST] as? StoredValue.Text)?.value?.let { Watchlist.decode(it).entries.size }
            return BackupSummary(home = home, icons = iconApps.size, names = names, hidden = hidden, watching = watching)
        }

        /**
         * The home screen of [formFactor] standing up, else lying down, else any saved; null
         * when there is none (a TV, for now).
         */
        fun homeLayoutOf(settings: Map<String, StoredValue>, formFactor: FormFactor): HomeLayout? {
            val device = formFactor.name.lowercase()
            val keys = listOf("home.$device.portrait.layout", "home.$device.landscape.layout") +
                settings.keys.filter { it.startsWith("home.") && it.endsWith(BackupPart.LAYOUT_SUFFIX) }.sorted()
            for (key in keys) {
                val text = (settings[key] as? StoredValue.Text)?.value ?: continue
                HomeLayoutCodec.decode(text)?.let { return it }
            }
            return null
        }

        /**
         * Every app placed on a home screen of [formFactor] (or of any device, when that one has
         * none), folders included, in the order first met.
         */
        fun appsOnHome(settings: Map<String, StoredValue>, formFactor: FormFactor): List<String> {
            val device = formFactor.name.lowercase()
            val layouts = settings.filterKeys { it.startsWith("home.") && it.endsWith(BackupPart.LAYOUT_SUFFIX) }
            val own = layouts.filterKeys { BackupPart.formFactorOf(it)?.name?.lowercase() == device }
            val apps = LinkedHashSet<String>()
            for ((_, value) in (own.ifEmpty { layouts }).toSortedMap()) {
                val layout = HomeLayoutCodec.decode((value as? StoredValue.Text)?.value) ?: continue
                for (item in layout.items) {
                    when (val content = item.content) {
                        is ItemContent.App -> apps += content.appKey
                        is ItemContent.Folder -> apps += content.appKeys
                        is ItemContent.Widget, is ItemContent.AppWidget -> Unit
                    }
                }
            }
            return apps.toList()
        }

        /** The packages of the icon packs whose icons were chosen for some app. */
        fun iconPacks(settings: Map<String, StoredValue>): Set<String> = settings.entries.mapNotNullTo(LinkedHashSet()) { (key, value) ->
            if (IconChoice.appKeyOf(key) == null) null else IconChoice.fromStored((value as? StoredValue.Text)?.value)?.pack
        }

        /** The copies of widgets of other apps placed on any saved home screen, with who draws them. */
        fun appWidgets(settings: Map<String, StoredValue>): List<ItemContent.AppWidget> {
            val found = LinkedHashMap<Int, ItemContent.AppWidget>()
            for ((key, value) in settings) {
                if (!key.endsWith(BackupPart.LAYOUT_SUFFIX)) continue
                val layout = HomeLayoutCodec.decode((value as? StoredValue.Text)?.value) ?: continue
                for (item in layout.items) (item.content as? ItemContent.AppWidget)?.let { found[it.appWidgetId] = it }
            }
            return found.values.toList()
        }

        /** Where the market widget keeps its list. */
        const val MARKET_WATCHLIST = "watchlist"
    }
}
