package io.github.johankrs.kiri.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.KiriAccessibilityService
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.GridProfile
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.HomeGridGeometry
import io.github.johankrs.kiri.core.HomeRoom
import io.github.johankrs.kiri.core.Surface
import io.github.johankrs.kiri.core.WidgetCells
import io.github.johankrs.kiri.core.gesture.LauncherAction
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.GridSize
import io.github.johankrs.kiri.core.layout.HomeItem
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.canMerge
import io.github.johankrs.kiri.core.layout.dissolveFolder
import io.github.johankrs.kiri.core.layout.folder
import io.github.johankrs.kiri.core.layout.folderOf
import io.github.johankrs.kiri.core.layout.mergeApp
import io.github.johankrs.kiri.core.layout.renameFolder
import io.github.johankrs.kiri.core.layout.reorderFolder
import io.github.johankrs.kiri.core.layout.setFolderGround
import io.github.johankrs.kiri.core.layout.startPage
import io.github.johankrs.kiri.core.layout.withoutFolder
import io.github.johankrs.kiri.core.look.DrawerLook
import io.github.johankrs.kiri.core.look.FolderGround
import io.github.johankrs.kiri.core.look.FolderLook
import io.github.johankrs.kiri.core.look.GlassBevel
import io.github.johankrs.kiri.core.look.HomeReturnStyle
import io.github.johankrs.kiri.core.look.IconSettings
import io.github.johankrs.kiri.core.look.LabelLook
import io.github.johankrs.kiri.core.look.WidgetLook
import io.github.johankrs.kiri.core.named
import io.github.johankrs.kiri.core.shown
import io.github.johankrs.kiri.core.tv.TvWallpaper
import io.github.johankrs.kiri.core.tv.TvWidgetKind
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.data.Backups
import io.github.johankrs.kiri.data.InstalledCheck
import io.github.johankrs.kiri.data.LauncherSettings
import io.github.johankrs.kiri.data.SettingsStore
import io.github.johankrs.kiri.data.TvInput
import io.github.johankrs.kiri.data.TvInputs
import io.github.johankrs.kiri.data.WallpaperSource
import io.github.johankrs.kiri.data.WallpaperStudio
import io.github.johankrs.kiri.data.expandNotifications
import io.github.johankrs.kiri.isTelevision
import io.github.johankrs.kiri.market.MarketWidgetSpec
import io.github.johankrs.kiri.ui.settings.AppIconScreen
import io.github.johankrs.kiri.ui.settings.BackupScreen
import io.github.johankrs.kiri.ui.settings.DrawerLookScreen
import io.github.johankrs.kiri.ui.settings.FolderSettingsScreen
import io.github.johankrs.kiri.ui.settings.GestureSettingsScreen
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GridSettingsScreen
import io.github.johankrs.kiri.ui.settings.HiddenAppsScreen
import io.github.johankrs.kiri.ui.settings.IconLookScreen
import io.github.johankrs.kiri.ui.settings.IconPickerScreen
import io.github.johankrs.kiri.ui.settings.MotionSettingsScreen
import io.github.johankrs.kiri.ui.settings.RestoreScreen
import io.github.johankrs.kiri.ui.settings.SettingsIndexScreen
import io.github.johankrs.kiri.ui.settings.TvPictureScreen
import io.github.johankrs.kiri.ui.settings.TvScreenSettings
import io.github.johankrs.kiri.ui.settings.TypographyScreen
import io.github.johankrs.kiri.ui.settings.WallpaperScreen
import io.github.johankrs.kiri.ui.settings.labelRes
import io.github.johankrs.kiri.ui.theme.Backdrop
import io.github.johankrs.kiri.ui.theme.GlassPanes
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.LocalBackdrop
import io.github.johankrs.kiri.ui.theme.LocalGlassTile
import io.github.johankrs.kiri.ui.theme.LocalLabelLook
import io.github.johankrs.kiri.ui.theme.WallpaperStandIn
import io.github.johankrs.kiri.ui.settings.labelRes
import io.github.johankrs.kiri.ui.theme.appLabelStyle
import io.github.johankrs.kiri.ui.theme.drawWallpaper
import io.github.johankrs.kiri.ui.theme.kiriSheet
import io.github.johankrs.kiri.ui.theme.spec
import io.github.johankrs.kiri.ui.tv.TvHome
import io.github.johankrs.kiri.ui.tv.TvHomeActions
import io.github.johankrs.kiri.ui.tv.TvHomePreview
import io.github.johankrs.kiri.ui.widgets.PackageIcon
import io.github.johankrs.kiri.ui.widgets.PlacedWidget
import io.github.johankrs.kiri.ui.widgets.WidgetsScreen
import io.github.johankrs.kiri.widgets.HomeCells
import io.github.johankrs.kiri.widgets.KiriWidgets
import io.github.johankrs.kiri.widgets.market.MarketRow
import io.github.johankrs.kiri.widgets.market.MarketWidget
import java.util.UUID
import io.github.johankrs.kiri.widgets.sizeLimits
import io.github.johankrs.kiri.widgets.startSpan
import io.github.johankrs.kiri.widgets.system.LocalWidgetLooks
import io.github.johankrs.kiri.widgets.system.LocalWidgetPlaceAgain
import io.github.johankrs.kiri.widgets.system.SystemWidgets
import io.github.johankrs.kiri.widgets.system.rememberWidgetSetup
import io.github.johankrs.kiri.widgets.widgetFacts
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What is on screen. The last entry of the stack is visible; Back removes it. */
private enum class Screen {
    HOME,
    DRAWER,

    /** The home screen of a TV, with "All apps" in it: a TV has neither of the two above. */
    TV_HOME,
    SETTINGS,

    /** Where widgets are chosen and looked after. On the sheet of the settings, but not one of them. */
    WIDGETS,
    HOME_GRID_SETTINGS,
    DRAWER_GRID_SETTINGS,
    GESTURE_SETTINGS,
    WIDGET_SETTINGS,
    DRAWER_LOOK_SETTINGS,
    ICON_LOOK_SETTINGS,
    MOTION_SETTINGS,
    ICON_PICKER,
    TYPOGRAPHY_SETTINGS,
    WALLPAPER_SETTINGS,
    FOLDER_SETTINGS,
    HIDDEN_APPS,
    APP_ICON_SETTINGS,
    BACKUP,
    BACKUP_RESTORE,
    TV_SCREEN_SETTINGS,

    /** The pictures to choose the TV's wallpaper from. */
    TV_PICTURE,
}

private val ScreenStackSaver = Saver<List<Screen>, String>(
    save = { stack -> stack.joinToString(",") { it.name } },
    restore = { saved -> saved.split(",").map { Screen.valueOf(it) } },
)

/** What a long-press menu is about, and where that is on screen (root coordinates). */
private sealed interface MenuTarget {
    val anchor: Rect

    data class App(val app: AppEntry, override val anchor: Rect) : MenuTarget

    /** A widget on the home screen, by the id of its item. */
    data class Widget(val itemId: Long, override val anchor: Rect) : MenuTarget

    /** A folder on the home screen, by the id of its item; [anchor] is its closed face. */
    data class Folder(val itemId: Long, override val anchor: Rect) : MenuTarget

    /** An empty place of the home screen: the menu of the home screen itself. */
    data class Home(override val anchor: Rect) : MenuTarget

    /** How a widget of another app on the home screen is dressed, by the id of its item. */
    data class Look(val itemId: Long, override val anchor: Rect) : MenuTarget
}

/** What a long-press menu shows: kept as a whole so it can stay on screen while it fades out. */
private class MenuContent(
    val title: String,
    val anchor: Rect,
    val actions: List<MenuAction>,
    val steppers: List<MenuStepper> = emptyList(),
    val quickActions: List<MenuAction> = emptyList(),
    val app: AppEntry? = null,
    val folder: ItemContent.Folder? = null,
    /** The app whose icon stands next to the title, when the menu is about a widget. */
    val packageName: String? = null,
)

/** An app held in the drawer: it becomes a [Carry] if the finger then moves. */
private class DrawerLift(val app: AppEntry, val iconBounds: Rect)

/** An app in the air between the drawer and the home screen. Positions are root coordinates. */
@Stable
private class Carry(val app: AppEntry, private val iconCenter: Offset, private val start: Offset) {
    /** Where the finger is. */
    var pointer by mutableStateOf(start)

    /** Once the finger lets go, the app glides on its own along this. */
    val landing = Animatable(Offset.Zero, Offset.VectorConverter)
    var landed by mutableStateOf(false)

    /** It lands inside an app or a folder, so it gets small instead of settling in a cell. */
    var into by mutableStateOf(false)

    /** The middle of the carried app: it keeps the distance to the finger it was picked up with. */
    val center: Offset get() = if (landed) landing.value else iconCenter + (pointer - start)
}

/**
 * Root of the launcher.
 *
 * [backdrop] is where the blurred wallpaper is put for all glass to show through; whoever calls
 * this also hands it down as `LocalBackdrop`.
 *
 * [refreshTick] changes whenever the launcher comes back to the front.
 * [homeTick] changes whenever the Home button is pressed (go back to the first screen).
 */
