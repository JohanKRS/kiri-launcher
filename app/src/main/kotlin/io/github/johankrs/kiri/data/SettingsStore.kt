package io.github.johankrs.kiri.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.johankrs.kiri.core.AppMoves
import io.github.johankrs.kiri.core.AppNames
import io.github.johankrs.kiri.core.GridField
import io.github.johankrs.kiri.core.GridProfile
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.HiddenApps
import io.github.johankrs.kiri.core.backup.BackupPart
import io.github.johankrs.kiri.core.backup.BackupPlan
import io.github.johankrs.kiri.core.backup.StoredValue
import io.github.johankrs.kiri.core.backup.withAppWidgetRenumbered
import io.github.johankrs.kiri.core.fromStored
import io.github.johankrs.kiri.core.gesture.Gesture
import io.github.johankrs.kiri.core.gesture.GestureMap
import io.github.johankrs.kiri.core.gesture.LauncherAction
import io.github.johankrs.kiri.core.layout.CellPosition
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.GridSize
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.HomeLayoutCodec
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.mirrorFolders
import io.github.johankrs.kiri.core.look.DrawerLook
import io.github.johankrs.kiri.core.look.FolderLook
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconGround
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.core.look.IconSettings
import io.github.johankrs.kiri.core.look.LabelLook
import io.github.johankrs.kiri.core.look.MotionSettings
import io.github.johankrs.kiri.core.look.WallpaperSetup
import io.github.johankrs.kiri.core.look.WidgetLook
import io.github.johankrs.kiri.core.tv.TvBanner
import io.github.johankrs.kiri.core.tv.TvHomeItems
import io.github.johankrs.kiri.core.tv.TvInputNames
import io.github.johankrs.kiri.core.tv.TvItem
import io.github.johankrs.kiri.core.tv.TvLook
import io.github.johankrs.kiri.core.tv.TvSettings
import io.github.johankrs.kiri.core.tv.TvWidgets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Tells whether an app is still installed, by `AppEntry.key`. Null while the list is unknown. */
typealias InstalledCheck = ((appKey: String) -> Boolean)?

/**
 * All launcher settings read atomically from a single DataStore snapshot, so cold start arrives
 * in one frame and settings screens never capture stale defaults before DataStore finishes.
 */
@Immutable
data class LauncherSettings(
    val homeSpec: GridSpec,
    val drawerSpec: GridSpec,
    val homeLayout: HomeLayout,
    val gestures: GestureMap,
    val motion: MotionSettings,
    val drawerLook: DrawerLook,
    val labelLook: LabelLook,
    val wallpaperSetup: WallpaperSetup,
    val iconSettings: IconSettings,
    /** How each widget of another app is dressed, by the number of its copy. */
    val widgetLooks: Map<Int, WidgetLook> = emptyMap(),
    /** The names the person gave to apps, by `AppEntry.key`. */
    val appNames: Map<String, String> = emptyMap(),
    val folderLook: FolderLook = FolderLook(),
    /** The apps the person hid, by `AppEntry.key`. */
    val hiddenApps: Set<String> = emptySet(),
    /** The TV's home screen. */
    val tv: TvSettings = TvSettings(),
)

