package io.github.johankrs.kiri.ui.tv

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.tv.TvAllPlacement
import io.github.johankrs.kiri.core.tv.TvArrange
import io.github.johankrs.kiri.core.tv.TvArrangement
import io.github.johankrs.kiri.core.tv.TvBanner
import io.github.johankrs.kiri.core.tv.TvBox
import io.github.johankrs.kiri.core.tv.TvConsoles
import io.github.johankrs.kiri.core.tv.TvDirection
import io.github.johankrs.kiri.core.tv.TvFocus
import io.github.johankrs.kiri.core.tv.TvHomeItems
import io.github.johankrs.kiri.core.tv.TvItem
import io.github.johankrs.kiri.core.tv.TvLook
import io.github.johankrs.kiri.core.tv.TvNameShow
import io.github.johankrs.kiri.core.tv.TvPlacement
import io.github.johankrs.kiri.core.tv.TvSettings
import io.github.johankrs.kiri.core.tv.TvWidget
import io.github.johankrs.kiri.core.tv.TvWidgetKind
import io.github.johankrs.kiri.core.tv.TvWidgets
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.data.TvInput
import io.github.johankrs.kiri.ui.LocalOutOfSight
import io.github.johankrs.kiri.widgets.market.MarketRow
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The key of the gear at the top, and of "All apps", the last place on the home screen. */
private const val GEAR = "@gear"

/**
 * The shadow of a banner (units): how far it fades, how much lower it falls and how dark it is
 * under the banner (at the banner's edge, half of it). With the focus a wider one comes in too.
 */
private const val BANNER_SHADOW_BLUR = 4f
private const val BANNER_SHADOW_DROP = 3f
private const val BANNER_SHADOW = 0.4f
private const val BANNER_LIFT_BLUR = 9f
private const val BANNER_LIFT_DROP = 7f
private const val BANNER_LIFT_SHADOW = 0.26f

internal const val ALL_APPS = "@all"

/** The key of the menu that adds a widget. */
private const val ADD_WIDGET = "@addWidget"

/** One place of the TV's home screen as it is drawn, with its name in the person's language. */
@Immutable
internal class TvShown(
    val key: String,
    val item: TvItem?,
    val name: String,
    val art: BannerArt,
    val app: AppEntry? = null,
    val input: TvInput? = null,
)

/** How [item] is drawn, or null while what it is is not known (or no longer there). */
internal fun shownOf(
    item: TvItem,
    appsByKey: Map<String, AppEntry>,
    inputsById: Map<String, TvInput>?,
    tv: TvSettings,
): TvShown? = when (item) {
    is TvItem.App -> appsByKey[item.appKey]?.let { app ->
        val art = if (tv.bannerOf(item) == TvBanner.Kiri) BannerArt.AppGlass(app) else BannerArt.AppOwn(app)
        TvShown(item.key, item, app.label, art, app = app)
    }
    is TvItem.Input -> inputsById?.get(item.inputId)?.let { input ->
        val name = tv.inputNames[input.id] ?: input.ownName
        TvShown(item.key, item, name, inputArt(tv.bannerOf(item), input, name), input = input)
    }
}

/** The banner of an input: the one chosen for it, or the drawing its kind has. */
internal fun inputArt(banner: TvBanner?, input: TvInput, name: String): BannerArt {
    when (banner) {
        is TvBanner.Console -> TvConsoles.byId(banner.consoleId)?.let { return BannerArt.ConsoleArt(it, banner.style) }
        is TvBanner.Drawn -> return BannerArt.Drawn(banner.glyph, banner.color, name, input.ownName)
        else -> Unit
    }
    val own = TvBanner.forInput(input.kind)
    return BannerArt.Drawn(own.glyph, own.color, name, input.ownName)
}

/** What the TV's home screen asks of the launcher. */
class TvHomeActions(
    /** Opens an app; the rectangle is where its banner is on the screen, in pixels, when known. */
    val launch: (AppEntry, Rect?) -> Unit,
    /** Shows the input; false when the TV would not. */
    val watch: (TvInput) -> Boolean,
    val openSettings: () -> Unit,
    /** Changes what is on the home screen; `start` is what it is while nothing was saved. */
    val editHome: (start: TvHomeItems, change: (TvHomeItems) -> TvHomeItems) -> Unit,
    val setBanner: (TvItem, TvBanner?) -> Unit,
    /** The name the person typed for an app (by `AppEntry.key`) or an input, cleaned; null for its own again. */
    val setAppName: (AppEntry, String?) -> Unit,
    val setInputName: (TvInput, String?) -> Unit,
    val hide: (AppEntry) -> Unit,
    val info: (AppEntry) -> Unit,
    val store: (AppEntry) -> Unit,
    val uninstall: (AppEntry) -> Unit,
    val canUninstall: (AppEntry) -> Boolean,
    /** Changes the widgets over the home screen, from what is saved now. */
    val editWidgets: ((TvWidgets) -> TvWidgets) -> Unit,
    /** Opens the settings of a widget that has some. */
    val openWidgetSettings: (TvWidgetKind) -> Unit,
)

internal enum class TvLayer { HOME, ALL, MENU, EDITOR }

/** A menu that is open: about the place [key], opened from [from], beside [anchor] (units). */
internal class MenuOpen(val key: String, val from: TvLayer, val anchor: TvBox) {
    val targets = TvTargets()
}

internal class EditorOpen(val key: String, val from: TvLayer) {
    val targets = TvTargets()
}

private class Toast(val text: String)

/** Holding OK: it opens the menu instead of the item, once held as long as a long press. */
private class OkHold {
    var down = false
    var held = false
    var job: Job? = null
}

/**
 * The home screen of a TV: the banners the person chose, in a grid or a carousel over a
 * wallpaper, "All apps" as the last of them, and the gear for the settings at the top.
 *
 * Everything is moved with the remote: the arrows move the focus where it looks like it goes,
 * OK opens, holding OK opens the menu of the banner. [active] is false while something else
 * (the settings) is over it: then it takes no key and gives up the focus.
 *
 * [inputs] and [tvAppKeys] are null while they are being read. [homeTick] changes when Home is
 * pressed: everything closes and the focus goes back to the first banner.
 */