@Composable
fun KiriApp(
    repository: AppRepository,
    settings: SettingsStore,
    backdrop: Backdrop,
    refreshTick: Int,
    homeTick: Int,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Where the list of settings was left, kept while the screens opened from it come and go.
    val settingsIndexScroll = rememberScrollState()

    val formFactor = remember(configuration) {
        FormFactor.of(
            isTelevision = context.isTelevision(),
            smallestWidthDp = configuration.smallestScreenWidthDp,
        )
    }
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val drawerProfile = remember(formFactor, landscape) {
        GridProfile(Surface.DRAWER, formFactor, landscape)
    }
    val homeProfile = remember(formFactor, landscape) {
        GridProfile(Surface.HOME, formFactor, landscape)
    }

    // Re-read the app list only when the system reports an app changed, never on ordinary Home presses.
    var ownApps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(repository.appChanges, formFactor) {
        ownApps = repository.loadApps(formFactor)
    }

    // An app that turned to another of its icons (an activity-alias each, switched from inside
    // the app) or renamed its screen in an update is the same app under a new key: it keeps its
    // place on the home screen, its folder, its icon and its name. After the first list it waits
    // a moment, so an app that turns one icon off before turning the next on is seen done.
    val appListsSeen = remember { intArrayOf(0) }
    LaunchedEffect(ownApps) {
        if (ownApps.isEmpty()) return@LaunchedEffect
        if (appListsSeen[0]++ > 0) delay(APP_MOVE_SETTLE_MS)
        val installed = ownApps.mapTo(HashSet()) { it.key }
        settings.followMovedApps(installed) { packageName -> repository.mainEntryKey(packageName, formFactor) }
    }

    // On a TV: its inputs, and which apps were made for a TV (they fill its first home screen).
    val tvInputs = remember(formFactor) { if (formFactor == FormFactor.TV) TvInputs.of(context) else null }
    var inputs by remember { mutableStateOf<List<TvInput>?>(null) }
    LaunchedEffect(tvInputs, tvInputs?.changes) { inputs = tvInputs?.load() }
    var tvAppKeys by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(repository.appChanges, formFactor) {
        if (formFactor == FormFactor.TV) tvAppKeys = repository.loadTvAppKeys()
    }

    // One atomic DataStore read for every launcher setting so cold start lands in a single frame
    // and settings screens never capture defaults before disk values arrive.
    val lastLoaded = remember { arrayOfNulls<LauncherSettings>(1) }
    val stored by remember(homeProfile, drawerProfile) {
        settings.snapshot(homeProfile, drawerProfile)
    }.collectAsState(initial = lastLoaded[0])
    if (stored != null) lastLoaded[0] = stored

    // A TV has a home screen of its own, made for a remote, with all its apps in it.
    val root = if (formFactor == FormFactor.TV) Screen.TV_HOME else Screen.HOME
    var stack by rememberSaveable(root, stateSaver = ScreenStackSaver) { mutableStateOf(listOf(root)) }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    // Always handled: a home screen has nowhere to go back to.
    BackHandler { pop() }

    val loaded = stored ?: return

    // The apps under the names the person gave them, everywhere: home screen, drawer and menus.
    val apps = remember(ownApps, loaded.appNames) { ownApps.named(loaded.appNames) }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    // The ones that are seen: a hidden app is in no drawer and on no home screen, but it is
    // still known, so a gesture can still open it and the list of hidden apps can show it.
    val hiddenApps = loaded.hiddenApps
    val shownApps = remember(apps, hiddenApps) { apps.shown(hiddenApps) }
    // Until the apps are read nothing is known to be missing, so nothing is hidden or dropped.
    val isInstalled = remember<InstalledCheck>(appsByKey) {
        if (appsByKey.isEmpty()) null else ({ key: String -> key in appsByKey })
    }

    // How icons look: the repository hands that to whatever draws an icon.
    LaunchedEffect(repository, loaded.iconSettings) {
        repository.iconSettings = loaded.iconSettings
    }

    val drawerSpec = loaded.drawerSpec
    val homeSpec = loaded.homeSpec
    val homeGrid = GridSize(homeSpec.columns, homeSpec.rows)
    val savedHome = loaded.homeLayout
    val homeLayout = remember(savedHome, homeGrid, isInstalled) {
        savedHome.visibleOn(homeGrid, isInstalled)
    }
    // The home screen opens on the page chosen as the first one.
    var homePage by rememberSaveable { mutableIntStateOf(homeLayout.startPage) }
    val gestures = loaded.gestures
    val motion = loaded.motion
    val drawerLook = loaded.drawerLook
    val labelLook = loaded.labelLook
    val storedWallpaperSetup = loaded.wallpaperSetup
    val wallpaperStudio = remember { WallpaperStudio.of(context) }
    val widgets = remember { KiriWidgets.of(context) }
    // The widgets other apps offer, and what shows them.
    val systemWidgets = remember { SystemWidgets.of(context) }
    // The wallpaper as Kiri reads it, and its blurred picture for glass to show through.
    val wallpaper = remember { WallpaperSource.of(context) }
    val backups = remember { Backups.of(context) }
    val systemBlur = rememberSystemBlurEnabled()
    val blurRadiusPx = with(density) { drawerLook.blurDp.dp.toPx() }
    val deepBlurPx = with(density) { DEEP_BLUR.toPx() }
    // Split into two effects so adjusting the drawer blur slider never re-blurs the deep settings image.
    LaunchedEffect(wallpaper, backdrop.screenSize, blurRadiusPx, refreshTick, wallpaper.changes) {
        backdrop.image = wallpaper.blurred(backdrop.screenSize, blurRadiusPx)
    }
    LaunchedEffect(wallpaper, backdrop.screenSize, deepBlurPx, refreshTick, wallpaper.changes) {
        backdrop.deepImage = wallpaper.blurred(backdrop.screenSize, deepBlurPx)
    }
    val askWallpaperPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch {
            backdrop.image = wallpaper.blurred(backdrop.screenSize, blurRadiusPx)
            backdrop.deepImage = wallpaper.blurred(backdrop.screenSize, deepBlurPx)
        }
    }
    val allowWallpaper: () -> Unit = {
        val permission = wallpaper.permissionToAsk
        if (permission != null) {
            runCatching { askWallpaperPermission.launch(permission) }
        } else {
            wallpaper.accessScreens().firstOrNull { screen -> runCatching { context.startActivity(screen) }.isSuccess }
        }
    }
    // The widget whose settings are open, by id, so it survives a rotation.
    var openWidgetId by rememberSaveable { mutableStateOf<String?>(null) }
    // The app whose icon is being chosen, by key.
    var iconPickerKey by rememberSaveable { mutableStateOf<String?>(null) }
    // What the long-press menu is about, if it is open.
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    // An app held in the drawer (non-observed holder), and the app carried from there to the home screen.
    val drawerLift = remember { arrayOfNulls<DrawerLift>(1) }
    var carry by remember { mutableStateOf<Carry?>(null) }
    val dropZone = remember { HomeDropZone() }
    // The room the home grid has, as last laid out: the grid settings keep within it.
    var homeRoom by remember { mutableStateOf<HomeRoom?>(null) }
    val viewOnScreen = remember { IntArray(2) }
    // The folder that is open on the home screen, or on its way.
    val folderOpening = remember { FolderOpening(scope) }
    SideEffect {
        folderOpening.stiffnessScale = motion.stiffnessScale
        folderOpening.openDistancePx = with(density) { FOLDER_OPEN_DISTANCE.toPx() }
        folderOpening.closeDistancePx = with(density) { FOLDER_CLOSE_DISTANCE.toPx() }
    }
    // Every page small, to arrange them, coming out of the home screen or on its way back.
    val pagesOpening = remember { PagesOpening(scope) }
    SideEffect { pagesOpening.stiffnessScale = motion.stiffnessScale }
    // The wallpaper stays still while the pages are out, and glides back with the home screen.
    val pagesWallpaper = remember(wallpaper, backdrop) {
        PagesWallpaper(current = { backdrop.travel }) { position, pages ->
            backdrop.travel = wallpaper.scrollTo(view.windowToken, position, pages)
        }
    }
    // Leaving the pages: the home screen is already on the page it comes back to.
    LaunchedEffect(pagesOpening.leavingTo) {
        val to = pagesOpening.leavingTo ?: return@LaunchedEffect
        homePage = to
        pagesWallpaper.glideBack(PagesOpening.CLOSE_STIFFNESS * motion.stiffnessScale)
    }
    LaunchedEffect(pagesOpening.shown) {
        if (pagesOpening.shown) pagesWallpaper.hold() else pagesWallpaper.release()
    }

    // Deliberately not saved: after the activity is recreated (a rotation), both counters start
    // over together, so the restored stack is kept.
    var lastHomeTick by remember { mutableIntStateOf(homeTick) }
    LaunchedEffect(homeTick) {
        if (lastHomeTick != homeTick) {
            lastHomeTick = homeTick
            stack = listOf(root)
            homePage = homeLayout.startPage
            menuTarget = null
            folderOpening.closeNow()
            pagesOpening.closeNow()
        }
    }
    val current = stack.last()
    val push: (Screen) -> Unit = { screen -> stack = stack + screen }
    // The backup being restored is not kept when Android ends the app: its screen closes.
    LaunchedEffect(current) { if (current == Screen.BACKUP_RESTORE && backups.opened == null) pop() }

    val launch: (AppEntry, Rect?) -> Unit = { app, iconBounds ->
        runCatching {
            val options = appOpenOptions(motion.appOpen, view, iconBounds)
            context.startActivity(repository.launchIntent(app), options?.toBundle())
        }
    }

    // [at] is where on screen the gesture was made, when it has a place.
    val perform: (LauncherAction, Offset?) -> Unit = { action, at ->
        when (action) {
            LauncherAction.None -> Unit
            LauncherAction.OpenDrawer -> push(Screen.DRAWER)
            LauncherAction.OpenSettings -> push(Screen.SETTINGS)
            LauncherAction.OpenNotifications -> context.expandNotifications()
            // Not on yet: Android's settings are where it is turned on.
            LauncherAction.LockScreen -> if (!KiriAccessibilityService.lockScreen()) {
                KiriAccessibilityService.openSettings(context)
            }
            LauncherAction.OpenWidgets -> push(Screen.WIDGETS)
            LauncherAction.OpenPages -> if (root == Screen.HOME) {
                menuTarget = null
                folderOpening.closeNow()
                pagesOpening.show(homePage)
            }
            // The menu opens where the finger is, or in the middle when nothing says where.
            LauncherAction.OpenHomeMenu -> {
                val screen = backdrop.screenSize
                menuTarget = MenuTarget.Home(Rect(at ?: Offset(screen.width / 2f, screen.height * 0.6f), Size.Zero))
            }
            is LauncherAction.LaunchApp -> appsByKey[action.appKey]?.let { launch(it, null) }
        }
    }

    // Still open while a settings screen is over it, so it is there again when that closes.
    val drawerOpen = Screen.DRAWER in stack
    val drawerMotion = remember { DrawerMotion(initiallyOpen = drawerOpen, scope = scope) }
    SideEffect { drawerMotion.stiffnessScale = motion.stiffnessScale }
    val flingPx = with(density) { 400.dp.toPx() }
    // Back, Home or an action changed whether the drawer is open: glide there.
    LaunchedEffect(drawerOpen) { drawerMotion.settle(drawerOpen) }

    // A finger let go of the drawer sheet: remember where it rests and let it glide there.
    val releaseDrawer: (Boolean, Float) -> Unit by rememberUpdatedState(
        { open: Boolean, velocityY: Float ->
            if (open && !drawerOpen) push(Screen.DRAWER)
            if (!open && current == Screen.DRAWER) pop()
            drawerMotion.settle(open, velocityY)
        },
    )
    // The swipes of the home screen, also for one made beside an open folder.
    val homeSwipes = HomeSwipes(
        onGesture = { gesture -> if (current == Screen.HOME) perform(gestures.actionFor(gesture), null) },
        // A swipe that opens the drawer carries the drawer along with the finger.
        onSwipeStart = { swipe -> current == Screen.HOME && gestures.actionFor(swipe) == LauncherAction.OpenDrawer },
        onSwipeMove = { alongPx -> drawerMotion.dragBy(-alongPx) },
        onSwipeEnd = { velocityAlongPx ->
            val open = drawerMotion.restsOpen(velocityUp = velocityAlongPx, wasOpen = false, flingPx = flingPx)
            releaseDrawer(open, -velocityAlongPx)
        },
    )
    val drawerPull = remember(drawerMotion, flingPx) {
        DrawerPull(
            isPulled = { drawerMotion.progress < 1f },
            dragBy = { deltaY -> drawerMotion.dragBy(deltaY) },
            release = { velocityY ->
                val open = drawerMotion.restsOpen(velocityUp = -velocityY, wasOpen = true, flingPx = flingPx)
                releaseDrawer(open, velocityY)
            },
        )
    }

    // Each time the launcher returns to the front, its screen comes back the chosen way.
    // `arrivedTick` is the return the animation belongs to: until the animation of a new
    // return has started, the screen is held at its starting point instead of flashing whole.
    val arrival = remember { Animatable(0f) }
    val arrivedTick = remember { mutableIntStateOf(-1) }
    LaunchedEffect(refreshTick) {
        arrival.snapTo(0f)
        arrivedTick.intValue = refreshTick
        if (motion.homeReturn == HomeReturnStyle.NONE) {
            arrival.snapTo(1f)
        } else {
            arrival.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow * motion.stiffnessScale,
                    visibilityThreshold = 0.001f,
                ),
            )
        }
    }

    // An app held in the drawer starts moving: the menu and the drawer make way for it.
    val beginCarry: (Carry) -> Unit by rememberUpdatedState(
        { active: Carry ->
            menuTarget = null
            carry = active
            if (current == Screen.DRAWER) pop()
        },
    )
    // Puts the app [appKey] into the app or folder [targetId] on the home screen: two apps make
    // a new folder, named after what they are when they are the same kind. Saves it, keeps the
    // other orientation in step, and returns the home screen as it will be.
    val mergeInto: (String, Long) -> HomeLayout? by rememberUpdatedState(
        { appKey: String, targetId: Long ->
            val target = homeLayout.item(targetId)?.content
            if (target !is ItemContent.App && target !is ItemContent.Folder) {
                null
            } else {
                val newId = if (target is ItemContent.App) newFolderId() else ""
                val newName = if (target is ItemContent.App) {
                    newFolderName(context, repository, appsByKey[target.appKey], appsByKey[appKey])
                } else {
                    ""
                }
                val left = homeLayout.folderOf(appKey)?.id
                val joined = (target as? ItemContent.Folder)?.id ?: newId
                val result = homeLayout.mergeApp(appKey, targetId, newId, newName)
                if (result == homeLayout) {
                    null
                } else {
                    scope.launch {
                        settings.editHome(homeProfile, isInstalled, folders = listOfNotNull(left, joined)) { layout, _ ->
                            layout.mergeApp(appKey, targetId, newId, newName)
                        }
                    }
                    result
                }
            }
        },
    )
    // The finger let go of it over the home screen: it lands on the cell underneath, or inside
    // the app or folder it was let go right on.
    val dropCarry: (Carry, Offset) -> Unit by rememberUpdatedState(
        { active: Carry, at: Offset ->
            val key = active.app.key
            val content = ItemContent.App(key)
            val onto = dropZone.cellAt(active.center)?.let { homeLayout.itemAt(it) }?.takeIf { item ->
                item.span == CellSpan.Single &&
                    homeLayout.canMerge(null, item.id) &&
                    (active.center - dropZone.centerOf(item.position)).getDistance() <
                    MERGE_REACH * dropZone.cellSize.minDimension
            }
            // Let go on the folder it came out of, it simply goes back in.
            val goingBack = (onto?.content as? ItemContent.Folder)?.appKeys?.contains(key) == true
            val merged = if (onto != null && !goingBack) mergeInto(key, onto.id) else null
            val inside = onto != null && (merged != null || goingBack)
            val cell = if (inside) null else dropZone.cellAt(at)
            // An app taken out of a folder leaves it in the other orientation too.
            val leaving = homeLayout.folderOf(key)?.id
            val next = if (cell != null) homeLayout.place(content, cell, homeGrid) else homeLayout
            val placed = next.items.firstOrNull { it.content == content }?.position
            val changed = cell != null && placed != null && next != homeLayout
            if (cell != null && changed) {
                scope.launch {
                    if (leaving != null) {
                        settings.editHome(homeProfile, isInstalled, folders = listOf(leaving)) { layout, grid ->
                            layout.place(content, cell, grid)
                        }
                    } else {
                        settings.placeOnHome(homeProfile, content, cell, isInstalled)
                    }
                }
            }
            scope.launch {
                val landOn = onto?.takeIf { inside }
                if (landOn != null) {
                    active.landing.snapTo(active.center)
                    active.into = true
                    active.landed = true
                    active.landing.animateTo(
                        targetValue = dropZone.centerOf(landOn.position),
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = Spring.StiffnessMediumLow * motion.stiffnessScale,
                            visibilityThreshold = Offset(0.5f, 0.5f),
                        ),
                    )
                    if (carry === active) carry = null
                    return@launch
                }
                val from = active.center
                active.landing.snapTo(from)
                active.landed = true
                if (placed != null && changed) {
                    homePage = placed.page
                    active.landing.animateTo(
                        targetValue = dropZone.centerOf(placed),
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = Spring.StiffnessMediumLow * motion.stiffnessScale,
                            visibilityThreshold = Offset(0.5f, 0.5f),
                        ),
                    )
                }
                if (carry === active) carry = null
            }
            Unit
        },
    )
    // The widgets on the home screen, in the order they are met there, with what to call them.
    val placedWidgets = remember(homeLayout, systemWidgets.revision) {
        homeLayout.items
            .sortedWith(compareBy({ it.position.page }, { it.position.row }, { it.position.column }))
            .mapNotNull { item ->
                widgetFacts(context, item.content, widgets, systemWidgets)?.let { PlacedWidget(item, it) }
            }
    }
    // How large the cells of the home screen are: as laid out, or worked out before that.
    // A widget meets the icons: as wide as theirs from the first to the last, and as tall as
    // theirs with their names, from the top of the first row to the bottom of the last.
    val homeCells: () -> HomeCells = {
        val scale = density.density
        val cell = dropZone.cellSize
        val pad = HomeGridGeometry.CELL_PAD_DP
        if (cell.width > 0f && cell.height > 0f && dropZone.columns == homeGrid.columns && dropZone.rows == homeGrid.rows) {
            HomeCells(
                grid = homeGrid,
                cellWidthDp = cell.width / scale,
                cellHeightDp = (cell.height / scale - 2 * pad).coerceAtLeast(1f),
                gapDp = dropZone.gapXPx / scale,
                rowGapDp = dropZone.gapYPx / scale + 2 * pad,
            )
        } else {
            val names = if (homeSpec.showLabels) NAMES_GUESS_DP else 0f
            val room = HomeRoom(configuration.screenWidthDp.toFloat(), configuration.screenHeightDp.toFloat(), names)
            val geometry = HomeGridGeometry.of(homeSpec, room)
            HomeCells(
                grid = homeGrid,
                cellWidthDp = geometry.cellWidthDp,
                cellHeightDp = (geometry.cellHeightDp - 2 * pad).coerceAtLeast(1f),
                gapDp = geometry.gapXDp,
                rowGapDp = geometry.gapYDp + 2 * pad,
            )
        }
    }
    // A widget goes to the page the person was on, and the home screen comes back to show it.
    val addWidget: (ItemContent, CellSpan) -> Unit = { content, span ->
        scope.launch {
            val placed = settings.addToHome(homeProfile, content, homePage, isInstalled, span)
            stack = listOf(root)
            placed?.let { homePage = it.page }
            // Found no place: Android is not left remembering a copy that is nowhere.
            if (placed == null && content is ItemContent.AppWidget) systemWidgets.delete(content.appWidgetId)
        }
    }
    val widgetSetup = rememberWidgetSetup(
        system = systemWidgets,
        // The looks chosen for widgets that are gone go with them.
        // The widgets a restore may yet be undone to are kept too.
        placed = { settings.appWidgetIdsInUse()?.let { it + backups.widgetsKeptForUndo() }?.also { settings.keepWidgetLooksOf(it) } },
    ) { appWidgetId, info, replacing ->
        if (replacing != null) {
            // A widget Android forgot (after a restore) takes its new number where it stood.
            scope.launch { settings.renumberAppWidget(replacing, appWidgetId, info.provider.flattenToString()) }
        } else {
            addWidget(
                ItemContent.AppWidget(appWidgetId, info.provider.flattenToString()),
                info.startSpan(homeCells(), density.density),
            )
        }
    }
    val placeAgain: (Int, AppWidgetProviderInfo) -> Unit = { old, info -> widgetSetup.placeAgain(old, info) }
    // A widget of another app that leaves the home screen is also handed back to Android.
    val removeItem: (HomeItem) -> Unit = { item ->
        scope.launch {
            settings.removeFromHome(homeProfile, item.content, isInstalled)
            val gone = (item.content as? ItemContent.AppWidget)?.appWidgetId ?: return@launch
            // Only once no arrangement shows it any more; when that cannot be told, it stays.
            val inUse = settings.appWidgetIdsInUse() ?: return@launch
            // Nor while a restore that can be undone may bring it back.
            if (gone !in inUse && gone !in backups.widgetsKeptForUndo()) {
                systemWidgets.delete(gone)
                settings.keepWidgetLooksOf(inUse)
            }
        }
    }
    // Opens what a widget has to adjust: Kiri's own screen for it, or the app's.
    val adjustWidget: (HomeItem) -> Unit = { item ->
        when (val content = item.content) {
            is ItemContent.Widget -> {
                openWidgetId = content.widgetId
                push(Screen.WIDGET_SETTINGS)
            }

            is ItemContent.AppWidget -> widgetSetup.adjust(content.appWidgetId)
            is ItemContent.App, is ItemContent.Folder -> Unit
        }
    }
    val incomingPointer: (() -> Offset)? = remember(carry) { carry?.let { active -> { active.pointer } } }

    // Panels, widgets and menus are made of the same tile of glass as the icons.
    val iconLook = loaded.iconSettings.look
    // What the TV's home screen does: the same as anywhere else in Kiri, by remote.
    val launchNow by rememberUpdatedState(launch)
    val tvActions = remember(repository, settings, tvInputs) {
        TvHomeActions(
            launch = { app -> launchNow(app, null) },
            watch = { input ->
                tvInputs?.watchIntents(input).orEmpty().any { intent -> runCatching { context.startActivity(intent) }.isSuccess }
            },
            openSettings = { push(Screen.SETTINGS) },
            editHome = { start, change -> scope.launch { settings.editTvHome(start, change) } },
            setBanner = { item, banner -> scope.launch { settings.setTvBanner(item, banner) } },
            setAppName = { app, name -> scope.launch { settings.setAppName(app.key, name) } },
            setInputName = { input, name -> scope.launch { settings.setTvInputName(input.id, name) } },
            hide = { app -> scope.launch { settings.setAppHidden(app.key, true) } },
            info = { app -> runCatching { context.startActivity(repository.infoIntent(app)) } },
            store = { app -> repository.storeIntents(app).firstOrNull { runCatching { context.startActivity(it) }.isSuccess } },
            uninstall = { app -> runCatching { context.startActivity(repository.uninstallIntent(app)) } },
            canUninstall = { app -> repository.canUninstall(app) },
            editWidgets = { change -> scope.launch { settings.editTvWidgets(change) } },
            openWidgetSettings = { kind ->
                if (kind == TvWidgetKind.MARKET) {
                    openWidgetId = MarketWidgetSpec.SPEC.id
                    push(Screen.WIDGET_SETTINGS)
                }
            },
        )
    }
    // Stocks and currencies, as the TV's home screen draws them itself.
    val tvMarketRows: @Composable () -> List<MarketRow>? = {
        (widgets.byId(MarketWidgetSpec.SPEC.id) as? MarketWidget)?.liveRows()
    }
    val glassTile = remember(iconLook) { GlassBevel.Tile.of(iconLook) }
    LaunchedEffect(glassTile, density.density) {
        GlassPanes.prewarm(density.density, glassTile)
    }

    KiriTheme(labelLook = labelLook, glassTile = glassTile) {
        val settingsOpen = current != Screen.HOME && current != Screen.DRAWER && current != Screen.TV_HOME
        // The list keeps its place between its own screens, and starts from the top the next
        // time the settings are opened.
        LaunchedEffect(settingsOpen) {
            if (!settingsOpen) settingsIndexScroll.scrollTo(0)
        }
        val settingsVisibility = remember { MutableTransitionState(settingsOpen) }
        settingsVisibility.targetState = settingsOpen
        // Once the settings fully cover the screen, Home and Drawer stop drawing and taking focus
        // while staying composed in memory so returning is instant and preserves scroll position.
        val covered = settingsVisibility.currentState && settingsVisibility.targetState
        // The same, for what is only asked while drawing or moving the focus: no recomposition.
        val coveredNow = remember(settingsVisibility) {
            { settingsVisibility.currentState && settingsVisibility.targetState }
        }
        // Covered, Home and Drawer keep showing what they showed until then, so that a + or −
        // in the settings redraws only its preview and not every icon and name hidden behind
        // it. They catch up once, as the settings start to go.
        val behindHolder = remember { arrayOfNulls<Behind>(1) }
        val behind = behindHolder[0]?.takeIf { covered }
            ?: Behind(homeLayout, homeSpec, drawerSpec, drawerLook, labelLook, glassTile, loaded.iconSettings, loaded.folderLook)
                .also { behindHolder[0] = it }
        // The settings screen on show. It outlives `current` so the last one can fade out.
        val lastSettings = remember { arrayOf(Screen.SETTINGS) }
        val shownSettings = when {
            settingsOpen -> current.also { lastSettings[0] = it }
            settingsVisibility.currentState -> lastSettings[0]
            else -> Screen.SETTINGS.also { lastSettings[0] = it }
        }
        val pace = motion.durationScale

        // The wallpaper blurs where the drawer is over it. When Kiri has its own blurred picture
        // of the wallpaper, the sheet of the drawer carries it (further down). When it has none,
        // as with a live wallpaper, the device is asked to blur, where it can: in steps, so
        // that the window is not asked for a new blur at every single frame.
        WallpaperBlur {
            val step = (drawerMotion.progress * BLUR_STEPS).roundToInt() / BLUR_STEPS
            if (covered || !systemBlur || backdrop.image != null) 0 else (blurRadiusPx * step).roundToInt()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    drawerMotion.travelPx = it.height.toFloat().coerceAtLeast(1f)
                    backdrop.screenSize = it
                }
                .onGloballyPositioned {
                    view.getLocationOnScreen(viewOnScreen)
                    val origin = Offset(viewOnScreen[0].toFloat(), viewOnScreen[1].toFloat())
                    if (backdrop.windowOrigin != origin) backdrop.windowOrigin = origin
                }
                .onPreviewKeyEvent { event ->
                    // The Menu key of a remote or keyboard opens settings.
                    if (event.key == Key.Menu && event.type == KeyEventType.KeyUp && !settingsOpen) {
                        push(Screen.SETTINGS)
                        true
                    } else {
                        false
                    }
                }
                // Carrying an app out of the drawer. This looks at the touches before anything
                // else does and, once the app moves, keeps them so that nothing else reacts.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        drawerLift[0] = null
                        var previous = down.position
                        var liftedAt: Offset? = null
                        var active: Carry? = null
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val lifted = drawerLift[0]
                                if (lifted == null) {
                                    if (!change.pressed) break
                                    previous = change.position
                                    continue
                                }
                                val start = liftedAt ?: previous.also { liftedAt = it }
                                var carried = active
                                if (carried == null) {
                                    if (!change.pressed) break
                                    val moved = (change.position - start).getDistance()
                                    if (moved <= viewConfiguration.touchSlop) continue
                                    carried = Carry(lifted.app, lifted.iconBounds.center, start)
                                    active = carried
                                    beginCarry(carried)
                                }
                                change.consume()
                                if (change.pressed) {
                                    carried.pointer = change.position
                                } else {
                                    dropCarry(carried, change.position)
                                    active = null
                                    break
                                }
                            }
                        } finally {
                            drawerLift[0] = null
                            // The touch was taken away before a drop: the app goes back.
                            val abandoned = active
                            if (abandoned != null && carry === abandoned) carry = null
                        }
                    }
                },
        ) {
            CompositionLocalProvider(
                LocalLabelLook provides behind.labelLook,
                LocalGlassTile provides behind.glassTile,
                LocalIconSettings provides behind.iconSettings,
                LocalFolderLook provides behind.folderLook,
                LocalFolderOpening provides folderOpening,
                // What is under the settings takes no focus: a remote moves only among them.
                LocalOutOfSight provides coveredNow,
                LocalWidgetLooks provides loaded.widgetLooks,
                LocalWidgetPlaceAgain provides placeAgain,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent { if (!covered) drawContent() }
                        .graphicsLayer {
                            if (covered) {
                                alpha = 0f
                            } else {
                                val arrived = if (arrivedTick.intValue == refreshTick) arrival.value else 0f
                                homeReturning(motion.homeReturn, arrived)
                            }
                        }
                        .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier),
                ) {
                    if (root == Screen.HOME) {
                        HomeSurface(
                            layout = behind.homeLayout,
                            appsByKey = appsByKey,
                            spec = behind.homeSpec,
                            repository = repository,
                            interactive = true,
                            page = homePage,
                            onPageChange = { homePage = it },
                            onLaunch = launch,
                            onItemLongPress = { item, anchor ->
                                menuTarget = when (val content = item.content) {
                                    is ItemContent.App -> appsByKey[content.appKey]?.let { MenuTarget.App(it, anchor) }
                                    is ItemContent.Widget, is ItemContent.AppWidget -> MenuTarget.Widget(item.id, anchor)
                                    is ItemContent.Folder -> MenuTarget.Folder(item.id, anchor)
                                }
                            },
                            // The menu opened by the long press makes way once the item is carried.
                            onDragStart = { menuTarget = null },
                            onMoveItem = { id, to ->
                                scope.launch { settings.moveOnHome(homeProfile, id, to, isInstalled) }
                            },
                            // Only the home screen itself listens: not while the drawer is over it.
                            onGesture = { gesture, at ->
                                if (current == Screen.HOME) perform(gestures.actionFor(gesture), at)
                            },
                            onSwipeStart = homeSwipes.onSwipeStart,
                            onSwipeMove = homeSwipes.onSwipeMove,
                            onSwipeEnd = homeSwipes.onSwipeEnd,
                            emptyHint = { homeHint(gestures, appsByKey) },
                            // The home screen steps back as the drawer comes over it. A folder
                            // opening over it leaves every icon where it is and as large.
                            modifier = Modifier.graphicsLayer {
                                homeBehindDrawer(motion.drawer, drawerMotion.progress)
                                // The pages draw it themselves while they are out.
                                if (pagesOpening.covering) alpha = 0f
                            },
                            widgets = widgets,
                            dropZone = dropZone,
                            incomingPointer = incomingPointer,
                            // A wallpaper that scrolls, or a live one that reacts, follows the pages.
                            // Glass is told too, to show the part of it that is now behind it.
                            onPagePosition = { position, pages ->
                                pagesWallpaper.report(position, pages, covered = pagesOpening.shown)
                            },
                            onMergeItem = { liftedId, targetId ->
                                (homeLayout.item(liftedId)?.content as? ItemContent.App)?.let { mergeInto(it.appKey, targetId) }
                            },
                            jumpToPage = { pagesOpening.shown },
                            onRoom = { homeRoom = it },
                        )
                    }
                    if (root == Screen.TV_HOME) {
                        TvHome(
                            apps = shownApps,
                            inputs = inputs,
                            tvAppKeys = tvAppKeys,
                            tv = loaded.tv,
                            repository = repository,
                            active = current == Screen.TV_HOME,
                            homeTick = homeTick,
                            actions = tvActions,
                            marketRows = tvMarketRows,
                        )
                    }
                    // The drawer is always there, waiting out of sight, so that opening it only
                    // has to move it. Visibility semantics and focus are isolated so pulling the
                    // drawer up never recomposes KiriApp or HomeSurface on frame 1.
                    if (root != Screen.TV_HOME) DrawerVisibilityGate(
                        isShown = { drawerMotion.progress > 0f },
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent { if (drawerMotion.progress > 0f) drawContent() }
                            .graphicsLayer { drawerSheet(motion.drawer, drawerMotion.progress) },
                    ) {
                        DrawerSurface(
                            apps = shownApps,
                            spec = behind.drawerSpec,
                            look = behind.drawerLook,
                            repository = repository,
                            interactive = true,
                            onLaunch = launch,
                            // On a TV the drawer is the first screen: there is nothing to pull it
                            // down to, and no home screen to carry apps to yet.
                            pull = if (root == Screen.HOME) drawerPull else null,
                            settle = { drawerMotion.progress },
                            // The sheet is what blurs the wallpaper: the blurred picture is part
                            // of it, cut to it, and stays put on the screen while the sheet moves.
                            frost = {
                                val picture = backdrop.image
                                if (picture != null) {
                                    clipRect {
                                        heldStill(motion.drawer, drawerMotion.progress) {
                                            drawWallpaper(picture, backdrop.travel, size.width, size.height)
                                        }
                                    }
                                }
                            },
                            onLongPress = { app, iconBounds ->
                                menuTarget = MenuTarget.App(app, iconBounds)
                                if (root == Screen.HOME) drawerLift[0] = DrawerLift(app, iconBounds)
                            },
                        )
                    }
                }
            }

            // The open folder, over the home screen.
            FolderLayer(folderOpening) { shown ->
                val folder = homeLayout.folder(shown.folderId)
                if (folder == null || current != Screen.HOME) {
                    LaunchedEffect(shown) { folderOpening.closeNow() }
                } else {
                    CompositionLocalProvider(LocalFolderLook provides loaded.folderLook) {
                        val folderId = folder.id
                        val editFolder: ((HomeLayout, GridSize) -> HomeLayout) -> Unit = { change ->
                            scope.launch { settings.editHome(homeProfile, isInstalled, folders = listOf(folderId), change = change) }
                        }
                        // A swipe beside it takes the home screen back as the drawer comes, and the
                        // folder closing there goes along with the rest of it.
                        Box(Modifier.fillMaxSize().graphicsLayer { homeBehindDrawer(motion.drawer, drawerMotion.progress) }) {
                            FolderOverlay(
                                opening = folderOpening,
                                folder = folder,
                                appsByKey = appsByKey,
                                spec = homeSpec,
                                repository = repository,
                                onLaunch = launch,
                                onRename = { name -> editFolder { layout, _ -> layout.renameFolder(folderId, name) } },
                                onReorder = { order -> editFolder { layout, _ -> layout.reorderFolder(folderId, order) } },
                                onCarryOut = { app, bounds -> drawerLift[0] = DrawerLift(app, bounds) },
                                // The same menu as an app has on the home screen, closed once it is carried.
                                onAppLongPress = { app, bounds -> menuTarget = MenuTarget.App(app, bounds) },
                                onAppDragStart = { menuTarget = null },
                                // A swipe beside the folder closes it and does what it does on the home screen.
                                homeSwipes = homeSwipes,
                            )
                        }
                    }
                }
            }

            // Every page small, over the home screen.
            if (pagesOpening.shown && root == Screen.HOME) {
                LaunchedEffect(current) { if (current != Screen.HOME) pagesOpening.closeNow() }
                BackHandler(enabled = pagesOpening.open) { pagesOpening.close() }
                // The small pages are drawn as the home screen draws itself.
                CompositionLocalProvider(
                    LocalIconSettings provides loaded.iconSettings,
                    LocalFolderLook provides loaded.folderLook,
                    LocalFolderOpening provides folderOpening,
                    LocalWidgetLooks provides loaded.widgetLooks,
                ) {
                    PagesOverlay(
                        opening = pagesOpening,
                        layout = homeLayout,
                        appsByKey = appsByKey,
                        spec = homeSpec,
                        repository = repository,
                        widgets = widgets,
                        onEdit = { change, gone ->
                            scope.launch {
                                settings.editHome(homeProfile, isInstalled) { layout, _ -> change(layout) }
                                // The widgets of other apps on a page taken away go back to Android.
                                val goneWidgets = gone.mapNotNull { (it.content as? ItemContent.AppWidget)?.appWidgetId }
                                if (goneWidgets.isEmpty()) return@launch
                                val inUse = settings.appWidgetIdsInUse() ?: return@launch
                                goneWidgets.filter { it !in inUse }.forEach { systemWidgets.delete(it) }
                                settings.keepWidgetLooksOf(inUse)
                            }
                        },
                    )
                }
            }

            // The app carried out of the drawer or a folder, above everything it flies over.
            carry?.let { active ->
                // As an app sits on the home screen: its cell, with the room beside it for its name.
                val cell = dropZone.cellSize.let { Size(maxOf(it.width, it.width + dropZone.gapXPx - with(density) { HomeGridGeometry.LABEL_INSET_DP.dp.toPx() }), it.height) }
                val raise = remember(active) { Animatable(1f) }
                LaunchedEffect(active, active.landed) {
                    val size = when {
                        !active.landed -> 1.1f
                        active.into -> 0.45f
                        else -> 1f
                    }
                    raise.animateTo(size, spring(stiffness = Spring.StiffnessMediumLow))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset {
                            val center = active.center
                            IntOffset(
                                x = (center.x - cell.width / 2f).roundToInt(),
                                y = (center.y - cell.height / 2f).roundToInt(),
                            )
                        }
                        .size(with(density) { cell.width.toDp() }, with(density) { cell.height.toDp() })
                        .graphicsLayer {
                            scaleX = raise.value
                            scaleY = raise.value
                        },
                ) {
                    AppCell(
                        app = active.app,
                        spec = homeSpec,
                        labelStyle = homeLabelStyle(),
                        labelHeight = 0.dp,
                        repository = repository,
                        interactive = false,
                        onClick = {},
                        modifier = Modifier.wrapContentHeight(unbounded = true),
                    )
                }
            }

            AnimatedVisibility(
                visibleState = settingsVisibility,
                enter = fadeIn(tween((200 * pace).roundToInt())) +
                    slideInVertically(tween((280 * pace).roundToInt())) { it / 12 },
                exit = fadeOut(tween((160 * pace).roundToInt())) +
                    slideOutVertically(tween((220 * pace).roundToInt())) { it / 12 },
            ) {
                // All settings lie on one sheet of glass. It stays put while one screen fades
                // into the next, so the sharp wallpaper never flashes between them. Also blocks
                // pointer events from leaking to the hidden Home/Drawer kept alive underneath.
                Box(
                    Modifier
                        .fillMaxSize()
                        .kiriSheet()
                        .pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
                        },
                ) {
                    AnimatedContent(
                        targetState = shownSettings,
                        transitionSpec = {
                            // Going deeper comes in from the right; going back, from the left.
                            val back = targetState == Screen.SETTINGS ||
                                (targetState == Screen.WIDGETS && initialState == Screen.WIDGET_SETTINGS) ||
                                (targetState == Screen.BACKUP && initialState == Screen.BACKUP_RESTORE) ||
                                (targetState == Screen.TV_SCREEN_SETTINGS && initialState == Screen.TV_PICTURE)
                            val direction = if (back) -1 else 1
                            val slide = tween<IntOffset>((280 * pace).roundToInt())
                            val arriving = fadeIn(tween((220 * pace).roundToInt())) +
                                slideInHorizontally(slide) { direction * it / 5 }
                            val leaving = fadeOut(tween((140 * pace).roundToInt())) +
                                slideOutHorizontally(slide) { -direction * it / 5 }
                            arriving togetherWith leaving
                        },
                        label = "settings",
                    ) { screen ->
                        when (screen) {
                            Screen.SETTINGS -> SettingsIndexScreen(
                                onBack = pop,
                                onOpenHomeGrid = if (root == Screen.HOME) { { push(Screen.HOME_GRID_SETTINGS) } } else null,
                                onOpenDrawerGrid = if (root == Screen.TV_HOME) null else { { push(Screen.DRAWER_GRID_SETTINGS) } },
                                onOpenGestures = if (root == Screen.HOME) { { push(Screen.GESTURE_SETTINGS) } } else null,
                                onOpenDrawerLook = if (root == Screen.TV_HOME) null else { { push(Screen.DRAWER_LOOK_SETTINGS) } },
                                onOpenIcons = { push(Screen.ICON_LOOK_SETTINGS) },
                                onOpenMotion = { push(Screen.MOTION_SETTINGS) },
                                onOpenWallpaper = { push(Screen.WALLPAPER_SETTINGS) },
                                onOpenTypography = { push(Screen.TYPOGRAPHY_SETTINGS) },
                                onOpenWidgets = if (root == Screen.HOME) { { push(Screen.WIDGETS) } } else null,
                                onOpenFolders = if (root == Screen.HOME) { { push(Screen.FOLDER_SETTINGS) } } else null,
                                onOpenHiddenApps = { push(Screen.HIDDEN_APPS) },
                                hiddenApps = apps.size - shownApps.size,
                                onOpenBackup = { push(Screen.BACKUP) },
                                lastBackupAt = backups.last?.at,
                                scroll = settingsIndexScroll,
                                onOpenAppIcon = { push(Screen.APP_ICON_SETTINGS) },
                                onOpenTvScreen = if (root == Screen.TV_HOME) { { push(Screen.TV_SCREEN_SETTINGS) } } else null,
                            )

                            Screen.TV_SCREEN_SETTINGS -> TvScreenSettings(
                                look = loaded.tv.look,
                                onChange = { look -> scope.launch { settings.setTvLook(look) } },
                                widgets = loaded.tv.widgets,
                                onWidgets = { next -> scope.launch { settings.setTvWidgets(next) } },
                                onPickPicture = { push(Screen.TV_PICTURE) },
                                onBack = pop,
                                preview = { modifier ->
                                    TvHomePreview(
                                        apps = shownApps,
                                        inputs = inputs,
                                        tv = loaded.tv,
                                        repository = repository,
                                        marketRows = tvMarketRows,
                                        modifier = modifier,
                                    )
                                },
                            )

                            // A picture chosen there is the wallpaper from then on.
                            Screen.TV_PICTURE -> TvPictureScreen(
                                onPicked = {
                                    scope.launch { settings.setTvLook(loaded.tv.look.copy(wallpaper = TvWallpaper.OWN)) }
                                    pop()
                                },
                                onBack = pop,
                            )

                            Screen.APP_ICON_SETTINGS -> AppIconScreen(onBack = pop)

                            Screen.GESTURE_SETTINGS -> GestureSettingsScreen(
                                gestures = gestures,
                                appsByKey = appsByKey,
                                onChange = { gesture, action ->
                                    scope.launch { settings.setGesture(gesture, action) }
                                    // Chosen to lock the screen: straight to where that is turned on.
                                    if (action == LauncherAction.LockScreen && !KiriAccessibilityService.isOn(context)) {
                                        KiriAccessibilityService.openSettings(context)
                                    }
                                },
                                onBack = pop,
                                lockReady = remember(refreshTick, gestures) { KiriAccessibilityService.isOn(context) },
                                onTurnOnLock = { KiriAccessibilityService.openSettings(context) },
                            )

                            Screen.MOTION_SETTINGS -> MotionSettingsScreen(
                                motion = motion,
                                onChange = { next -> scope.launch { settings.setMotion(next) } },
                                onBack = pop,
                            )

                            Screen.DRAWER_LOOK_SETTINGS -> DrawerLookScreen(
                                look = drawerLook,
                                onChange = { next -> scope.launch { settings.setDrawerLook(next) } },
                                onBack = pop,
                                systemBlur = systemBlur,
                                reading = wallpaper.reading,
                                onAllowWallpaper = allowWallpaper,
                                preview = { draft ->
                                    WallpaperStandIn(Modifier.fillMaxSize(), blurred = true) {
                                        DrawerSurface(
                                            apps = shownApps,
                                            spec = drawerSpec,
                                            look = draft,
                                            repository = repository,
                                            interactive = false,
                                            onLaunch = { _, _ -> },
                                        )
                                    }
                                },
                            )

                            Screen.ICON_LOOK_SETTINGS -> {
                                val icons = loaded.iconSettings
                                IconLookScreen(
                                    look = icons.look,
                                    onChange = { next -> scope.launch { settings.setIconLook(next) } },
                                    onBack = pop,
                                    ownSizes = (icons.artPercents.keys + icons.grounds.keys).size,
                                    onClearOwnSizes = { scope.launch { settings.clearIconArts() } },
                                    preview = {
                                        // The apps of the home screen first: they are the ones seen most.
                                        val samples = remember(homeLayout, shownApps) {
                                            val onHome = homeLayout.items.mapNotNull { item ->
                                                (item.content as? ItemContent.App)?.let { appsByKey[it.appKey] }
                                            }
                                            (onHome + shownApps).distinctBy { it.key }.take(ICON_SAMPLES)
                                        }
                                        IconSamples(
                                            apps = samples,
                                            spec = homeSpec.copy(showLabels = false),
                                            repository = repository,
                                            labelStyle = homeLabelStyle(),
                                        )
                                    },
                                )
                            }

                            Screen.TYPOGRAPHY_SETTINGS -> TypographyScreen(
                                look = labelLook,
                                onChange = { next -> scope.launch { settings.setLabelLook(next) } },
                                onBack = pop,
                                preview = {
                                    // Real apps with their names, the ones of the home screen first.
                                    val samples = remember(homeLayout, shownApps) {
                                        val onHome = homeLayout.items.mapNotNull { item ->
                                            (item.content as? ItemContent.App)?.let { appsByKey[it.appKey] }
                                        }
                                        (onHome + shownApps).distinctBy { it.key }.take(ICON_SAMPLES)
                                    }
                                    IconSamples(
                                        apps = samples,
                                        spec = homeSpec.copy(showLabels = true),
                                        repository = repository,
                                        labelStyle = homeLabelStyle(),
                                    )
                                },
                            )

                            Screen.WALLPAPER_SETTINGS -> WallpaperScreen(
                                setup = storedWallpaperSetup,
                                studio = wallpaperStudio,
                                screen = backdrop.screenSize,
                                onChange = { next -> scope.launch { settings.setWallpaperSetup(next) } },
                                // Glass shows the new wallpaper from now on.
                                onApplied = { wallpaper.changed() },
                                onBack = pop,
                            )

                            Screen.FOLDER_SETTINGS -> FolderSettingsScreen(
                                look = loaded.folderLook,
                                onChange = { next -> scope.launch { settings.setFolderLook(next) } },
                                onBack = pop,
                                preview = { draft ->
                                    FolderSamples(
                                        layout = homeLayout,
                                        apps = shownApps,
                                        appsByKey = appsByKey,
                                        spec = homeSpec,
                                        look = draft,
                                        repository = repository,
                                    )
                                },
                            )

                            Screen.WIDGET_SETTINGS -> openWidgetId?.let(widgets::byId)?.Settings(onBack = pop)

                            Screen.HIDDEN_APPS -> HiddenAppsScreen(
                                apps = apps,
                                hidden = hiddenApps,
                                repository = repository,
                                onHiddenChange = { app, hidden -> scope.launch { settings.setAppHidden(app.key, hidden) } },
                                onBack = pop,
                            )

                            Screen.ICON_PICKER -> iconPickerKey?.let { appsByKey[it] }?.let { app ->
                                val icons = loaded.iconSettings
                                IconPickerScreen(
                                    app = app,
                                    apps = apps,
                                    homeSpec = homeSpec,
                                    labelStyle = homeLabelStyle(),
                                    current = icons.choices[app.key],
                                    artPercent = icons.artPercents[app.key],
                                    sharedArtPercent = icons.look.artPercent,
                                    ground = icons.grounds[app.key],
                                    repository = repository,
                                    onChoose = { choice ->
                                        scope.launch { settings.setIconChoice(app.key, choice) }
                                        pop()
                                    },
                                    onArtChange = { percent -> scope.launch { settings.setIconArt(app.key, percent) } },
                                    onGroundChange = { ground -> scope.launch { settings.setIconGround(app.key, ground) } },
                                    onRename = { name -> scope.launch { settings.setAppName(app.key, name) } },
                                    onBack = pop,
                                )
                            }

                            Screen.HOME_GRID_SETTINGS -> GridSettingsScreen(
                                profile = homeProfile,
                                spec = homeSpec,
                                onSpecChange = { next -> scope.launch { settings.setGridSpec(homeProfile, next) } },
                                onBack = pop,
                                room = homeRoom,
                                preview = { draft ->
                                    // An empty home screen would show nothing to adjust, so the
                                    // preview then borrows the first apps of the drawer.
                                    val shown = remember(savedHome, draft.columns, draft.rows, isInstalled, shownApps) {
                                        val grid = GridSize(draft.columns, draft.rows)
                                        savedHome.visibleOn(grid, isInstalled).takeIf { it.items.isNotEmpty() }
                                            ?: shownApps.take(grid.columns * grid.rows).fold(HomeLayout()) { layout, app ->
                                                layout.add(ItemContent.App(app.key), grid)
                                            }
                                    }
                                    WallpaperStandIn(Modifier.fillMaxSize()) {
                                        CompositionLocalProvider(LocalFolderLook provides loaded.folderLook) {
                                            HomeSurface(
                                                layout = shown,
                                                appsByKey = appsByKey,
                                                spec = draft,
                                                repository = repository,
                                                interactive = false,
                                                page = homePage,
                                                onPageChange = {},
                                                onLaunch = { _, _ -> },
                                                onItemLongPress = { _, _ -> },
                                                onDragStart = {},
                                                onMoveItem = { _, _ -> },
                                                onGesture = { _, _ -> },
                                                onSwipeStart = { false },
                                                onSwipeMove = {},
                                                onSwipeEnd = {},
                                                emptyHint = { "" },
                                                widgets = widgets,
                                                onRoom = { homeRoom = it },
                                            )
                                        }
                                    }
                                },
                            )

                            Screen.DRAWER_GRID_SETTINGS -> GridSettingsScreen(
                                profile = drawerProfile,
                                spec = drawerSpec,
                                onSpecChange = { next -> scope.launch { settings.setGridSpec(drawerProfile, next) } },
                                onBack = pop,
                                preview = { draft ->
                                    WallpaperStandIn(Modifier.fillMaxSize(), blurred = true) {
                                        DrawerSurface(
                                            apps = shownApps,
                                            spec = draft,
                                            look = drawerLook,
                                            repository = repository,
                                            interactive = false,
                                            onLaunch = { _, _ -> },
                                        )
                                    }
                                },
                            )

                            Screen.WIDGETS -> WidgetsScreen(
                                placed = placedWidgets,
                                apps = apps,
                                repository = repository,
                                kiri = widgets,
                                system = systemWidgets,
                                cells = homeCells,
                                onAdd = { offer ->
                                    val own = offer.kiri
                                    val theirs = offer.system
                                    if (own != null) {
                                        addWidget(ItemContent.Widget(own.spec.id), offer.startSpan(homeCells(), density.density))
                                    } else if (theirs != null) {
                                        widgetSetup.add(theirs)
                                    }
                                },
                                onShow = { item ->
                                    stack = listOf(root)
                                    homePage = item.position.page
                                },
                                onAdjust = adjustWidget,
                                onRemove = removeItem,
                                onBack = pop,
                            )

                            Screen.BACKUP -> BackupScreen(
                                backups = backups,
                                formFactor = formFactor,
                                appNames = { apps.associate { it.key to it.label } },
                                isInstalled = { it in appsByKey },
                                screen = backdrop.screenSize,
                                canSeeFiles = wallpaper.canRead(),
                                onAllowFiles = allowWallpaper,
                                onOpened = { push(Screen.BACKUP_RESTORE) },
                                onBack = pop,
                            )

                            // The backup as it was when the screen came: it stays while the screen leaves.
                            Screen.BACKUP_RESTORE -> remember { backups.opened }?.let { opened ->
                                RestoreScreen(
                                    backups = backups,
                                    opened = opened,
                                    screen = backdrop.screenSize,
                                    onRestored = pop,
                                    onBack = {
                                        pop()
                                        backups.opened = null
                                    },
                                )
                            }

                            Screen.HOME, Screen.DRAWER, Screen.TV_HOME -> Unit
                        }
                    }
                }
            }

            // The long-press menu. What it shows is kept while it fades out after closing.
            val closeMenu = { menuTarget = null }
            val openMenu: MenuContent? = when (val target = menuTarget) {
                null -> null

                is MenuTarget.App -> {
                    val app = target.app
                    val content = ItemContent.App(app.key)
                    val homeAction = when {
                        root != Screen.HOME -> null

                        homeLayout.contains(content) ->
                            MenuAction(stringResource(R.string.action_remove_from_home), Glyph.CLOSE) {
                                closeMenu()
                                scope.launch { settings.removeFromHome(homeProfile, content, isInstalled) }
                            }

                        else -> MenuAction(stringResource(R.string.action_add_to_home), Glyph.PLUS) {
                            closeMenu()
                            scope.launch {
                                val placed = settings.addToHome(homeProfile, content, homePage, isInstalled)
                                // Show the result: back to the home screen, on the app's page.
                                stack = listOf(root)
                                placed?.let { homePage = it.page }
                            }
                        }
                    }
                    val editAction = MenuAction(stringResource(R.string.action_edit_app), Glyph.EDIT) {
                        closeMenu()
                        iconPickerKey = app.key
                        push(Screen.ICON_PICKER)
                    }
                    // Out of the drawer and off the home screen; the settings bring it back.
                    val hideAction = MenuAction(stringResource(R.string.action_hide_app), Glyph.HIDE) {
                        closeMenu()
                        drawerLift[0] = null
                        scope.launch { settings.setAppHidden(app.key, true) }
                    }
                    // What the system does with the app, side by side at the bottom.
                    val infoAction = MenuAction(stringResource(R.string.action_app_info), Glyph.INFO) {
                        closeMenu()
                        runCatching { context.startActivity(repository.infoIntent(app)) }
                    }
                    val storeAction = MenuAction(stringResource(R.string.action_store_page), Glyph.STORE) {
                        closeMenu()
                        repository.storeIntents(app).firstOrNull { intent ->
                            runCatching { context.startActivity(intent) }.isSuccess
                        }
                    }
                    val canUninstall = remember(app.packageName, repository.appChanges) { repository.canUninstall(app) }
                    val uninstallAction = if (canUninstall) {
                        MenuAction(stringResource(R.string.action_uninstall), Glyph.UNINSTALL) {
                            closeMenu()
                            runCatching { context.startActivity(repository.uninstallIntent(app)) }
                        }
                    } else {
                        null
                    }
                    MenuContent(
                        title = app.label,
                        anchor = target.anchor,
                        actions = listOfNotNull(homeAction, editAction, hideAction),
                        quickActions = listOfNotNull(infoAction, storeAction, uninstallAction),
                        app = app,
                    )
                }

                is MenuTarget.Widget -> {
                    val item = homeLayout.item(target.itemId)
                    val content = item?.content
                    val facts = remember(content, systemWidgets.revision) {
                        content?.let { widgetFacts(context, it, widgets, systemWidgets) }
                    }
                    val limits = remember(content, homeGrid, systemWidgets.revision) {
                        content?.let { sizeLimits(it, widgets, systemWidgets, homeCells(), density.density) }
                    }
                    if (item == null || facts == null || limits == null) {
                        null
                    } else {
                        val (smallest, largest) = limits
                        // One step of its size, or null where the widget or the grid goes no further.
                        val step: (Int, Int) -> (() -> Unit)? = { columns, rows ->
                            WidgetCells.stepped(item.span, columns, rows, smallest, largest)?.let { span ->
                                { scope.launch { settings.resizeOnHome(homeProfile, item.id, span, isInstalled) } }
                            }
                        }
                        val settingsAction = if (facts.adjustable) {
                            MenuAction(stringResource(R.string.action_widget_settings), Glyph.TUNE) {
                                closeMenu()
                                adjustWidget(item)
                            }
                        } else {
                            null
                        }
                        val removeAction = MenuAction(stringResource(R.string.action_remove_from_home), Glyph.CLOSE) {
                            closeMenu()
                            removeItem(item)
                        }
                        // A widget of another app can be dressed like one of Kiri's own.
                        val lookAction = if (content is ItemContent.AppWidget) {
                            MenuAction(stringResource(R.string.widget_look), Glyph.SHAPES) {
                                menuTarget = MenuTarget.Look(item.id, target.anchor)
                            }
                        } else {
                            null
                        }
                        MenuContent(
                            title = facts.label,
                            anchor = target.anchor,
                            actions = listOfNotNull(lookAction, settingsAction, removeAction),
                            steppers = listOf(
                                MenuStepper(
                                    label = stringResource(R.string.widget_width),
                                    valueText = "${item.span.columns}",
                                    onDecrease = step(-1, 0),
                                    onIncrease = step(1, 0),
                                ),
                                MenuStepper(
                                    label = stringResource(R.string.widget_height),
                                    valueText = "${item.span.rows}",
                                    onDecrease = step(0, -1),
                                    onIncrease = step(0, 1),
                                ),
                            ),
                            packageName = facts.packageName,
                        )
                    }
                }

                is MenuTarget.Folder -> {
                    val folder = homeLayout.item(target.itemId)?.content as? ItemContent.Folder
                    if (folder == null) {
                        null
                    } else {
                        val folderId = folder.id
                        val editFolder: ((HomeLayout, GridSize) -> HomeLayout) -> Unit = { change ->
                            scope.launch { settings.editHome(homeProfile, isInstalled, folders = listOf(folderId), change = change) }
                        }
                        val renameAction = MenuAction(stringResource(R.string.action_rename_folder), Glyph.EDIT) {
                            closeMenu()
                            folderOpening.open(folderId, target.anchor, renaming = true)
                        }
                        val undoAction = MenuAction(stringResource(R.string.action_undo_folder), Glyph.SHAPES) {
                            closeMenu()
                            editFolder { layout, grid -> layout.dissolveFolder(folderId, grid) }
                        }
                        val removeAction = MenuAction(stringResource(R.string.action_remove_from_home), Glyph.CLOSE) {
                            closeMenu()
                            editFolder { layout, _ -> layout.withoutFolder(folderId) }
                        }
                        // Its own ground, or none of its own to follow all folders.
                        val grounds = listOf<FolderGround?>(null) + FolderGround.entries
                        val at = grounds.indexOf(folder.ground)
                        val groundStep: (Int) -> (() -> Unit)? = { step ->
                            val next = at + step
                            if (next in grounds.indices) {
                                { editFolder { layout, _ -> layout.setFolderGround(folderId, grounds[next]) } }
                            } else {
                                null
                            }
                        }
                        MenuContent(
                            title = folderName(folder),
                            anchor = target.anchor,
                            actions = listOf(renameAction, undoAction, removeAction),
                            steppers = listOf(
                                MenuStepper(
                                    label = stringResource(R.string.folder_ground_own),
                                    valueText = folder.ground?.let { stringResource(it.labelRes()) }
                                        ?: stringResource(R.string.folder_ground_shared),
                                    onDecrease = groundStep(-1),
                                    onIncrease = groundStep(1),
                                    values = grounds.map { ground ->
                                        ground?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.folder_ground_shared)
                                    },
                                    named = true,
                                ),
                            ),
                            folder = folder,
                        )
                    }
                }

                // How a widget of another app is dressed. The menu sits next to the widget, which
                // changes as the choices are made: it is its own preview.
                is MenuTarget.Look -> {
                    val item = homeLayout.item(target.itemId)
                    val content = item?.content as? ItemContent.AppWidget
                    val facts = remember(content, systemWidgets.revision) {
                        content?.let { widgetFacts(context, it, widgets, systemWidgets) }
                    }
                    if (item == null || content == null || facts == null) {
                        null
                    } else {
                        val look = loaded.widgetLooks[content.appWidgetId] ?: WidgetLook.AsMade
                        val wear: (WidgetLook?) -> (() -> Unit)? = { next ->
                            next?.let { { scope.launch { settings.setWidgetLook(content.appWidgetId, it) } } }
                        }
                        val glass = look.glass
                        val glassStepper = MenuStepper(
                            label = stringResource(R.string.widget_look_glass),
                            valueText = stringResource(glass?.labelRes() ?: R.string.widget_look_as_made),
                            onDecrease = wear(look.withGlassStepped(-1)),
                            onIncrease = wear(look.withGlassStepped(1)),
                            values = WidgetLook.GLASSES.map { stringResource(it?.labelRes() ?: R.string.widget_look_as_made) },
                            named = true,
                        )
                        // Without glass the widget is as its app made it: nothing else to choose.
                        val dressed = if (glass == null) {
                            emptyList()
                        } else {
                            listOfNotNull(
                                eitherStepper(
                                    label = stringResource(R.string.widget_look_background),
                                    first = stringResource(R.string.widget_look_hidden),
                                    second = stringResource(R.string.widget_look_shown),
                                    isSecond = look.ownBackground,
                                ) { wear(look.copy(ownBackground = it)) },
                                // On its own background a widget keeps the colours made for it.
                                if (look.ownBackground) {
                                    null
                                } else {
                                    eitherStepper(
                                        label = stringResource(R.string.widget_look_colors),
                                        first = stringResource(R.string.widget_look_as_made),
                                        second = stringResource(R.string.app_name),
                                        isSecond = look.kiriColors,
                                    ) { wear(look.copy(kiriColors = it)) }
                                },
                                eitherStepper(
                                    label = stringResource(R.string.widget_look_font),
                                    first = stringResource(R.string.widget_look_as_made),
                                    second = stringResource(R.string.app_name),
                                    isSecond = look.kiriFont,
                                ) { wear(look.copy(kiriFont = it)) },
                                MenuStepper(
                                    label = stringResource(R.string.widget_look_margin),
                                    valueText = "${look.marginLevel}",
                                    onDecrease = wear(look.withMarginStepped(-1)),
                                    onIncrease = wear(look.withMarginStepped(1)),
                                ),
                            )
                        }
                        MenuContent(
                            title = stringResource(R.string.widget_look_of, facts.label),
                            anchor = target.anchor,
                            actions = listOf(
                                MenuAction(stringResource(R.string.action_back), Glyph.BACK) {
                                    menuTarget = MenuTarget.Widget(item.id, target.anchor)
                                },
                            ),
                            steppers = listOf(glassStepper) + dressed,
                            packageName = facts.packageName,
                        )
                    }
                }

                // The home screen itself: the ways into what is arranged from here.
                is MenuTarget.Home -> MenuContent(
                    title = stringResource(R.string.settings_home),
                    anchor = target.anchor,
                    actions = listOf(
                        MenuAction(stringResource(R.string.widgets_title), Glyph.WIDGETS) {
                            closeMenu()
                            push(Screen.WIDGETS)
                        },
                        MenuAction(stringResource(R.string.pages_title), Glyph.PAGES) {
                            closeMenu()
                            perform(LauncherAction.OpenPages, null)
                        },
                        MenuAction(stringResource(R.string.settings_wallpaper), Glyph.PICTURE) {
                            closeMenu()
                            push(Screen.WALLPAPER_SETTINGS)
                        },
                        MenuAction(stringResource(R.string.settings_title), Glyph.TUNE) {
                            closeMenu()
                            push(Screen.SETTINGS)
                        },
                    ),
                )
            }
            // Kept outside of observed state on purpose: it is rebuilt at every pass, and noting
            // each one as a change would ask for yet another pass, without end.
            val lastMenu = remember { arrayOfNulls<MenuContent>(1) }
            var menuLeaving by remember { mutableStateOf(false) }
            if (openMenu != null) lastMenu[0] = openMenu
            LaunchedEffect(openMenu != null) {
                if (openMenu != null) menuLeaving = true
            }
            val menu = openMenu ?: if (menuLeaving) lastMenu[0] else null
            if (menu != null) {
                val menuApp = menu.app
                val menuFolder = menu.folder
                val folderIconPx = with(density) { homeSpec.iconSizeDp.dp.roundToPx() }
                val menuPackage = menu.packageName
                val launchers = remember(apps) { apps.associateBy { it.packageName } }
                val header: @Composable () -> Unit = {
                    if (menuApp != null) {
                        AppIcon(menuApp, repository, 32.dp)
                    } else if (menuPackage != null) {
                        PackageIcon(menuPackage, launchers, repository, 32.dp)
                    } else if (menuFolder != null) {
                        FolderFace(
                            folder = menuFolder,
                            appsByKey = appsByKey,
                            repository = repository,
                            iconSizePx = folderIconPx,
                            look = loaded.folderLook,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                ItemMenu(
                    title = menu.title,
                    actions = menu.actions,
                    steppers = menu.steppers,
                    quickActions = menu.quickActions,
                    anchor = menu.anchor,
                    visible = openMenu != null,
                    onDismiss = closeMenu,
                    onHidden = { menuLeaving = false },
                    speed = motion.stiffnessScale,
                    header = if (menuApp != null || menuPackage != null || menuFolder != null) header else null,
                )
            }
        }
    }
}