/** The launcher's saved settings. Which keys exist and what they mean is decided in `core`. */
@Stable
class SettingsStore private constructor(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private val stringKeys = ConcurrentHashMap<String, Preferences.Key<String>>()
    private val intKeys = ConcurrentHashMap<String, Preferences.Key<Int>>()
    private val boolKeys = ConcurrentHashMap<String, Preferences.Key<Boolean>>()

    private fun strKey(name: String): Preferences.Key<String> = stringKeys.computeIfAbsent(name, ::stringPreferencesKey)
    private fun intKey(name: String): Preferences.Key<Int> = intKeys.computeIfAbsent(name, ::intPreferencesKey)
    private fun boolKey(name: String): Preferences.Key<Boolean> = boolKeys.computeIfAbsent(name, ::booleanPreferencesKey)

    /** Reads every launcher setting together from one DataStore subscription. */
    fun snapshot(homeProfile: GridProfile, drawerProfile: GridProfile): Flow<LauncherSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            LauncherSettings(
                homeSpec = preferences.readGridSpec(homeProfile),
                drawerSpec = preferences.readGridSpec(drawerProfile),
                homeLayout = preferences.readHomeLayout(homeProfile),
                gestures = GestureMap.fromStored { key -> preferences[strKey(key)] },
                motion = MotionSettings.fromStored(
                    string = { key -> preferences[strKey(key)] },
                    int = { key -> preferences[intKey(key)] },
                ),
                drawerLook = DrawerLook.fromStored(
                    string = { key -> preferences[strKey(key)] },
                    int = { key -> preferences[intKey(key)] },
                ),
                labelLook = LabelLook.fromStored(
                    string = { key -> preferences[strKey(key)] },
                    int = { key -> preferences[intKey(key)] },
                ),
                wallpaperSetup = WallpaperSetup.fromStored(preferences[KEY_WALLPAPER_SETUP]),
                iconSettings = preferences.readIconSettings(),
                widgetLooks = preferences.readWidgetLooks(),
                appNames = preferences.readAppNames(),
                folderLook = FolderLook.fromStored(
                    string = { key -> preferences[strKey(key)] },
                    int = { key -> preferences[intKey(key)] },
                    boolean = { key -> preferences[boolKey(key)] },
                ),
                hiddenApps = preferences.readHiddenApps(),
                tv = preferences.readTvSettings(),
            )
        }
        .distinctUntilChanged()

    suspend fun setGridSpec(profile: GridProfile, spec: GridSpec) {
        store.edit { preferences ->
            for (field in GridField.entries) {
                preferences[intKey(field.storageKey(profile))] = field.get(spec)
            }
            preferences[boolKey(GridField.showLabelsStorageKey(profile))] = spec.showLabels
            preferences[boolKey(GridField.pageDotsStorageKey(profile))] = spec.showPageDots
            preferences[boolKey(GridField.evenGapsStorageKey(profile))] = spec.evenGaps
            preferences[intKey(GridField.placeStorageKey(profile))] = spec.place.key
        }
    }

    suspend fun setGesture(gesture: Gesture, action: LauncherAction) {
        store.edit { preferences ->
            preferences[strKey(GestureMap.storageKey(gesture))] = action.id
        }
    }

    /**
     * Puts [content] on the home screen, looking for room from [fromPage] onwards, and returns
     * where it ended up. The other orientation, when it has its own arrangement, gets it too.
     * [span] is the size it starts with; a grid too small for it gets it as large as it fits.
     */
    suspend fun addToHome(
        profile: GridProfile,
        content: ItemContent,
        fromPage: Int,
        isInstalled: InstalledCheck,
        span: CellSpan = CellSpan.Single,
    ): CellPosition? {
        var placed: CellPosition? = null
        store.edit { preferences ->
            val grid = preferences.readGridSize(profile)
            val current = preferences.readHomeLayout(profile).visibleOn(grid, isInstalled)
            val next = if (current.contains(content)) current else current.add(content, grid, span, fromPage)
            placed = next.items.firstOrNull { it.content == content }?.position
            preferences[homeLayoutKey(profile)] = HomeLayoutCodec.encode(next)

            val other = profile.otherOrientation()
            HomeLayoutCodec.decode(preferences[homeLayoutKey(other)])?.let { saved ->
                val otherGrid = preferences.readGridSize(other)
                val fitted = saved.fitTo(otherGrid)
                if (!fitted.contains(content)) {
                    preferences[homeLayoutKey(other)] = HomeLayoutCodec.encode(fitted.add(content, otherGrid, span))
                }
            }
        }
        return placed
    }

    /**
     * Puts [content] on the cell [at] of the home screen, as when it is dropped there (see
     * `HomeLayout.place`). The other orientation, when it has its own arrangement, gets it in
     * its first free place.
     */
    suspend fun placeOnHome(profile: GridProfile, content: ItemContent, at: CellPosition, isInstalled: InstalledCheck) {
        store.edit { preferences ->
            val grid = preferences.readGridSize(profile)
            val current = preferences.readHomeLayout(profile).visibleOn(grid, isInstalled)
            preferences[homeLayoutKey(profile)] = HomeLayoutCodec.encode(current.place(content, at, grid))

            val other = profile.otherOrientation()
            HomeLayoutCodec.decode(preferences[homeLayoutKey(other)])?.let { saved ->
                val otherGrid = preferences.readGridSize(other)
                val fitted = saved.fitTo(otherGrid)
                if (!fitted.contains(content)) {
                    preferences[homeLayoutKey(other)] = HomeLayoutCodec.encode(fitted.add(content, otherGrid))
                }
            }
        }
    }

    /**
     * Moves the item [id] to the cell [to]: a free cell is taken, an app already there swaps
     * places with it. Only the orientation of [profile] is rearranged.
     */
    suspend fun moveOnHome(profile: GridProfile, id: Long, to: CellPosition, isInstalled: InstalledCheck) {
        store.edit { preferences ->
            val grid = preferences.readGridSize(profile)
            val current = preferences.readHomeLayout(profile).visibleOn(grid, isInstalled)
            preferences[homeLayoutKey(profile)] = HomeLayoutCodec.encode(current.move(id, to, grid))
        }
    }

    /**
     * Gives the item [id] the size [span]. Whatever it then covers moves to the nearest free
     * place. Only the orientation of [profile] is rearranged.
     */
    suspend fun resizeOnHome(profile: GridProfile, id: Long, span: CellSpan, isInstalled: InstalledCheck) {
        store.edit { preferences ->
            val grid = preferences.readGridSize(profile)
            val current = preferences.readHomeLayout(profile).visibleOn(grid, isInstalled)
            preferences[homeLayoutKey(profile)] = HomeLayoutCodec.encode(current.resize(id, span, grid))
        }
    }

    /** Takes [content] off the home screen, in both orientations. */
    suspend fun removeFromHome(profile: GridProfile, content: ItemContent, isInstalled: InstalledCheck) {
        store.edit { preferences ->
            val current = preferences.readHomeLayout(profile).visibleOn(preferences.readGridSize(profile), isInstalled)
            preferences[homeLayoutKey(profile)] = HomeLayoutCodec.encode(current.without(content))

            val other = profile.otherOrientation()
            HomeLayoutCodec.decode(preferences[homeLayoutKey(other)])?.let { saved ->
                preferences[homeLayoutKey(other)] = HomeLayoutCodec.encode(saved.without(content))
            }
        }
    }

    /**
     * Changes the home screen of [profile] with [change], which gets what is shown now and the
     * grid. The folders [folders] are then brought in line in the other orientation, when it
     * has an arrangement of its own. Returns the new layout.
     */
    suspend fun editHome(
        profile: GridProfile,
        isInstalled: InstalledCheck,
        folders: Collection<String> = emptyList(),
        change: (HomeLayout, GridSize) -> HomeLayout,
    ): HomeLayout {
        var result = HomeLayout()
        store.edit { preferences ->
            val grid = preferences.readGridSize(profile)
            val current = preferences.readHomeLayout(profile).visibleOn(grid, isInstalled)
            // Remembered with the grid it was arranged on, so other grids can follow from it.
            val next = change(current, grid).let { if (it.grid == null) it.copy(grid = grid) else it }
            result = next
            if (next == current) return@edit
            preferences[homeLayoutKey(profile)] = HomeLayoutCodec.encode(next)

            if (folders.isEmpty()) return@edit
            val other = profile.otherOrientation()
            HomeLayoutCodec.decode(preferences[homeLayoutKey(other)])?.let { saved ->
                val otherGrid = preferences.readGridSize(other)
                val mirrored = saved.fitTo(otherGrid).mirrorFolders(folders, next, otherGrid)
                preferences[homeLayoutKey(other)] = HomeLayoutCodec.encode(mirrored)
            }
        }
        return result
    }

    suspend fun setFolderLook(look: FolderLook) {
        store.edit { preferences ->
            preferences[strKey(FolderLook.KEY_GROUND)] = look.ground.key
            preferences[intKey(FolderLook.KEY_GROUND_OPACITY)] = look.groundOpacity
            preferences[strKey(FolderLook.KEY_SHAPE)] = look.shape?.key ?: FolderLook.SAME_SHAPE
            preferences[intKey(FolderLook.KEY_MINIS)] = look.minis
            preferences[intKey(FolderLook.KEY_MARGIN)] = look.margin
            preferences[intKey(FolderLook.KEY_SPACING)] = look.spacing
            preferences[boolKey(FolderLook.KEY_SHOW_NAME)] = look.showName
            preferences[strKey(FolderLook.KEY_PANEL_GLASS)] = look.panelGlass.key
            preferences[intKey(FolderLook.KEY_PANEL_OPACITY)] = look.panelOpacity
            preferences[intKey(FolderLook.KEY_COLUMNS)] = look.columns
            preferences[intKey(FolderLook.KEY_PANEL_SPACING)] = look.panelSpacing
            preferences[intKey(FolderLook.KEY_PANEL_MARGIN)] = look.panelMargin
            preferences[boolKey(FolderLook.KEY_PANEL_NAMES)] = look.panelNames
            preferences[boolKey(FolderLook.KEY_PANEL_TITLE)] = look.panelTitle
            preferences[intKey(FolderLook.KEY_DIM)] = look.dim
        }
    }

    /** Dresses the copy [appWidgetId] of a widget of another app as [look] says. */
    suspend fun setWidgetLook(appWidgetId: Int, look: WidgetLook) {
        store.edit { preferences ->
            val key = strKey(WidgetLook.storageKey(appWidgetId))
            if (look == WidgetLook.AsMade) preferences.remove(key) else preferences[key] = look.encode()
        }
    }

    /** Forgets the looks of the copies that are not among [inUse]: those widgets are gone. */
    suspend fun keepWidgetLooksOf(inUse: Set<Int>) {
        val gone: (Preferences.Key<*>) -> Boolean = { key -> WidgetLook.appWidgetIdOf(key.name)?.let { it !in inUse } == true }
        // Nearly always there is nothing to forget, and then nothing is written.
        val any = runCatching { store.data.first().asMap().keys.any(gone) }.getOrDefault(false)
        if (!any) return
        store.edit { preferences ->
            for (key in preferences.asMap().keys.filter(gone)) preferences.remove(key)
        }
    }

    /**
     * The numbers of every app widget placed on a home screen, in any arrangement kept here.
     * Null when the saved settings could not be read, which says nothing about what is placed.
     */
    suspend fun appWidgetIdsInUse(): Set<Int>? = runCatching {
        val preferences = store.data.first()
        preferences.asMap().entries
            .filter { it.key.name.endsWith(LAYOUT_SUFFIX) }
            .flatMapTo(HashSet()) { HomeLayoutCodec.decode(it.value as? String)?.appWidgetIds.orEmpty() }
    }.getOrNull()

    suspend fun setMotion(motion: MotionSettings) {
        store.edit { preferences ->
            preferences[KEY_APP_OPEN] = motion.appOpen.key
            preferences[KEY_HOME] = motion.home.key
            preferences[KEY_DRAWER] = motion.drawer.key
            preferences[KEY_SPEED] = motion.speed
        }
    }

    suspend fun setDrawerLook(look: DrawerLook) {
        store.edit { preferences ->
            preferences[KEY_DRAWER_GLASS] = look.glass.key
            preferences[KEY_DRAWER_OPACITY] = look.opacityPercent
            preferences[KEY_DRAWER_BLUR] = look.blurDp
        }
    }

    suspend fun setLabelLook(look: LabelLook) {
        store.edit { preferences ->
            preferences[KEY_LABEL_SIZE] = look.sizeSp
            preferences[KEY_LABEL_WEIGHT] = look.weight.key
        }
    }

    suspend fun setWallpaperSetup(setup: WallpaperSetup) {
        store.edit { preferences -> preferences[KEY_WALLPAPER_SETUP] = setup.stored }
    }

    suspend fun setIconLook(look: IconLook) {
        store.edit { preferences ->
            preferences[KEY_ICON_SHAPE] = look.shape.key
            preferences[KEY_ICON_GLASS_USE] = look.glassUse.key
            preferences[KEY_ICON_GLASS] = look.glass.key
            preferences[KEY_ICON_GLASS_OPACITY] = look.glassOpacity
            preferences[KEY_ICON_RELIEF] = look.relief.key
            preferences[KEY_ICON_EDGE_LIGHT] = look.edgeLight.key
            preferences[KEY_ICON_EDGE_WIDTH] = look.edgeWidth
            preferences[KEY_ICON_SHADOW] = look.shadow.key
            preferences[KEY_ICON_ART] = look.artPercent
            preferences[KEY_ICON_SHINE] = look.shine.key
        }
    }

    /**
     * Sets how large the drawing of one app's icon is, everywhere the app is shown. Null goes
     * back to the size all icons share.
     */
    suspend fun setIconArt(appKey: String, percent: Int?) {
        store.edit { preferences ->
            val key = intKey(IconLook.appArtKey(appKey))
            if (percent == null) preferences.remove(key) else preferences[key] = percent.coerceIn(IconLook.APP_ART_PERCENT)
        }
    }

    /**
     * Sets what the icon of one app sits on, everywhere the app is shown. Null goes back to
     * what all icons share.
     */
    suspend fun setIconGround(appKey: String, ground: IconGround?) {
        store.edit { preferences ->
            val key = strKey(IconLook.appGroundKey(appKey))
            if (ground == null) preferences.remove(key) else preferences[key] = ground.key
        }
    }

    /** Gives every app back the size of drawing and the background that all icons share. */
    suspend fun clearIconArts() {
        store.edit { preferences ->
            for (key in preferences.asMap().keys.toList()) {
                if (IconLook.appKeyOfArt(key.name) != null || IconLook.appKeyOfGround(key.name) != null) {
                    preferences.remove(key)
                }
            }
        }
    }

    /** Chooses the icon of an app everywhere it is shown. Null goes back to the app's own icon. */
    suspend fun setIconChoice(appKey: String, choice: IconChoice?) {
        store.edit { preferences ->
            val key = strKey(IconChoice.storageKey(appKey))
            if (choice == null) preferences.remove(key) else preferences[key] = choice.stored
        }
    }

    /**
     * Gives an app a name of its own, everywhere it is shown. Null goes back to the name the app
     * came with.
     */
    suspend fun setAppName(appKey: String, name: String?) {
        store.edit { preferences ->
            val key = strKey(AppNames.storageKey(appKey))
            if (name.isNullOrBlank()) preferences.remove(key) else preferences[key] = name
        }
    }

    /**
     * Hides an app everywhere, or shows it again. A hidden app also leaves every home screen and
     * folder it was on, in both orientations; shown again, it comes back to the drawer only,
     * like a newly installed app.
     */
    suspend fun setAppHidden(appKey: String, hidden: Boolean) {
        store.edit { preferences ->
            val key = boolKey(HiddenApps.storageKey(appKey))
            if (!hidden) {
                preferences.remove(key)
                return@edit
            }
            preferences[key] = true
            val app = ItemContent.App(appKey)
            for ((layoutKey, value) in preferences.asMap().entries.toList()) {
                if (!layoutKey.name.endsWith(LAYOUT_SUFFIX)) continue
                val saved = HomeLayoutCodec.decode(value as? String) ?: continue
                val without = saved.without(app)
                if (without != saved) preferences[strKey(layoutKey.name)] = HomeLayoutCodec.encode(without)
            }
            // And the TV's home screen.
            val tvHome = TvHomeItems.decode(preferences[KEY_TV_HOME])
            if (tvHome != null) {
                val without = tvHome.without(TvItem.App(appKey))
                if (without != tvHome) preferences[KEY_TV_HOME] = TvHomeItems.encode(without)
            }
        }
    }

    /** How the TV's home screen looks as a whole. */
    suspend fun setTvLook(look: TvLook) {
        store.edit { preferences ->
            preferences[strKey(TvLook.KEY_ARRANGEMENT)] = look.arrangement.key
            preferences[strKey(TvLook.KEY_ALIGN)] = look.align.key
            preferences[intKey(TvLook.KEY_SIZE)] = look.bannerSize
            preferences[strKey(TvLook.KEY_NAMES)] = look.names.key
            preferences[strKey(TvLook.KEY_WALLPAPER)] = look.wallpaper.key
            preferences[intKey(TvLook.KEY_GAP)] = look.gap
            preferences[strKey(TvLook.KEY_SHADOW)] = look.shadow.key
        }
    }

    /**
     * Changes what is on the TV's home screen by [change], from what is saved now; [start] is
     * what it is while nothing was saved yet. Two changes in a row never undo each other.
     */
    suspend fun editTvHome(start: TvHomeItems, change: (TvHomeItems) -> TvHomeItems) {
        store.edit { preferences ->
            val now = TvHomeItems.decode(preferences[KEY_TV_HOME]) ?: start
            val next = change(now)
            if (next != now || preferences[KEY_TV_HOME] == null) preferences[KEY_TV_HOME] = TvHomeItems.encode(next)
        }
    }

    /** The banner of one place of the TV's home screen; null goes back to what it has by itself. */
    suspend fun setTvBanner(item: TvItem, banner: TvBanner?) {
        store.edit { preferences ->
            val key = strKey(TvBanner.storageKey(item.key))
            if (banner == null) preferences.remove(key) else preferences[key] = banner.stored
        }
    }

    /** The widgets over the TV's home screen. */
    suspend fun setTvWidgets(widgets: TvWidgets) {
        store.edit { preferences -> preferences[KEY_TV_WIDGETS] = TvWidgets.encode(widgets) }
    }

    /** Changes the widgets over the TV's home screen by [change], from what is saved now. */
    suspend fun editTvWidgets(change: (TvWidgets) -> TvWidgets) {
        store.edit { preferences ->
            val now = TvWidgets.decode(preferences[KEY_TV_WIDGETS])
            val next = change(now)
            if (next != now) preferences[KEY_TV_WIDGETS] = TvWidgets.encode(next)
        }
    }

    /** The name of an input of the TV; null goes back to the input's own. */
    suspend fun setTvInputName(inputId: String, name: String?) {
        store.edit { preferences ->
            val key = strKey(TvInputNames.storageKey(inputId))
            if (name.isNullOrBlank()) preferences.remove(key) else preferences[key] = name
        }
    }

    /**
     * Moves what is saved for apps that Android now lists under another key: an app that
     * turned to another of its icons (see `AppMoves`) keeps its places on every home screen,
     * its folder, its icon and its name. [installed] are the apps listed now, by
     * `AppEntry.key`; [mainEntry] tells, by package, the entry Android opens for an app as a
     * whole. Returns what moved, old key to new; nothing is written when nothing did.
     */
    suspend fun followMovedApps(
        installed: Set<String>,
        mainEntry: (packageName: String) -> String?,
    ): Map<String, String> = withContext(Dispatchers.Default) {
        // Read first: almost always nothing moved, and then nothing is written at all.
        val now = runCatching { store.data.first() }.getOrNull() ?: return@withContext emptyMap()
        if (AppMoves.find(AppMoves.appKeysIn(now.dump()), installed, mainEntry).isEmpty()) return@withContext emptyMap()
        var moved = emptyMap<String, String>()
        store.edit { preferences ->
            val current = preferences.dump()
            moved = AppMoves.find(AppMoves.appKeysIn(current), installed, mainEntry)
            if (moved.isNotEmpty()) preferences.replaceAll(AppMoves.applied(current, moved))
        }
        moved
    }

    /** Every saved setting as it is, for a backup. Null when the settings could not be read. */
    suspend fun dumpAll(): Map<String, StoredValue>? = runCatching { store.data.first().dump() }.getOrNull()

    /** Puts the [parts] of the settings of a backup in place of what they hold now (see `BackupPlan`). */
    suspend fun restore(backup: Map<String, StoredValue>, parts: Set<BackupPart>) {
        store.edit { preferences ->
            preferences.replaceAll(BackupPlan.restore(preferences.dump(), backup, parts))
        }
    }

    /**
     * Shows the copy [old] of a widget of another app by the new copy [new] on every home
     * screen, in the same place and with the same look: the widget was placed again after
     * Android had forgotten the old copy.
     */
    suspend fun renumberAppWidget(old: Int, new: Int, provider: String) {
        store.edit { preferences ->
            for ((key, value) in preferences.asMap().entries.toList()) {
                if (!key.name.endsWith(LAYOUT_SUFFIX)) continue
                val saved = HomeLayoutCodec.decode(value as? String) ?: continue
                val renumbered = saved.withAppWidgetRenumbered(old, new, provider)
                if (renumbered != saved) preferences[strKey(key.name)] = HomeLayoutCodec.encode(renumbered)
            }
            val oldLook = strKey(WidgetLook.storageKey(old))
            preferences[oldLook]?.let { look ->
                preferences[strKey(WidgetLook.storageKey(new))] = look
                preferences.remove(oldLook)
            }
        }
    }

    private fun Preferences.dump(): Map<String, StoredValue> {
        val values = LinkedHashMap<String, StoredValue>()
        for ((key, value) in asMap()) StoredValue.of(value)?.let { values[key.name] = it }
        return values
    }

    /** Leaves exactly [values] saved: what is not among them goes, the rest is written as given. */
    private fun MutablePreferences.replaceAll(values: Map<String, StoredValue>) {
        val now = asMap().entries.associate { (key, value) -> key.name to value }
        for (key in asMap().keys.toList()) {
            if (key.name !in values) remove(key)
        }
        for ((name, value) in values) {
            if (now[name] == value.raw) continue
            // Keys are told apart by name only: a setting saved before as another kind of value
            // is replaced, not kept beside it.
            when (value) {
                is StoredValue.Text -> this[strKey(name)] = value.value
                is StoredValue.Whole -> this[intKey(name)] = value.value
                is StoredValue.Flag -> this[boolKey(name)] = value.value
                is StoredValue.Big -> this[longPreferencesKey(name)] = value.value
                is StoredValue.Fraction -> this[floatPreferencesKey(name)] = value.value
                is StoredValue.Precise -> this[doublePreferencesKey(name)] = value.value
                is StoredValue.Texts -> this[stringSetPreferencesKey(name)] = value.value
            }
        }
    }

    private fun Preferences.readHiddenApps(): Set<String> {
        var hidden: HashSet<String>? = null
        for ((key, value) in asMap()) {
            val appKey = HiddenApps.appKeyOf(key.name) ?: continue
            if (value == true) (hidden ?: HashSet<String>().also { hidden = it }) += appKey
        }
        return hidden ?: emptySet()
    }

    private fun Preferences.readTvSettings(): TvSettings {
        var banners: HashMap<String, TvBanner>? = null
        var names: HashMap<String, String>? = null
        for ((key, value) in asMap()) {
            val name = key.name
            if (!name.startsWith(TV_PREFIX)) continue
            TvBanner.itemKeyOf(name)?.let { item ->
                val banner = TvBanner.fromStored(value as? String) ?: return@let
                (banners ?: HashMap<String, TvBanner>().also { banners = it })[item] = banner
            }
            TvInputNames.inputIdOf(name)?.let { input ->
                val typed = (value as? String)?.takeIf { it.isNotBlank() } ?: return@let
                (names ?: HashMap<String, String>().also { names = it })[input] = typed
            }
        }
        return TvSettings(
            look = TvLook.fromStored(
                string = { key -> this[strKey(key)] },
                int = { key -> this[intKey(key)] },
            ),
            home = TvHomeItems.decode(this[KEY_TV_HOME]),
            banners = banners ?: emptyMap(),
            inputNames = names ?: emptyMap(),
            widgets = TvWidgets.decode(this[KEY_TV_WIDGETS]),
        )
    }

    private fun Preferences.readAppNames(): Map<String, String> {
        val names = HashMap<String, String>()
        for ((key, value) in asMap()) {
            val appKey = AppNames.appKeyOf(key.name) ?: continue
            (value as? String)?.takeIf { it.isNotBlank() }?.let { names[appKey] = it }
        }
        return names
    }

    private fun Preferences.readIconSettings(): IconSettings {
        val choices = HashMap<String, IconChoice>()
        val artPercents = HashMap<String, Int>()
        val grounds = HashMap<String, IconGround>()
        for ((key, value) in asMap()) {
            val name = key.name
            IconChoice.appKeyOf(name)?.let { appKey ->
                IconChoice.fromStored(value as? String)?.let { choices[appKey] = it }
            }
            IconLook.appKeyOfArt(name)?.let { appKey ->
                (value as? Int)?.let { artPercents[appKey] = it.coerceIn(IconLook.APP_ART_PERCENT) }
            }
            IconLook.appKeyOfGround(name)?.let { appKey ->
                IconGround.fromKey(value as? String)?.let { grounds[appKey] = it }
            }
        }
        return IconSettings(
            look = IconLook.fromStored(
                string = { key -> this[strKey(key)] },
                int = { key -> this[intKey(key)] },
                boolean = { key -> this[boolKey(key)] },
            ),
            choices = choices,
            artPercents = artPercents,
            grounds = grounds,
        )
    }

    private fun Preferences.readWidgetLooks(): Map<Int, WidgetLook> {
        var looks: HashMap<Int, WidgetLook>? = null
        for ((key, value) in asMap()) {
            val id = WidgetLook.appWidgetIdOf(key.name) ?: continue
            val map = looks ?: HashMap<Int, WidgetLook>().also { looks = it }
            map[id] = WidgetLook.decode(value as? String)
        }
        return looks ?: emptyMap()
    }

    private fun Preferences.readGridSpec(profile: GridProfile): GridSpec = GridSpec.fromStored(
        profile = profile,
        int = { key -> this[intKey(key)] },
        boolean = { key -> this[boolKey(key)] },
    )

    private fun Preferences.readGridSize(profile: GridProfile): GridSize =
        readGridSpec(profile).let { GridSize(columns = it.columns, rows = it.rows) }

    private fun Preferences.readHomeLayout(profile: GridProfile): HomeLayout =
        HomeLayoutCodec.decode(this[homeLayoutKey(profile)])
            ?: HomeLayoutCodec.decode(this[homeLayoutKey(profile.otherOrientation())])
            ?: HomeLayout()

    private fun homeLayoutKey(profile: GridProfile) = strKey("${profile.id}$LAYOUT_SUFFIX")

    private fun GridProfile.otherOrientation(): GridProfile = copy(landscape = !landscape)

    companion object {
        /** How the name of every setting that holds a home layout ends. */
        private const val LAYOUT_SUFFIX = ".layout"

        private val KEY_APP_OPEN = stringPreferencesKey(MotionSettings.KEY_APP_OPEN)
        private val KEY_HOME = stringPreferencesKey(MotionSettings.KEY_HOME)
        private val KEY_DRAWER = stringPreferencesKey(MotionSettings.KEY_DRAWER)
        private val KEY_SPEED = intPreferencesKey(MotionSettings.KEY_SPEED)

        private val KEY_DRAWER_GLASS = stringPreferencesKey(DrawerLook.KEY_GLASS)
        private val KEY_DRAWER_OPACITY = intPreferencesKey(DrawerLook.KEY_OPACITY)
        private val KEY_DRAWER_BLUR = intPreferencesKey(DrawerLook.KEY_BLUR)

        private val KEY_LABEL_SIZE = intPreferencesKey(LabelLook.KEY_SIZE)
        private val KEY_LABEL_WEIGHT = stringPreferencesKey(LabelLook.KEY_WEIGHT)

        private val KEY_WALLPAPER_SETUP = stringPreferencesKey(WallpaperSetup.KEY)

        private val KEY_TV_HOME = stringPreferencesKey(TvHomeItems.KEY)
        private val KEY_TV_WIDGETS = stringPreferencesKey(TvWidgets.KEY)
        private const val TV_PREFIX = "tv."

        private val KEY_ICON_SHAPE = stringPreferencesKey(IconLook.KEY_SHAPE)
        private val KEY_ICON_GLASS_USE = stringPreferencesKey(IconLook.KEY_GLASS_USE)
        private val KEY_ICON_GLASS = stringPreferencesKey(IconLook.KEY_GLASS)
        private val KEY_ICON_GLASS_OPACITY = intPreferencesKey(IconLook.KEY_GLASS_OPACITY)
        private val KEY_ICON_RELIEF = stringPreferencesKey(IconLook.KEY_RELIEF)
        private val KEY_ICON_EDGE_LIGHT = stringPreferencesKey(IconLook.KEY_EDGE_LIGHT)
        private val KEY_ICON_EDGE_WIDTH = intPreferencesKey(IconLook.KEY_EDGE_WIDTH)
        private val KEY_ICON_SHADOW = stringPreferencesKey(IconLook.KEY_SHADOW)
        private val KEY_ICON_ART = intPreferencesKey(IconLook.KEY_ART)
        private val KEY_ICON_SHINE = stringPreferencesKey(IconLook.KEY_SHINE)

        @Volatile
        private var instance: SettingsStore? = null

        fun of(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}