@Composable
fun TvHome(
    apps: List<AppEntry>,
    inputs: List<TvInput>?,
    tvAppKeys: Set<String>?,
    tv: TvSettings,
    repository: AppRepository?,
    active: Boolean,
    homeTick: Int,
    actions: TvHomeActions,
    marketRows: @Composable () -> List<MarketRow>?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val outOfSight = LocalOutOfSight.current
    val look = tv.look

    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val inputsById = remember(inputs) { inputs?.associateBy { it.id } }
    // The first home screen: the TV's inputs, then the first apps made for a TV.
    val start = remember(inputs, tvAppKeys, apps) {
        if (inputs == null || tvAppKeys == null || apps.isEmpty()) {
            null
        } else {
            TvHomeItems.firstRun(inputs.map { it.id }, apps.filter { it.key in tvAppKeys }.map { it.key })
        }
    }
    // Saved once, so apps installed afterwards never come in on their own.
    LaunchedEffect(start, tv.home == null) {
        if (tv.home == null && start != null) actions.editHome(start) { it }
    }
    // A change shows at once; what is saved catches up a moment later.
    var pending by remember { mutableStateOf<TvHomeItems?>(null) }
    LaunchedEffect(tv.home) { if (pending != null && tv.home == pending) pending = null }
    val saved = pending ?: tv.home ?: start ?: TvHomeItems.EMPTY
    val home = remember(saved, appsByKey, inputsById) {
        saved.present(
            isApp = if (appsByKey.isEmpty()) null else ({ key: String -> key in appsByKey }),
            isInput = inputsById?.let { known -> { id: String -> id in known } },
        )
    }
    val changeHome: ((TvHomeItems) -> TvHomeItems) -> Unit = { edit ->
        val next = edit(pending ?: tv.home ?: start ?: TvHomeItems.EMPTY)
        pending = next
        actions.editHome(start ?: TvHomeItems.EMPTY) { next }
    }
    // The widgets, the same way: a change shows at once.
    var pendingWidgets by remember { mutableStateOf<TvWidgets?>(null) }
    LaunchedEffect(tv.widgets) { if (pendingWidgets != null && tv.widgets == pendingWidgets) pendingWidgets = null }
    val widgets = pendingWidgets ?: tv.widgets
    val changeWidgets: ((TvWidgets) -> TvWidgets) -> Unit = { edit ->
        val next = edit(pendingWidgets ?: tv.widgets)
        pendingWidgets = next
        actions.editWidgets { next }
    }

    val allLabel = stringResource(R.string.tv_all_apps)
    val shown = remember(home, appsByKey, inputsById, tv, allLabel) {
        home.items.mapNotNull { shownOf(it, appsByKey, inputsById, tv) } +
            TvShown(ALL_APPS, null, allLabel, BannerArt.AllApps(allLabel))
    }
    // "All apps": the inputs, then every app that is not hidden.
    val allShown = remember(appsByKey, inputs, tv) {
        inputs.orEmpty().map { input ->
            val item = TvItem.Input(input.id)
            val name = tv.inputNames[input.id] ?: input.ownName
            TvShown(item.key, item, name, inputArt(tv.bannerOf(item), input, name), input = input)
        } to apps.map { app ->
            val item = TvItem.App(app.key)
            TvShown(item.key, item, app.label, BannerArt.AppOwn(app), app = app)
        }
    }

    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var onGear by rememberSaveable { mutableStateOf(false) }
    // The key of the widget with the focus; null while it is on a banner.
    var widgetFocus by rememberSaveable { mutableStateOf<String?>(null) }
    if (widgetFocus != null && widgets.byKey(widgetFocus) == null) widgetFocus = null
    val focusIndex = shown.indexOfFirst { it.key == focusKey }.takeIf { it >= 0 } ?: 0
    var layer by remember { mutableStateOf(TvLayer.HOME) }
    var menu by remember { mutableStateOf<MenuOpen?>(null) }
    var editor by remember { mutableStateOf<EditorOpen?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var allFocus by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }
    val say: (String) -> Unit = { toast = Toast(it) }
    var resetTick by remember { mutableIntStateOf(0) }
    val scroll = remember { floatArrayOf(0f) }
    val resetFocus = {
        focusKey = shown.firstOrNull()?.key
        onGear = false
        widgetFocus = null
        scroll[0] = 0f
        resetTick++
    }
    var lastHomeTick by remember { mutableIntStateOf(homeTick) }
    LaunchedEffect(homeTick) {
        if (homeTick != lastHomeTick) {
            lastHomeTick = homeTick
            menu = null
            editor = null
            moving = null
            layer = TvLayer.HOME
            resetFocus()
        }
    }

    val rootFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var rootFocused by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            runCatching { rootFocus.requestFocus() }
        } else if (rootFocused) {
            // Settings opened over it: let go, so the remote moves in them and not behind them.
            focusManager.clearFocus(force = true)
        }
    }

    val ok = remember { OkHold() }
    val keys = remember { TvKeys() }
    val holdMs = LocalViewConfiguration.current.longPressTimeoutMillis

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (!active) return@onKeyEvent false
                val key = event.tvKey() ?: return@onKeyEvent false
                val handler = keys.screen ?: keys.panel
                if (key == TvKey.OK) {
                    when (event.type) {
                        KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) {
                            ok.down = true
                            ok.held = false
                            ok.job?.cancel()
                            ok.job = scope.launch {
                                delay(holdMs)
                                if (ok.down && !ok.held) {
                                    ok.held = true
                                    handler?.hold?.invoke()
                                }
                            }
                        } else if (event.nativeKeyEvent.isLongPress && ok.down && !ok.held) {
                            ok.job?.cancel()
                            ok.held = true
                            handler?.hold?.invoke()
                        }
                        KeyEventType.KeyUp -> {
                            ok.job?.cancel()
                            val pressed = ok.down && !ok.held
                            ok.down = false
                            if (pressed) handler?.ok?.invoke()
                        }
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyDown) {
                    key.direction()?.let { handler?.move?.invoke(it) }
                }
                true
            }
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusProperties { canFocus = active && !outOfSight() }
            .focusable(),
    ) {
        val density = LocalDensity.current
        val units = remember(maxWidth, density) { TvUnits(maxWidth / TvArrange.WIDTH, density) }
        val screenH = maxHeight / units.unit
        val withWidgets = widgets.list.isNotEmpty()
        val placement = remember(shown.size, look, focusIndex, screenH, resetTick, withWidgets) {
            TvArrange.place(shown.size, look, screenH, focusIndex, scroll[0], withWidgets).also { scroll[0] = it.scroll }
        }
        val widgetBoxes = remember(widgets, look) { TvArrange.widgets(widgets.list.map { it.width }, look.align, look.arrangement) }

        // Where a box of the screen is, in pixels: an app opens from its banner.
        fun pixels(box: TvBox?): Rect? = box?.let { Rect(units.px(it.left), units.px(it.top), units.px(it.right), units.px(it.bottom)) }

        // What each key does, as things are now. [from] is where the item is on the screen.
        fun activate(item: TvShown, from: TvBox?) {
            when {
                item.key == ALL_APPS -> {
                    layer = TvLayer.ALL
                    allFocus = 0
                }
                item.app != null -> actions.launch(item.app, pixels(from))
                item.input != null -> if (!actions.watch(item.input)) say(context.getString(R.string.tv_toast_no_input, item.name))
            }
        }

        fun openMenu(item: TvShown, from: TvLayer, anchor: TvBox) {
            if (item.item == null) return
            menu = MenuOpen(item.key, from, anchor)
            layer = TvLayer.MENU
        }

        fun openWidgetMenu(widget: TvWidget, boxes: List<TvBox>) {
            val anchor = boxes.getOrNull(widgets.list.indexOf(widget)) ?: return
            menu = MenuOpen(widget.key, TvLayer.HOME, anchor)
            layer = TvLayer.MENU
        }

        fun closeMenu() {
            val open = menu ?: return
            menu = null
            layer = open.from
        }

        fun openEditor(key: String, from: TvLayer) {
            menu = null
            editor = EditorOpen(key, from)
            layer = TvLayer.EDITOR
        }

        fun closeEditor() {
            val open = editor ?: return
            editor = null
            layer = open.from
            if (open.from == TvLayer.HOME) focusKey = open.key
        }

        fun allBox(index: Int, all: TvAllPlacement): TvBox? {
            val tile = all.tiles.getOrNull(index) ?: return null
            val moved = all.scrollFor(allFocus, screenH - ALL_TOP)
            return tile.copy(top = tile.top + ALL_TOP - moved)
        }

        val allPlaced = remember(allShown) { TvAllPlacement.place(allShown.first.size, allShown.second.size) }
        val allList = remember(allShown) { allShown.first + allShown.second }

        val movingWidget = widgets.byKey(moving)
        val handler = when {
            movingWidget != null -> TvKeyActions(
                ok = {
                    moving = null
                    say(context.getString(R.string.tv_toast_dropped))
                },
                hold = { moving = null },
                move = { direction ->
                    when (direction) {
                        TvDirection.LEFT -> changeWidgets { it.moved(movingWidget.kind, -1) }
                        TvDirection.RIGHT -> changeWidgets { it.moved(movingWidget.kind, 1) }
                        else -> Unit
                    }
                },
            )
            moving != null -> TvKeyActions(
                ok = {
                    moving = null
                    say(context.getString(R.string.tv_toast_dropped))
                },
                hold = { moving = null },
                move = move@{ direction ->
                    val key = moving ?: return@move
                    val at = shown.indexOfFirst { it.key == key }
                    if (at < 0) return@move
                    // Only among the banners: "All apps" stays last.
                    val boxes = placement.boxes.mapIndexed { i, box -> box.takeIf { shown[i].key != ALL_APPS } }
                    val to = TvFocus.nearest(placement.boxes[at], boxes, direction, sameRow = true)
                    val item = shown[at].item ?: return@move
                    val target = shown.getOrNull(to)?.item ?: return@move
                    changeHome { now -> now.moved(item, now.items.indexOf(target)) }
                },
            )
            layer == TvLayer.HOME -> TvKeyActions(
                ok = {
                    val widget = widgets.byKey(widgetFocus)
                    when {
                        onGear -> actions.openSettings()
                        // A widget has no app of its own to open: OK shows what can be done with it.
                        widget != null -> openWidgetMenu(widget, widgetBoxes)
                        else -> shown.getOrNull(focusIndex)?.let { activate(it, placement.boxes.getOrNull(focusIndex)) }
                    }
                },
                hold = {
                    val widget = widgets.byKey(widgetFocus)
                    when {
                        onGear -> Unit
                        widget != null -> openWidgetMenu(widget, widgetBoxes)
                        else -> {
                            val item = shown.getOrNull(focusIndex)
                            if (item != null) openMenu(item, TvLayer.HOME, placement.boxes[focusIndex])
                        }
                    }
                },
                move = move@{ direction ->
                    if (onGear) {
                        if (direction == TvDirection.DOWN) onGear = false
                        return@move
                    }
                    // The screen is in layers: the gear, the widgets, the banners.
                    val widget = widgets.byKey(widgetFocus)
                    if (widget != null) {
                        val from = widgetBoxes.getOrNull(widgets.list.indexOf(widget)) ?: return@move
                        when (direction) {
                            TvDirection.UP -> onGear = true
                            TvDirection.DOWN -> {
                                val seen = placement.boxes.map { box -> box.takeIf { it.top >= placement.shownFrom } }
                                val to = TvFocus.nearest(from, seen, direction).takeIf { it >= 0 }
                                    ?: TvFocus.nearest(from, placement.boxes, direction)
                                if (to >= 0) {
                                    widgetFocus = null
                                    focusKey = shown[to].key
                                }
                            }
                            else -> {
                                val to = TvFocus.nearest(from, widgetBoxes, direction, sameRow = true)
                                if (to >= 0) widgetFocus = widgets.list[to].key
                            }
                        }
                        return@move
                    }
                    val from = placement.boxes.getOrNull(focusIndex) ?: return@move
                    val to = TvFocus.nearest(from, placement.boxes, direction, sameRow = true)
                    when {
                        to >= 0 -> focusKey = shown[to].key
                        // Going up from the top reaches the widgets, then the gear.
                        direction == TvDirection.UP && widgetBoxes.isNotEmpty() -> {
                            val near = TvFocus.nearest(from, widgetBoxes, direction).coerceAtLeast(0)
                            widgetFocus = widgets.list[near].key
                        }
                        direction == TvDirection.UP -> onGear = true
                    }
                },
            )
            layer == TvLayer.ALL -> TvKeyActions(
                ok = { allList.getOrNull(allFocus)?.let { activate(it, allBox(allFocus, allPlaced)) } },
                hold = {
                    val item = allList.getOrNull(allFocus)
                    val box = allBox(allFocus, allPlaced)
                    if (item != null && box != null) openMenu(item, TvLayer.ALL, box)
                },
                move = move@{ direction ->
                    val from = allPlaced.tiles.getOrNull(allFocus) ?: return@move
                    val to = TvFocus.nearest(from, allPlaced.tiles, direction, sameRow = true)
                    if (to >= 0) allFocus = to
                },
            )
            else -> null
        }

        // The panels say themselves what their keys do; the home screen and "All apps" here.
        keys.screen = handler
        if (layer == TvLayer.HOME || layer == TvLayer.ALL) keys.panel = null

        BackHandler(enabled = active && (layer != TvLayer.HOME || moving != null || onGear || widgetFocus != null || focusIndex != 0 || scroll[0] != 0f)) {
            when {
                moving != null -> moving = null
                layer == TvLayer.MENU -> closeMenu()
                layer == TvLayer.EDITOR -> keys.panel?.back?.invoke() ?: closeEditor()
                layer == TvLayer.ALL -> {
                    layer = TvLayer.HOME
                    focusKey = ALL_APPS
                }
                else -> resetFocus()
            }
        }

        CompositionLocalProvider(LocalTvUnits provides units, LocalTvInk provides tvInk(look)) {
            TvBackdrop(look, running = active)
            // "All apps" covers the home screen, which fades out under it.
            val allOpen = layer == TvLayer.ALL || (layer != TvLayer.HOME && (menu?.from == TvLayer.ALL || editor?.from == TvLayer.ALL))
            val homeAway by animateFloatAsState(if (allOpen) 1f else 0f, tween(220), label = "homeAway")
            if (homeAway < 1f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - homeAway }) {
                    val homeFocus = layer == TvLayer.HOME && !onGear || moving != null || layer == TvLayer.MENU && menu?.from == TvLayer.HOME
                    TvBanners(
                        shown = shown,
                        placement = placement,
                        look = look,
                        focusIndex = if (homeFocus && widgetFocus == null) focusIndex else -1,
                        moving = moving,
                        repository = repository,
                    )
                    TvWidgetsBand(
                        widgets = widgets.list,
                        boxes = widgetBoxes,
                        focused = widgetFocus.takeIf { homeFocus },
                        moving = moving,
                        marketRows = marketRows,
                        shadow = look.shadowAmount,
                    )
                    TopBar(gearFocused = onGear && layer == TvLayer.HOME)
                }
            }

            // Over the home screen: a shade, then whatever is open.
            val shade by animateFloatAsState(if (layer == TvLayer.MENU || layer == TvLayer.EDITOR) 1f else 0f, tween(300), label = "shade")
            if (shade > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(20f)
                        .graphicsLayer { alpha = shade }
                        .background(Color(0x6B03090C)),
                )
            }
            if (allOpen) {
                Box(Modifier.fillMaxSize().zIndex(10f)) {
                    TvAllApps(
                        inputs = allShown.first,
                        apps = allShown.second,
                        placed = allPlaced,
                        focus = if (layer == TvLayer.ALL || menu?.from == TvLayer.ALL) allFocus else -1,
                        onHome = home.items.toSet(),
                        screenH = screenH,
                        repository = repository,
                    )
                }
            }
            val openMenu = menu
            val menuWidget = openMenu?.let { widgets.byKey(it.key) }
            if (openMenu != null && menuWidget != null) {
                val kind = menuWidget.kind
                val name = widgetName(kind)
                val entries = buildList {
                    add(MenuEntry("move", stringResource(R.string.tv_menu_move), TvIcons.MOVE) {
                        closeMenu()
                        moving = menuWidget.key
                        say(context.getString(R.string.tv_toast_widget_moving))
                    })
                    // It stays open: the widget grows behind it, and OK again goes on.
                    add(MenuEntry("size", stringResource(R.string.tv_menu_size, widgetSizeName(menuWidget.size)), TvIcons.SIZE) {
                        changeWidgets { it.grown(kind) }
                    })
                    if (kind == TvWidgetKind.MARKET) {
                        add(MenuEntry("settings", stringResource(R.string.tv_menu_widget_settings), TvIcons.SLIDERS) {
                            closeMenu()
                            actions.openWidgetSettings(kind)
                        })
                    }
                    add(MenuEntry("remove", stringResource(R.string.tv_menu_remove), TvIcons.REMOVE) {
                        closeMenu()
                        val at = widgets.list.indexOf(menuWidget)
                        val rest = widgets.without(kind).list
                        widgetFocus = rest.getOrNull(at)?.key ?: rest.lastOrNull()?.key
                        if (widgetFocus == null) focusKey = shown.firstOrNull()?.key
                        changeWidgets { it.without(kind) }
                        say(context.getString(R.string.tv_toast_widget_removed, name))
                    })
                    if (widgets.missing.isNotEmpty()) {
                        add(MenuEntry("add", stringResource(R.string.tv_menu_add_widget), TvIcons.PLUS) {
                            menu = MenuOpen(ADD_WIDGET, TvLayer.HOME, openMenu.anchor)
                        })
                    }
                }
                TvMenu(
                    open = openMenu,
                    title = name,
                    subtitle = stringResource(R.string.tv_kind_widget),
                    badge = { WidgetBadge(kind, units.dp(64f)) },
                    entries = entries,
                    quick = emptyList(),
                    screenH = screenH,
                    onKeys = { keys.panel = it },
                    // Beside the widget as it is now: it may have just grown.
                    anchor = widgetBoxes.getOrNull(widgets.list.indexOf(menuWidget)) ?: openMenu.anchor,
                )
            } else if (openMenu != null && openMenu.key == ADD_WIDGET) {
                val entries = widgets.missing.map { kind ->
                    val name = widgetName(kind)
                    MenuEntry("add:${kind.key}", name, TvIcons.PLUS) {
                        closeMenu()
                        changeWidgets { it.with(kind, 0) }
                        widgetFocus = TvWidget(kind).key
                        say(context.getString(R.string.tv_toast_added, name))
                    }
                }
                if (entries.isEmpty()) {
                    LaunchedEffect(openMenu) { closeMenu() }
                } else {
                    TvMenu(
                        open = openMenu,
                        title = stringResource(R.string.tv_menu_add_widget),
                        subtitle = stringResource(R.string.tv_add_widget_caption),
                        badge = null,
                        entries = entries,
                        quick = emptyList(),
                        screenH = screenH,
                        onKeys = { keys.panel = it },
                    )
                }
            } else if (openMenu != null) {
                val item = (shown + allList).firstOrNull { it.key == openMenu.key }
                if (item == null) {
                    LaunchedEffect(openMenu) { closeMenu() }
                } else {
                    val onHomeNow = item.item != null && item.item in home
                    val entries = buildList {
                        if (openMenu.from == TvLayer.HOME) {
                            add(MenuEntry("move", stringResource(R.string.tv_menu_move), TvIcons.MOVE) {
                                closeMenu()
                                moving = item.key
                                say(context.getString(R.string.tv_toast_moving))
                            })
                            add(MenuEntry("edit", stringResource(R.string.tv_menu_edit), TvIcons.EDIT) { openEditor(item.key, TvLayer.HOME) })
                        }
                        if (onHomeNow) {
                            add(MenuEntry("remove", stringResource(R.string.tv_menu_remove), TvIcons.REMOVE) {
                                closeMenu()
                                val at = shown.indexOfFirst { it.key == item.key }
                                item.item?.let { gone -> changeHome { it.without(gone) } }
                                if (openMenu.from == TvLayer.HOME) focusKey = shown.getOrNull(at + 1)?.key
                                say(context.getString(R.string.tv_toast_removed, item.name))
                            })
                        } else {
                            add(MenuEntry("add", stringResource(R.string.tv_menu_add), TvIcons.ADD_HOME) {
                                closeMenu()
                                item.item?.let { added -> changeHome { it.with(added) } }
                                say(context.getString(R.string.tv_toast_added, item.name))
                            })
                        }
                        if (openMenu.from == TvLayer.ALL) {
                            add(MenuEntry("edit", stringResource(R.string.tv_menu_edit), TvIcons.EDIT) { openEditor(item.key, TvLayer.ALL) })
                        }
                        if (item.app != null) {
                            add(MenuEntry("hide", stringResource(R.string.action_hide_app), TvIcons.HIDE) {
                                closeMenu()
                                val at = shown.indexOfFirst { it.key == item.key }
                                actions.hide(item.app)
                                if (openMenu.from == TvLayer.HOME) focusKey = shown.getOrNull(at + 1)?.key
                                say(context.getString(R.string.tv_toast_hidden, item.name))
                            })
                        }
                    }
                    val quick = if (item.app == null) {
                        emptyList()
                    } else {
                        buildList {
                            add(MenuEntry("info", stringResource(R.string.tv_menu_info), TvIcons.INFO) {
                                closeMenu()
                                actions.info(item.app)
                            })
                            add(MenuEntry("store", stringResource(R.string.tv_menu_store), TvIcons.STORE) {
                                closeMenu()
                                actions.store(item.app)
                            })
                            if (actions.canUninstall(item.app)) {
                                add(MenuEntry("uninstall", stringResource(R.string.action_uninstall), TvIcons.TRASH) {
                                    closeMenu()
                                    actions.uninstall(item.app)
                                })
                            }
                        }
                    }
                    val subtitle = item.input?.let { stringResource(R.string.tv_kind_input, it.ownName) } ?: stringResource(R.string.tv_kind_app)
                    TvMenu(
                        open = openMenu,
                        title = item.name,
                        subtitle = subtitle,
                        badge = { TvBannerFace(item.art, units.dp(64f), repository, corner = units.dp(7f)) },
                        entries = entries,
                        quick = quick,
                        screenH = screenH,
                        onKeys = { keys.panel = it },
                    )
                }
            }
            val openEditor = editor
            if (openEditor != null) {
                val item = (shown + allList).firstOrNull { it.key == openEditor.key }
                if (item?.item == null) {
                    LaunchedEffect(openEditor) { closeEditor() }
                } else {
                    TvBannerEditor(
                        open = openEditor,
                        item = item,
                        banner = tv.bannerOf(item.item),
                        screenH = screenH,
                        repository = repository,
                        actions = actions,
                        rootFocus = rootFocus,
                        onClose = { closeEditor() },
                        onKeys = { keys.panel = it },
                    )
                }
            }
            ToastPill(toast?.text)
        }
    }
}