/**
 * Isolates the hidden/shown toggle of the drawer so switching from closed to open only updates
 * this thin wrapper's semantics, never recomposing KiriApp or HomeSurface. The apps of a closed
 * drawer take no focus: each one asks [LocalOutOfSight] when the focus looks for a place.
 */
@Composable
private fun DrawerVisibilityGate(
    isShown: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown by remember(isShown) { derivedStateOf(isShown) }
    val gate = if (shown) modifier else modifier.clearAndSetSemantics { }
    val outer = LocalOutOfSight.current
    val outOfSight = remember(outer, isShown) { { outer() || !isShown() } }
    CompositionLocalProvider(LocalOutOfSight provides outOfSight) {
        Box(gate) { content() }
    }
}

/** A choice between two in a menu: minus goes to the [first], plus to the [second]. */
private fun eitherStepper(
    label: String,
    first: String,
    second: String,
    isSecond: Boolean,
    choose: (second: Boolean) -> (() -> Unit)?,
): MenuStepper = MenuStepper(
    label = label,
    valueText = if (isSecond) second else first,
    onDecrease = if (isSecond) choose(false) else null,
    onIncrease = if (isSecond) null else choose(true),
    values = listOf(first, second),
    named = true,
)

/**
 * What Home and Drawer show while the settings cover them: what they showed when the settings
 * came over them (see `behind` in [KiriApp]).
 */
