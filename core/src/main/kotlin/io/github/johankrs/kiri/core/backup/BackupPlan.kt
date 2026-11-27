package io.github.johankrs.kiri.core.backup

import io.github.johankrs.kiri.core.AppNames
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.HiddenApps
import io.github.johankrs.kiri.core.Surface
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.HomeLayoutCodec
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.core.look.LiveLook
import io.github.johankrs.kiri.core.look.WallpaperSetup
import io.github.johankrs.kiri.core.look.WidgetLook
import io.github.johankrs.kiri.core.tv.TvBanner
import io.github.johankrs.kiri.core.tv.TvInputNames

/**
 * The parts a backup is restored by. Every saved setting of the launcher belongs to exactly
 * one; a setting added later that none of them claims belongs to [LOOK], so it is in every
 * backup without anyone having to remember it.
 */
enum class BackupPart(val key: String) {
    /**
     * The home screens and the drawer: grids, pages, apps, folders, widgets and their looks; on
     * a TV, what is on its home screen and how it is laid out.
     */
    HOME("home"),

    /** The icon and the name the person chose for single apps, and the banners and names on a TV. */
    ICONS("icons"),

    HIDDEN("hidden"),

    /** Everything else: glass, icons as a whole, letters, motion, gestures, folders' look. */
    LOOK("look"),

    /** The picture chosen in Kiri and how it is framed, and Kiri's own live wallpaper. */
    WALLPAPER("wallpaper"),

    /** The market widget: its list, its source and how it looks. Its keys go apart. */
    MARKET("market");

    companion object {
        fun fromKey(key: String?): BackupPart? = entries.firstOrNull { it.key == key }

        /** The part the launcher setting [key] belongs to. [MARKET] has a file of its own. */
        fun of(key: String): BackupPart = when {
            key.endsWith(LAYOUT_SUFFIX) || formFactorOf(key) != null -> HOME
            WidgetLook.appWidgetIdOf(key) != null -> HOME
            key.startsWith(TV_HOME_PREFIX) -> HOME
            TvBanner.itemKeyOf(key) != null || TvInputNames.inputIdOf(key) != null -> ICONS
            IconChoice.appKeyOf(key) != null || IconLook.appKeyOfArt(key) != null ||
                IconLook.appKeyOfGround(key) != null || AppNames.appKeyOf(key) != null -> ICONS
            HiddenApps.appKeyOf(key) != null -> HIDDEN
            key == WallpaperSetup.KEY || key == LiveLook.KEY -> WALLPAPER
            else -> LOOK
        }

        /**
         * The kind of device a setting of a grid belongs to, as in `home.phone.portrait.rows`;
         * null for a setting that is not about one grid.
         */
        fun formFactorOf(key: String): FormFactor? {
            val parts = key.split('.', limit = 4)
            if (parts.size < 4) return null
            if (Surface.entries.none { it.name.lowercase() == parts[0] }) return null
            if (parts[2] != "portrait" && parts[2] != "landscape") return null
            return FormFactor.entries.firstOrNull { it.name.lowercase() == parts[1] }
        }

        /** How the settings of the TV's home screen begin. */
        private const val TV_HOME_PREFIX = "tv.home."

        /** How the name of every setting that holds a home layout ends. */
        const val LAYOUT_SUFFIX = ".layout"

        /** Whether the market widget's setting [key] is one of the person's keys to a source. */
        fun isMarketSecret(key: String): Boolean = key.startsWith("key.")
    }
}

/** How restoring a backup changes the saved settings. */
object BackupPlan {
    /**
     * The launcher settings after the [parts] of [backup] are put over [current].
     *
     * A part restored is restored whole: what it holds now and the backup does not is gone
     * afterwards, so restoring takes the launcher back to exactly what was saved. Two
     * exceptions keep what the backup knows nothing about: the grids of a kind of device the
     * backup has none of (a phone's backup leaves the tablet's home screen alone), and the
     * looks of widgets, which go when their widget goes.
     *
     * Apps hidden afterwards are taken off every home screen and folder, as hiding does.
     */
    fun restore(
        current: Map<String, StoredValue>,
        backup: Map<String, StoredValue>,
        parts: Set<BackupPart>,
    ): Map<String, StoredValue> {
        if (parts.isEmpty()) return current
        val devicesInBackup = backup.keys.mapNotNullTo(HashSet()) { BackupPart.formFactorOf(it) }
        val result = LinkedHashMap<String, StoredValue>()
        for ((key, value) in current) {
            val part = BackupPart.of(key)
            val replaced = part in parts && when (part) {
                BackupPart.HOME -> BackupPart.formFactorOf(key)?.let { it in devicesInBackup } ?: false
                else -> true
            }
            if (!replaced) result[key] = value
        }
        for ((key, value) in backup) {
            val part = BackupPart.of(key)
            if (part in parts && part != BackupPart.MARKET) result[key] = value
        }
        if (BackupPart.HIDDEN in parts || BackupPart.HOME in parts) takeOutHidden(result)
        return result
    }

    /**
     * The market widget's settings after its part of a backup is restored: everything as in
     * [backup], and the keys from [secrets]; with no [secrets], the keys of now stay.
     */
    fun restoreMarket(
        current: Map<String, StoredValue>,
        backup: Map<String, StoredValue>,
        secrets: Map<String, StoredValue>?,
    ): Map<String, StoredValue> {
        val result = LinkedHashMap<String, StoredValue>()
        for ((key, value) in current) {
            if (BackupPart.isMarketSecret(key) && secrets == null) result[key] = value
        }
        for ((key, value) in backup) if (!BackupPart.isMarketSecret(key)) result[key] = value
        secrets?.forEach { (key, value) -> if (BackupPart.isMarketSecret(key)) result[key] = value }
        return result
    }

    /** Takes every hidden app off every saved home screen. */
    private fun takeOutHidden(settings: MutableMap<String, StoredValue>) {
        val hidden = settings.entries.mapNotNull { (key, value) ->
            HiddenApps.appKeyOf(key)?.takeIf { value == StoredValue.Flag(true) }
        }
        if (hidden.isEmpty()) return
        for ((key, value) in settings.entries.toList()) {
            if (!key.endsWith(BackupPart.LAYOUT_SUFFIX)) continue
            val text = (value as? StoredValue.Text)?.value ?: continue
            val saved = HomeLayoutCodec.decode(text) ?: continue
            var layout = saved
            for (appKey in hidden) layout = layout.without(ItemContent.App(appKey))
            if (layout != saved) settings[key] = StoredValue.Text(HomeLayoutCodec.encode(layout))
        }
    }
}

/**
 * The same home screen with the copy [old] of a widget of another app shown by the new copy
 * [new], which Android gave when the widget was placed again. It keeps its place and size.
 */
fun HomeLayout.withAppWidgetRenumbered(old: Int, new: Int, provider: String): HomeLayout {
    if (items.none { (it.content as? ItemContent.AppWidget)?.appWidgetId == old }) return this
    return copy(
        items = items.map { item ->
            val content = item.content
            if (content is ItemContent.AppWidget && content.appWidgetId == old) {
                item.copy(content = ItemContent.AppWidget(new, provider))
            } else {
                item
            }
        },
    )
}