/** What the keys do on what is open now. */
internal class TvKeyActions(
    val ok: () -> Unit,
    val hold: () -> Unit = ok,
    val move: (TvDirection) -> Unit,
    /** Back, when the panel wants it before it is closed. */
    val back: (() -> Unit)? = null,
)

/**
 * What the keys do, as last composed: [screen] for the home screen and "All apps", [panel]
 * for the menu or the editor, which say it themselves. Read when a key comes.
 */
private class TvKeys {
    var screen: TvKeyActions? = null
    var panel: TvKeyActions? = null
}

/** Where "All apps" starts its list, under its title. */
internal const val ALL_TOP = 104f

@Composable
private fun TvBanners(
    shown: List<TvShown>,
    placement: TvPlacement,
    look: TvLook,
    focusIndex: Int,
    moving: String?,
    repository: AppRepository?,
) {
    val carousel = look.arrangement == TvArrangement.CAROUSEL
    shown.forEachIndexed { index, item ->
        val box = placement.boxes.getOrNull(index) ?: return@forEachIndexed
        androidx.compose.runtime.key(item.key) {
            BannerSlot(
                item = item,
                box = box,
                width = placement.bannerWidth,
                focused = index == focusIndex,
                moving = moving == item.key,
                focusScale = placement.focusScale,
                names = look.names,
                carousel = carousel,
                outOfSight = box.top < placement.shownFrom,
                repository = repository,
                shadow = look.shadowAmount,
            )
        }
    }
}