@Immutable
private class Behind(
    val homeLayout: HomeLayout,
    val homeSpec: GridSpec,
    val drawerSpec: GridSpec,
    val drawerLook: DrawerLook,
    val labelLook: LabelLook,
    val glassTile: GlassBevel.Tile?,
    val iconSettings: IconSettings,
    val folderLook: FolderLook,
)

/**
 * How long a new list of apps must stand before what an app moved to is followed: an app
 * switching its icon turns two entries on and off, and between the two it may list neither.
 */
private const val APP_MOVE_SETTLE_MS = 1_000L

/** How tall names are guessed to be, room above them included, before the home screen was laid out. */
private const val NAMES_GUESS_DP = 38f

/** How finely the blur follows the drawer: this many steps between closed and open. */
private const val BLUR_STEPS = 20f

/** How many apps show the look of icons while it is adjusted. */
private const val ICON_SAMPLES = 8

/** An id for a new folder: the same in both orientations, never one of the characters saved around it. */
private fun newFolderId(): String = "f" + UUID.randomUUID().toString().replace("-", "").take(12)

/**
 * A name for a folder made of [first] and [second]: what they both are, when their makers say
 * they are the same kind of app, and otherwise just "Folder".
 */
private fun newFolderName(context: Context, repository: AppRepository, first: AppEntry?, second: AppEntry?): String {
    val kinds = listOfNotNull(first, second).map { repository.category(it) }.distinct()
    val name = when (kinds.singleOrNull()) {
        ApplicationInfo.CATEGORY_GAME -> R.string.folder_games
        ApplicationInfo.CATEGORY_AUDIO -> R.string.folder_music
        ApplicationInfo.CATEGORY_VIDEO -> R.string.folder_video
        ApplicationInfo.CATEGORY_IMAGE -> R.string.folder_photos
        ApplicationInfo.CATEGORY_SOCIAL -> R.string.folder_social
        ApplicationInfo.CATEGORY_NEWS -> R.string.folder_news
        ApplicationInfo.CATEGORY_MAPS -> R.string.folder_maps
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> R.string.folder_productivity
        else -> R.string.folder_default
    }
    return context.getString(name)
}

/** How much the wallpaper is blurred under the sheet of the settings: only its colours stay. */
private val DEEP_BLUR = 64.dp

/**
 * Where the drawer sheet is between closed (0) and fully open (1).
 *
 * A finger moves it directly with [dragBy]; when the finger lets go, or when the drawer is
 * opened or closed some other way, [settle] glides it the rest of the way on a spring that
 * starts at the speed the finger had.
 */
@Stable
private class DrawerMotion(initiallyOpen: Boolean, private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(if (initiallyOpen) 1f else 0f)
        private set

    /** How many pixels the sheet travels from closed to open: the height of the screen. */
    var travelPx = 1f

    /** The person's choice of speed, as a factor on the stiffness of the spring. */
    var stiffnessScale = 1f

    private var settling: Job? = null
    private var settlingTo = progress

    /** Moves the sheet by [deltaY] pixels (down is positive). Returns the pixels it used. */
    fun dragBy(deltaY: Float): Float {
        settling?.cancel()
        val from = progress
        progress = (from - deltaY / travelPx).coerceIn(0f, 1f)
        return (from - progress) * travelPx
    }

    /** Whether a sheet released at [velocityUp] pixels a second should end up open. */
    fun restsOpen(velocityUp: Float, wasOpen: Boolean, flingPx: Float): Boolean = when {
        velocityUp > flingPx -> true
        velocityUp < -flingPx -> false
        wasOpen -> progress > 0.75f
        else -> progress > 0.2f
    }

    fun settle(open: Boolean, velocityY: Float = 0f) {
        val target = if (open) 1f else 0f
        if (settling?.isActive == true && settlingTo == target) return
        settling?.cancel()
        settlingTo = target
        settling = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = -velocityY / travelPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow * stiffnessScale,
                    visibilityThreshold = 0.0005f,
                ),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
        }
    }
}