@Composable
private fun BannerSlot(
    item: TvShown,
    box: TvBox,
    width: Float,
    focused: Boolean,
    moving: Boolean,
    focusScale: Float,
    names: TvNameShow,
    carousel: Boolean,
    outOfSight: Boolean,
    repository: AppRepository?,
    /** How dark the shadow around the banner is, as a share of the usual one (see `TvLook.shadowAmount`). */
    shadow: Float,
) {
    val units = LocalTvUnits.current
    val glide = spring<Float>(dampingRatio = 0.82f, stiffness = 240f)
    val x = animateFloatAsState(box.left, glide, label = "x")
    val y = animateFloatAsState(box.top, glide, label = "y")
    val lift = if (moving) 1.16f else if (focused) focusScale else 1f
    val scale = animateFloatAsState(lift, spring(dampingRatio = 0.55f, stiffness = 420f), label = "scale")
    val ring = animateFloatAsState(if (focused || moving) 1f else 0f, tween(220), label = "ring")
    val seen = animateFloatAsState(if (outOfSight) 0f else 1f, tween(300), label = "seen")
    val nameSeen = animateFloatAsState(
        when (names) {
            TvNameShow.ALWAYS -> 1f
            TvNameShow.FOCUS -> if (focused) 1f else 0f
            TvNameShow.NEVER -> 0f
        },
        tween(250),
        label = "name",
    )
    val bw = units.dp(width)
    val bh = TvArrange.heightOf(width)
    val ringColor = if (moving) Color(0xFF7FE0D2) else Color.White.copy(alpha = 0.92f)
    Box(
        Modifier
            .offset { IntOffset(units.px(x.value).roundToInt(), units.px(y.value).roundToInt()) }
            .zIndex(if (focused || moving) 3f else 1f)
            .graphicsLayer { alpha = seen.value },
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = TransformOrigin(0.5f, 0.55f)
                    translationY = if (moving) -units.px(4f) else 0f
                    clip = false
                }
                .drawWithCache {
                    val corner = size.width * (12f / 148f)
                    val face = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
                    // A close shadow, and a wider one that comes in with the focus, as if lifted.
                    val near = if (shadow > 0f) SoftShadow(units.px(BANNER_SHADOW_BLUR)) else null
                    val far = if (shadow > 0f) SoftShadow(units.px(BANNER_LIFT_BLUR)) else null
                    onDrawBehind {
                        val r = ring.value
                        // Only around the banner: glass banners would show it under them.
                        if (near != null && far != null) clipPath(face, ClipOp.Difference) {
                            near.draw(this, BANNER_SHADOW * shadow, units.px(BANNER_SHADOW_DROP), corner)
                            if (r > 0f) far.draw(this, BANNER_LIFT_SHADOW * shadow * r, units.px(BANNER_LIFT_DROP), corner)
                        }
                        if (r > 0f) {
                            val line = units.px(2.5f)
                            drawRoundRect(
                                color = ringColor.copy(alpha = ringColor.alpha * r),
                                topLeft = Offset(-line / 2f, -line / 2f),
                                size = Size(size.width + line, size.height + line),
                                cornerRadius = CornerRadius(corner + line),
                                style = Stroke(line),
                            )
                        }
                    }
                },
        ) {
            TvBannerFace(item.art, bw, repository)
        }
        if (moving) {
            BasicText(
                text = "◀  ▲ ▼  ▶",
                style = TextStyle(
                    fontFamily = TvFont,
                    fontSize = units.sp(12f),
                    letterSpacing = units.sp(3f),
                    color = Color(0xFFB8F2E8),
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, units.px(1f)), units.px(4f)),
                ),
                modifier = Modifier
                    .width(bw)
                    .offset(y = units.dp(-30f)),
            )
        }
        if (names != TvNameShow.NEVER) {
            val shift = if (focused) (if (carousel) 12f else 6f) else 0f
            val nameShift = animateFloatAsState(shift, spring(dampingRatio = 0.6f, stiffness = 420f), label = "nameShift")
            val ink = LocalTvInk.current
            BasicText(
                text = item.name,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                style = TextStyle(
                    fontFamily = TvFont,
                    fontWeight = FontWeight(500),
                    fontSize = units.sp(if (focused && carousel) 16f else 14f),
                    lineHeight = units.sp(17f),
                    color = ink,
                    textAlign = TextAlign.Center,
                    shadow = if (ink == Color.White) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, units.px(1f)), units.px(4f)) else null,
                ),
                modifier = Modifier
                    .width(bw)
                    .wrapContentWidth(unbounded = true)
                    .graphicsLayer {
                        alpha = nameSeen.value
                        translationY = units.px(bh + 10f + nameShift.value - 4f * (1f - nameSeen.value))
                    },
            )
        }
    }
}

@Composable
private fun TopBar(gearFocused: Boolean) {
    val units = LocalTvUnits.current
    val context = LocalContext.current
    var now by remember { mutableStateOf(clockText(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = clockText(context)
        }
    }
    val scale by animateFloatAsState(if (gearFocused) 1.18f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "gear")
    val lit by animateFloatAsState(if (gearFocused) 1f else 0f, tween(200), label = "gearLit")
    val ink = LocalTvInk.current
    // Dark writing on a pale wallpaper needs no shadow to stand out.
    val shadow = if (ink == Color.White) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, units.px(1f)), units.px(6f)) else null
    Box(Modifier.fillMaxSize().zIndex(5f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = units.dp(22f), end = units.dp(34f)),
        ) {
            BasicText(
                text = now,
                style = TextStyle(
                    fontFamily = TvFont,
                    fontSize = units.sp(20f),
                    color = ink,
                    fontFeatureSettings = "tnum",
                    shadow = shadow,
                ),
            )
            Spacer(Modifier.width(units.dp(14f)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(units.dp(38f))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawBehind {
                        if (lit > 0f) {
                            drawCircle(ink.copy(alpha = 0.16f * lit))
                            drawCircle(ink.copy(alpha = 0.85f * lit), style = Stroke(units.px(2f)))
                        }
                    },
            ) {
                TvLineIcon(TvIcons.GEAR, ink, Modifier.size(units.dp(20f)))
            }
        }
    }
}