/** How the drawer answers a finger that pulls it down from the top of its list. */
@Immutable
class DrawerPull(
    val isPulled: () -> Boolean,
    val dragBy: (deltaY: Float) -> Float,
    val release: (velocityY: Float) -> Unit,
)

/**
 * The app drawer: every app, full screen, on a sheet of tinted glass over the wallpaper.
 * Pulling down while already at the top of the list drags the whole sheet down with the finger.
 *
 * [settle] says how far open the sheet is, for the apps to settle onto it a moment after it
 * arrives (see [drawerContents]). Without it the apps are simply there, as in a preview.
 *
 * [frost] draws what the glass of the sheet shows through itself: the blurred wallpaper.
 */
@Composable
fun DrawerSurface(
    apps: List<AppEntry>,
    spec: GridSpec,
    look: DrawerLook,
    repository: AppRepository,
    interactive: Boolean,
    onLaunch: (app: AppEntry, iconBounds: Rect?) -> Unit,
    modifier: Modifier = Modifier,
    pull: DrawerPull? = null,
    onLongPress: ((app: AppEntry, iconBounds: Rect) -> Unit)? = null,
    settle: (() -> Float)? = null,
    frost: (DrawScope.() -> Unit)? = null,
) {
    val glass = look.glass.spec(look.opacityPercent)
    val currentPull by rememberUpdatedState(pull)

    val sheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val sheet = currentPull ?: return Offset.Zero
                // Scrolling back up while the sheet is part of the way down lifts the sheet first.
                return if (source == NestedScrollSource.UserInput && available.y < 0f && sheet.isPulled()) {
                    Offset(0f, sheet.dragBy(available.y))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val sheet = currentPull ?: return Offset.Zero
                // The list is at its top and the finger still pulls down: the sheet goes along.
                return if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    Offset(0f, sheet.dragBy(available.y))
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val sheet = currentPull ?: return Velocity.Zero
                if (!sheet.isPulled()) return Velocity.Zero
                sheet.release(available.y)
                return available
            }
        }
    }

    // The drawer keeps clear of the system bars at its sides. At the top and bottom the grid
    // reaches under them, and the apps dissolve there as they are scrolled away. A preview sits
    // inside a screen that already made room, so the same room is taken by value in both.
    val bars = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val sides = Modifier.padding(
        start = bars.calculateStartPadding(direction),
        end = bars.calculateEndPadding(direction),
    )
    val insetModifier = if (interactive) sides.nestedScroll(sheetScroll) else sides

    Box(
        modifier = modifier
            .fillMaxSize()
            // What the glass of the sheet shows through itself, under its tint.
            .drawBehind { frost?.invoke(this) }
            .background(glass.fill)
            .then(insetModifier),
    ) {
        // The sheet is the glass here, with the blurred wallpaper behind all of it. Glass on
        // the sheet, such as the plate of an icon, only adds its tint: showing the wallpaper
        // again would cut a hole in the sheet.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Read only while drawing: the apps follow the sheet without being rebuilt.
                    .graphicsLayer { settle?.let { drawerContents(it()) } },
            ) {
                AppGrid(
                    apps = apps,
                    spec = spec,
                    repository = repository,
                    labelStyle = appLabelStyle().copy(color = glass.content),
                    interactive = interactive,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                    topRoom = bars.calculateTopPadding(),
                    bottomRoom = bars.calculateBottomPadding(),
                )
            }
        }
    }
}