private fun clockText(context: android.content.Context): String =
    DateFormat.getTimeFormat(context).format(Date())

@Composable
private fun ToastPill(text: String?) {
    val units = LocalTvUnits.current
    val shown = remember { mutableStateOf(text) }
    if (text != null) shown.value = text
    val seen by animateFloatAsState(if (text != null) 1f else 0f, tween(250), label = "toast")
    val words = shown.value ?: return
    if (seen <= 0f) return
    Box(Modifier.fillMaxSize().zIndex(40f), contentAlignment = Alignment.BottomCenter) {
        BasicText(
            text = words,
            style = TextStyle(fontFamily = TvFont, fontSize = units.sp(15f), color = Color.White),
            modifier = Modifier
                .padding(bottom = units.dp(26f))
                .graphicsLayer {
                    alpha = seen
                    translationY = units.px(14f) * (1f - seen)
                }
                .tvGlass(RoundedCornerShape(50), strong = true)
                .padding(horizontal = units.dp(20f), vertical = units.dp(10f)),
        )
    }
}

/** One row of a menu: [id] for the focus, what it says and its drawing, and what it does. */
internal class MenuEntry(val id: String, val label: String, val icon: String, val run: () -> Unit)

/**
 * A menu beside what it is about: its [title] and [subtitle] at the top, with a small drawing
 * of it ([badge]) when there is one, then its [entries] and a row of [quick] ones.
 */
@Composable
private fun TvMenu(
    open: MenuOpen,
    title: String,
    subtitle: String,
    badge: (@Composable () -> Unit)?,
    entries: List<MenuEntry>,
    quick: List<MenuEntry>,
    screenH: Float,
    onKeys: (TvKeyActions) -> Unit,
    anchor: TvBox = open.anchor,
) {
    val units = LocalTvUnits.current
    val targets = open.targets
    if (targets.focused == null || (entries + quick).none { it.id == targets.focused }) targets.focused = entries.firstOrNull()?.id
    val all = entries + quick
    onKeys(
        TvKeyActions(
            ok = { all.firstOrNull { it.id == targets.focused }?.run?.invoke() },
            move = { targets.move(it) },
        ),
    )
    // Beside the banner, where it fits; under it when it does not.
    val width = 300f
    val height = 20f + 61f + 46f * entries.size + if (quick.isEmpty()) 0f else 68f
    val a = anchor
    var x = a.right + 18f
    if (x + width > TvArrange.WIDTH - 16f) x = a.left - width - 18f
    x = x.coerceIn(16f, TvArrange.WIDTH - width - 16f)
    var y = (a.top - 10f).coerceIn(16f, (screenH - height - 16f).coerceAtLeast(16f))
    if (x < a.right && x + width > a.left) y = (a.bottom + 12f).coerceIn(16f, (screenH - height - 16f).coerceAtLeast(16f))

    val appear = remember(open) { Animatable(0f) }
    LaunchedEffect(open) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    val shape = RoundedCornerShape(units.dp(22f))
    Box(Modifier.fillMaxSize().zIndex(30f)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .offset(units.dp(x), units.dp(y))
                .width(units.dp(width))
                .graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s
                    scaleY = s
                }
                .then(targets.rootModifier)
                .tvGlass(shape)
                .padding(units.dp(10f))
                .clearAndSetSemantics { },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = units.dp(8f), end = units.dp(8f), top = units.dp(6f), bottom = units.dp(12f)),
            ) {
                if (badge != null) {
                    badge()
                    Spacer(Modifier.width(units.dp(12f)))
                }
                androidx.compose.foundation.layout.Column {
                    BasicText(
                        title,
                        maxLines = 2,
                        style = TextStyle(fontFamily = TvFont, fontWeight = FontWeight(600), fontSize = units.sp(17f), lineHeight = units.sp(19.5f), color = Color.White),
                    )
                    BasicText(
                        subtitle,
                        maxLines = 1,
                        style = TextStyle(fontFamily = TvFont, fontSize = units.sp(12.5f), color = Color.White.copy(alpha = 0.7f)),
                    )
                }
            }
            Divider()
            Spacer(Modifier.size(units.dp(6f)))
            for (entry in entries) MenuRow(entry, targets)
            if (quick.isNotEmpty()) {
                Spacer(Modifier.size(units.dp(6f)))
                Divider()
                Spacer(Modifier.size(units.dp(6f)))
                Row {
                    for (entry in quick) {
                        val on = targets.focused == entry.id
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .tvTarget(targets, entry.id)
                                .focusLight(on, RoundedCornerShape(units.dp(12f)))
                                .padding(top = units.dp(8f), bottom = units.dp(6f)),
                        ) {
                            TvLineIcon(entry.icon, Color.White, Modifier.size(units.dp(21f)))
                            Spacer(Modifier.size(units.dp(4f)))
                            BasicText(entry.label, maxLines = 1, style = TextStyle(fontFamily = TvFont, fontSize = units.sp(13f), color = Color.White))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    val units = LocalTvUnits.current
    Box(
        Modifier
            .size(width = units.dp(280f), height = units.dp(1f))
            .background(Color.White.copy(alpha = 0.12f)),
    )
}

@Composable
private fun MenuRow(entry: MenuEntry, targets: TvTargets) {
    val units = LocalTvUnits.current
    val on = targets.focused == entry.id
    val grow by animateFloatAsState(if (on) 1.02f else 1f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "row")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .size(width = units.dp(280f), height = units.dp(46f))
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .tvTarget(targets, entry.id)
            .focusLight(on, RoundedCornerShape(units.dp(13f)))
            .padding(horizontal = units.dp(12f)),
    ) {
        TvLineIcon(entry.icon, Color.White, Modifier.size(units.dp(21f)))
        Spacer(Modifier.width(units.dp(14f)))
        BasicText(entry.label, maxLines = 1, style = TextStyle(fontFamily = TvFont, fontSize = units.sp(16f), color = Color.White))
    }
}

/** The light of the focus on a row, a tile or a button of a panel. */
@Composable
internal fun Modifier.focusLight(on: Boolean, shape: RoundedCornerShape): Modifier {
    val units = LocalTvUnits.current
    val lit by animateFloatAsState(if (on) 1f else 0f, tween(150), label = "light")
    return drawBehind {
        if (lit > 0f) {
            val outline = shape.createOutline(size, layoutDirection, this)
            if (outline is androidx.compose.ui.graphics.Outline.Rounded) {
                val path = androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }
                drawPath(path, Color.White.copy(alpha = 0.17f * lit))
                drawPath(path, Color.White.copy(alpha = 0.8f * lit), style = Stroke(units.px(1.5f) * 2f))
            }
        }
    }
}

// ---------- all apps ----------

@Composable
private fun TvAllApps(
    inputs: List<TvShown>,
    apps: List<TvShown>,
    placed: TvAllPlacement,
    focus: Int,
    onHome: Set<TvItem>,
    screenH: Float,
    repository: AppRepository?,
) {
    val units = LocalTvUnits.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(220)) }
    val moved by animateFloatAsState(
        if (focus >= 0) placed.scrollFor(focus, screenH - ALL_TOP) else 0f,
        spring(dampingRatio = 0.9f, stiffness = 300f),
        label = "allScroll",
    )
    val all = inputs + apps
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value
                val s = 1.02f - 0.02f * appear.value
                scaleX = s
                scaleY = s
            }
            .background(Color(0xE6142229)),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(start = units.dp(44f), top = units.dp(34f))) {
            BasicText(stringResource(R.string.tv_all_apps), style = TextStyle(fontFamily = TvFont, fontSize = units.sp(26f), color = Color.White))
            BasicText(
                stringResource(R.string.tv_all_caption),
                style = TextStyle(fontFamily = TvFont, fontSize = units.sp(13.5f), color = Color.White.copy(alpha = 0.72f)),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = units.dp(ALL_TOP))
                .graphicsLayer { clip = true },
        ) {
            Box(Modifier.graphicsLayer { translationY = -units.px(moved) }) {
                val section = TextStyle(
                    fontFamily = TvFont,
                    fontWeight = FontWeight(500),
                    fontSize = units.sp(12.5f),
                    letterSpacing = units.sp(1f),
                    color = Color.White.copy(alpha = 0.62f),
                )
                placed.inputsTitleTop?.let { top ->
                    BasicText(
                        stringResource(R.string.tv_section_inputs).uppercase(),
                        style = section,
                        modifier = Modifier.offset(units.dp(TvAllPlacement.SIDE), units.dp(top + 8f)),
                    )
                }
                BasicText(
                    stringResource(R.string.tv_section_apps).uppercase(),
                    style = section,
                    modifier = Modifier.offset(units.dp(TvAllPlacement.SIDE), units.dp(placed.appsTitleTop + 8f)),
                )
                all.forEachIndexed { index, item ->
                    val tile = placed.tiles.getOrNull(index) ?: return@forEachIndexed
                    androidx.compose.runtime.key(item.key) {
                        AllTile(item, tile, index == focus, item.item in onHome, repository)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllTile(item: TvShown, tile: TvBox, focused: Boolean, onHome: Boolean, repository: AppRepository?) {
    val units = LocalTvUnits.current
    val grow by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "tile")
    val check by animateFloatAsState(if (onHome) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "check")
    val pad = TvAllPlacement.TILE_PADDING
    val bannerW = tile.width - 2 * pad
    Box(
        Modifier
            .offset(units.dp(tile.left), units.dp(tile.top))
            .size(units.dp(tile.width), units.dp(tile.height))
            .zIndex(if (focused) 2f else 1f)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .focusLight(focused, RoundedCornerShape(units.dp(14f))),
    ) {
        TvBannerFace(
            item.art,
            units.dp(bannerW),
            repository,
            corner = units.dp(9f),
            modifier = Modifier.offset(units.dp(pad), units.dp(pad)),
        )
        BasicText(
            item.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = TvFont, fontSize = units.sp(13f), color = Color.White, textAlign = TextAlign.Center),
            modifier = Modifier
                .offset(units.dp(pad), units.dp(pad + TvArrange.heightOf(bannerW) + 6f))
                .width(units.dp(bannerW)),
        )
        if (check > 0f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(units.dp(-2f), units.dp(2f))
                    .size(units.dp(22f))
                    .graphicsLayer {
                        alpha = check.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * check
                        scaleY = 0.6f + 0.4f * check
                        shadowElevation = units.px(3f)
                        shape = CircleShape
                    }
                    .background(Color.White, CircleShape),
            ) {
                TvLineIcon(TvIcons.CHECK, Color(0xFF13252B), Modifier.size(units.dp(14f)), stroke = 2.6f)
            }
        }
    }
}

/**
 * The TV's home screen small, as it is now, for the settings: what changes there is seen
 * here at once. Nothing in it moves with the remote.
 */
@Composable
fun TvHomePreview(
    apps: List<AppEntry>,
    inputs: List<TvInput>?,
    tv: TvSettings,
    repository: AppRepository?,
    marketRows: @Composable () -> List<MarketRow>?,
    modifier: Modifier = Modifier,
) {
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val inputsById = remember(inputs) { inputs?.associateBy { it.id } }
    val allLabel = stringResource(R.string.tv_all_apps)
    val home = tv.home ?: TvHomeItems.EMPTY
    val shown = remember(home, appsByKey, inputsById, tv, allLabel) {
        home.items.mapNotNull { shownOf(it, appsByKey, inputsById, tv) } +
            TvShown(ALL_APPS, null, allLabel, BannerArt.AllApps(allLabel))
    }
    BoxWithConstraints(modifier.clearAndSetSemantics { }) {
        val density = LocalDensity.current
        val units = remember(maxWidth, density) { TvUnits(maxWidth / TvArrange.WIDTH, density) }
        val screenH = maxHeight / units.unit
        val widgets = tv.widgets.list
        val placement = remember(shown.size, tv.look, screenH, widgets.isNotEmpty()) {
            TvArrange.place(shown.size, tv.look, screenH, 0, 0f, withWidgets = widgets.isNotEmpty())
        }
        val widgetBoxes = remember(widgets, tv.look) { TvArrange.widgets(widgets.map { it.width }, tv.look.align, tv.look.arrangement) }
        CompositionLocalProvider(LocalTvUnits provides units, LocalTvInk provides tvInk(tv.look)) {
            TvBackdrop(tv.look, running = false, small = true)
            TvBanners(shown, placement, tv.look, focusIndex = 0, moving = null, repository = repository)
            TvWidgetsBand(widgets, widgetBoxes, focused = null, moving = null, marketRows = marketRows, shadow = tv.look.shadowAmount)
            TopBar(gearFocused = false)
        }
    }
}
