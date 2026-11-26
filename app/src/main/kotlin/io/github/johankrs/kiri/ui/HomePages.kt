package io.github.johankrs.kiri.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.layout.HomeItem
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.PageTiles
import io.github.johankrs.kiri.core.layout.TileBox
import io.github.johankrs.kiri.core.layout.addPage
import io.github.johankrs.kiri.core.layout.canAddPage
import io.github.johankrs.kiri.core.layout.isEmptyPage
import io.github.johankrs.kiri.core.layout.itemsOn
import io.github.johankrs.kiri.core.layout.movePage
import io.github.johankrs.kiri.core.layout.removePage
import io.github.johankrs.kiri.core.layout.startPage
import io.github.johankrs.kiri.core.layout.withMainPage
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.kiriGlass
import io.github.johankrs.kiri.widgets.KiriWidgets
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * "Pages": every page of the home screen at once, small, to add one, take one away, put them in
 * another order and choose the one Home goes back to. It is the home screen itself stepping
 * back: the page on show shrinks into its place among the others, and grows back out of it.
 */

/**
 * How far "Pages" is open, from the home screen (0) to every page small (1), and which page
 * grows out of it or shrinks into it ([focus]).
 */
@Stable
internal class PagesOpening(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** The page that comes out of the home screen and goes back into it. */
    var focus by mutableIntStateOf(0)

    /** From the moment it is asked for until it has closed again. */
    var shown by mutableStateOf(false)
        private set

    /**
     * The home screen is hidden under the pages: only once they have been drawn over it, so
     * that nothing is ever missing for a frame, and again just before they go.
     */
    var covering by mutableStateOf(false)
        private set

    /** Where it is heading: open, or back to the home screen. */
    var open by mutableStateOf(false)
        private set

    /** The page the home screen is to show once this closes, set as it starts to close. */
    var leavingTo by mutableStateOf<Int?>(null)
        private set

    /** The person's choice of speed, as a factor on the stiffness of the spring. */
    var stiffnessScale = 1f

    private var settling: Job? = null

    /**
     * Opens it, from the page [page] of the home screen. The small pages are put together
     * first, while the page on show is still drawn as large as the screen and looks just like
     * the home screen; only then does it start to move, so that the work of putting them
     * together never makes the movement skip.
     */
    fun show(page: Int) {
        if (shown) {
            settle(true)
            return
        }
        settling?.cancel()
        focus = page
        progress = 0f
        shown = true
        covering = false
        open = true
        leavingTo = null
        settling = scope.launch {
            withFrameNanos { }
            covering = true
            repeat(READY_FRAMES - 1) { withFrameNanos { } }
            glide(toOpen = true)
        }
    }

    /** Back to the home screen, on the page [page]. */
    fun enter(page: Int) {
        focus = page
        settle(false)
    }

    fun close() = settle(false)

    fun settle(toOpen: Boolean) {
        settling?.cancel()
        if (!shown) return
        open = toOpen
        leavingTo = if (toOpen) null else focus
        settling = scope.launch { glide(toOpen) }
    }

    private suspend fun glide(toOpen: Boolean) {
        animate(
            initialValue = progress,
            targetValue = if (toOpen) 1f else 0f,
            // Unhurried, and with no bounce: the pages come to rest, and so does the home screen.
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = (if (toOpen) OPEN_STIFFNESS else CLOSE_STIFFNESS) * stiffnessScale,
                visibilityThreshold = 0.001f,
            ),
        ) { value, _ -> progress = value }
        if (!toOpen) {
            // The home screen is drawn again under the page, just as it looks, and then the
            // pages go.
            covering = false
            withFrameNanos { }
            shown = false
        }
    }

    /** Gone at once, as when Home was pressed. */
    fun closeNow() {
        settling?.cancel()
        progress = 0f
        open = false
        covering = false
        shown = false
    }

    companion object {
        /** How quickly the home screen comes back; the wallpaper keeps the same pace. */
        const val CLOSE_STIFFNESS = Spring.StiffnessLow * 0.65f

        /** Frames drawn before the pages start to move: the first one puts them together. */
        private const val READY_FRAMES = 2

        private const val OPEN_STIFFNESS = Spring.StiffnessLow * 0.55f
    }
}

/**
 * The wallpaper while "Pages" is out. Hidden under the pages, the home screen may jump to
 * another page, or have pages added and taken away, and the wallpaper is not to jump with it:
 * it stays where it is while the pages are shown, and glides to where the home screen is as
 * they close. [move] puts the wallpaper where the pages say; [current] is where it is now, as a
 * share of the way along the pages.
 */
internal class PagesWallpaper(
    private val current: () -> Float,
    private val move: (position: Float, pages: Int) -> Unit,
) {
    private var position = 0f
    private var pages = 1
    private var along = 0.5f
    private var held = false
    private var gliding = false

    /** The home screen is at [position] of [pages]; while [covered] the wallpaper stays put. */
    fun report(position: Float, pages: Int, covered: Boolean) {
        this.position = position
        this.pages = pages
        if (covered) hold()
        if (!held) move(position, pages)
    }

    /** The pages came out: the wallpaper stays where it is. */
    fun hold() {
        if (held) return
        held = true
        along = current()
    }

    /** Goes where the home screen is, gliding, and follows it again once there. */
    suspend fun glideBack(stiffness: Float) {
        if (!held || gliding) return
        gliding = true
        try {
            var speed = 0f
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val step = ((now - last) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
                last = now
                // The page to go to may still change as the last edit is saved: follow it.
                val target = if (pages > 1) (position / (pages - 1)).coerceIn(0f, 1f) else 0.5f
                // A spring with no bounce, worked out a frame at a time.
                speed += (stiffness * (target - along) - 2f * sqrt(stiffness) * speed) * step
                along += speed * step
                if (abs(target - along) < 0.0005f && abs(speed) < 0.005f) break
                val room = maxOf(pages, 2)
                move(along * (room - 1), room)
            }
        } finally {
            gliding = false
            held = false
            move(position, pages)
        }
    }

    /** Gone at once (Home was pressed): back with the home screen, unless already gliding there. */
    fun release() {
        if (!held || gliding) return
        held = false
        move(position, pages)
    }
}

/**
 * A name for each page that stays with it while pages move, are added and are taken away, so
 * that a small page glides from where it was instead of jumping.
 */
private class PageKeys {
    val list = mutableListOf<Long>()
    private var next = 0L

    /** The page added last, to come out of where the place for a new page was. */
    var newest: Long? = null
        private set

    fun match(count: Int) {
        if (list.size == count) return
        list.clear()
        repeat(count) { list += next++ }
        newest = null
    }

    fun added() {
        val key = next++
        list += key
        newest = key
    }

    fun removed(index: Int) {
        list.removeAt(index)
    }

    fun moved(from: Int, to: Int) {
        list.add(to, list.removeAt(from))
    }
}

/** Where a small page rests, gliding when the order or the room changes. */
@Stable
private class TileSpot(start: TileBox) {
    val corner = Animatable(Offset(start.x, start.y), Offset.VectorConverter)
    val width = Animatable(start.width)

    suspend fun glideTo(box: TileBox, stiffnessScale: Float) = coroutineScope {
        launch { corner.animateTo(Offset(box.x, box.y), spotSpring(stiffnessScale, Offset(0.5f, 0.5f))) }
        launch { width.animateTo(box.width, spotSpring(stiffnessScale, 0.5f)) }
    }

    suspend fun snapTo(corner: Offset) {
        this.corner.snapTo(corner)
    }
}

private fun <T> spotSpring(stiffnessScale: Float, threshold: T) = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow * stiffnessScale,
    visibilityThreshold = threshold,
)

/** A page held by the finger: which, where it was picked up, and where in the order it is now. */
private class Held(val key: Long, val grab: Offset, val at: Int)

/** What the touches need to know, kept up to date at every pass without being observed. */
private class PagesNow {
    var keys: List<Long> = emptyList()
    var tiles: List<TileBox> = emptyList()
    var count = 1
    var asking: Long? = null
}

/**
 * Every page of the home screen, small, over the wallpaper. [layout] is the home screen as it
 * is shown; [onEdit] saves a change to it, with what the change takes off the home screen.
 * [onEnter] is told which page the home screen shows once this closes.
 */
@Composable
internal fun PagesOverlay(
    opening: PagesOpening,
    layout: HomeLayout,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    widgets: KiriWidgets?,
    onEdit: (change: (HomeLayout) -> HomeLayout, gone: List<HomeItem>) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // A change is shown at once, without waiting for it to be saved and read back.
    var optimistic by remember { mutableStateOf<HomeLayout?>(null) }
    val lastLayout = remember { arrayOfNulls<HomeLayout>(1) }
    if (lastLayout[0] !== layout) {
        lastLayout[0] = layout
        if (optimistic == layout) optimistic = null
    }
    val shown = optimistic ?: layout
    val count = shown.pageCount
    val keys = remember { PageKeys() }
    keys.match(count)
    val canAdd = shown.canAddPage
    val mainPage = shown.startPage

    var held by remember { mutableStateOf<Held?>(null) }
    // Where the held page's corner is: read only while placing, so dragging recomposes nothing.
    val heldCorner = remember { mutableStateOf(Offset.Zero) }
    val raise = remember { Animatable(0f) }
    var asking by remember { mutableStateOf<Long?>(null) }
    var leaving by remember { mutableStateOf<Long?>(null) }
    val leave = remember { Animatable(0f) }
    val spots = remember { HashMap<Long, TileSpot>() }
    val now = remember { PagesNow() }

    // The order shown: the held page goes where the finger has taken it.
    val order: List<Long> = held?.let { holding ->
        keys.list.toMutableList().apply { remove(holding.key); add(holding.at.coerceIn(0, size), holding.key) }
    } ?: keys.list.toList()

    // Something changes the pages: shown at once, saved, and the page on show followed.
    val apply: (change: (HomeLayout) -> HomeLayout, gone: List<HomeItem>, follow: (PageKeys) -> Unit) -> Unit =
        { change, gone, follow ->
            val before = optimistic ?: layout
            val next = change(before)
            if (next != before) {
                val focusKey = keys.list.getOrNull(opening.focus)
                follow(keys)
                optimistic = next
                val focusNow = focusKey?.let { keys.list.indexOf(it) } ?: -1
                opening.focus = if (focusNow >= 0) focusNow else opening.focus.coerceIn(0, keys.list.size - 1)
                onEdit(change, gone)
            }
        }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val bars = WindowInsets.safeDrawing.asPaddingValues()
        val direction = LocalLayoutDirection.current
        val aspect = if (width > 0f) height / width else 1f
        val tiles = with(density) {
            val top = bars.calculateTopPadding().toPx() + HEADER.toPx()
            val bottom = bars.calculateBottomPadding().toPx() + 20.dp.toPx()
            val side = maxOf(bars.calculateLeftPadding(direction).toPx(), bars.calculateRightPadding(direction).toPx()) + 16.dp.toPx()
            remember(count, canAdd, width, height, top, bottom, side) {
                PageTiles.arrange(
                    count = count + if (canAdd) 1 else 0,
                    left = side,
                    top = top,
                    areaWidth = width - 2 * side,
                    areaHeight = height - top - bottom,
                    aspect = aspect,
                    gapX = 12.dp.toPx(),
                    gapY = 12.dp.toPx(),
                    below = BUTTONS.toPx(),
                    maxWidth = width * 0.46f,
                )
            }
        }
        now.keys = order
        now.tiles = tiles
        now.count = count
        now.asking = asking
        val stiffness = opening.stiffnessScale

        val addSpot = remember { arrayOfNulls<TileSpot>(1) }
        val tileOf: (Long) -> TileBox = { key -> tiles[order.indexOf(key).coerceIn(0, tiles.size - 1)] }

        // Where a page is drawn now: between its place on the home screen and its place here.
        // Where a page is drawn now. The page on show goes between the whole screen and its
        // place here; the others stay in their places and only come and go there.
        fun rectOf(index: Int, key: Long, spot: TileSpot): Rect {
            val holding = held?.takeIf { it.key == key }
            val corner = if (holding != null) heldCorner.value else spot.corner.value
            val w = if (holding != null) tileOf(key).width else spot.width.value
            if (index != opening.focus) return Rect(corner, Size(w, w * aspect))
            val t = opening.progress
            val tileWidth = lerp(width, w, t)
            return Rect(Offset(lerp(0f, corner.x, t), lerp(0f, corner.y, t)), Size(tileWidth, tileWidth * aspect))
        }

        val chrome: () -> Float = { smoothStep(0.5f, 1f, opening.progress) }

        Box(
            Modifier
                .fillMaxSize()
                // The wallpaper dims a little as the pages step back.
                .drawBehind { drawRect(Scrim.copy(alpha = SCRIM * opening.progress.coerceIn(0f, 1f))) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val settled = opening.open && opening.progress > 0.97f
                        val hit = if (settled) now.tileAt(down.position) else null
                        var moved = false
                        val canLift = hit != null && now.count > 1 && hit.first != now.asking && leaving == null
                        val first = withTimeoutOrNull(if (canLift) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE / 2) {
                            var seen: PageTouch? = null
                            while (seen == null) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                seen = when {
                                    // A second finger: nothing here answers two fingers.
                                    event.changes.count { it.pressed } >= 2 -> PageTouch.NONE
                                    change == null -> PageTouch.NONE
                                    // A button under the finger took the tap.
                                    !change.pressed -> if (moved || change.isConsumed) PageTouch.NONE else PageTouch.TAP
                                    else -> {
                                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                        null
                                    }
                                }
                            }
                            seen
                        } ?: if (moved) PageTouch.NONE else PageTouch.LIFT

                        when (first) {
                            PageTouch.TAP -> when {
                                !settled -> Unit
                                hit != null && hit.first == now.asking -> Unit
                                hit != null -> {
                                    asking = null
                                    opening.enter(keys.list.indexOf(hit.first).coerceAtLeast(0))
                                }
                                now.asking != null -> asking = null
                                else -> opening.close()
                            }

                            PageTouch.LIFT -> {
                                val (key, box) = hit ?: return@awaitEachGesture
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                asking = null
                                val from = now.keys.indexOf(key)
                                heldCorner.value = Offset(box.x, box.y)
                                held = Held(key, down.position - Offset(box.x, box.y), from)
                                scope.launch { raise.animateTo(1f, spotSpring(stiffness, 0.001f)) }
                                var at = from
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    val corner = change.position - (held?.grab ?: Offset.Zero)
                                    heldCorner.value = corner
                                    val middle = corner + Offset(box.width / 2f, box.height / 2f)
                                    val nearest = now.nearestPage(middle)
                                    if (nearest != at) {
                                        at = nearest
                                        held = Held(key, held?.grab ?: Offset.Zero, at)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                // Let go: it glides into its place, and the order is saved.
                                val spot = spots[key]
                                val drop = heldCorner.value
                                held = null
                                scope.launch { raise.animateTo(0f, spotSpring(stiffness, 0.001f)) }
                                val start = keys.list.indexOf(key)
                                if (start >= 0 && start != at) {
                                    apply({ it.movePage(start, at) }, emptyList()) { it.moved(start, at) }
                                }
                                if (spot != null) {
                                    scope.launch {
                                        spot.snapTo(drop)
                                        spot.glideTo(now.tiles[at.coerceIn(0, now.tiles.size - 1)], stiffness)
                                    }
                                }
                            }

                            PageTouch.NONE -> Unit
                        }
                    }
                },
        ) {
            // The title, over the pages; it comes last.
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 22.dp, top = 14.dp, end = 22.dp)
                    .graphicsLayer {
                        val shownNow = smoothStep(0.6f, 1f, opening.progress)
                        alpha = shownNow
                        translationY = (1f - shownNow) * 10.dp.toPx()
                    },
            ) {
                BasicText(
                    text = stringResource(R.string.pages_title),
                    style = KiriType.title.copy(color = Color.White, shadow = OnWallpaper),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.pages_hint),
                    style = KiriType.caption.copy(color = Color.White.copy(alpha = 0.72f), shadow = OnWallpaper),
                )
            }

            // The place for a new page, after the last one.
            if (canAdd) {
                val target = tiles.last()
                val spot = addSpot[0] ?: TileSpot(target).also { addSpot[0] = it }
                LaunchedEffect(target) { spot.glideTo(target, stiffness) }
                val addLabel = stringResource(R.string.pages_add)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .placed { Rect(spot.corner.value, Size(spot.width.value, spot.width.value * aspect)) }
                        .graphicsLayer {
                            val shownNow = smoothStep(0.55f, 1f, opening.progress)
                            alpha = shownNow
                            val scale = lerp(0.86f, 1f, shownNow)
                            scaleX = scale
                            scaleY = scale
                        }
                        .kiriGlass(GlassStyle.FUME, RoundedCornerShape(TILE_CORNER), PLATE_OPACITY, vivid = true)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = addLabel,
                        ) {
                            if (!opening.open || leaving != null) return@clickable
                            asking = null
                            apply({ it.addPage() }, emptyList()) { it.added() }
                        }
                        .semantics { contentDescription = addLabel },
                ) {
                    GlyphIcon(Glyph.PLUS, Color.White, Modifier.size(28.dp))
                }
                BasicText(
                    text = stringResource(R.string.pages_new),
                    style = KiriType.caption.copy(color = Color.White, shadow = OnWallpaper, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .placed {
                            val tileWidth = spot.width.value
                            Rect(
                                Offset(spot.corner.value.x, spot.corner.value.y + tileWidth * aspect),
                                Size(tileWidth, BUTTONS.toPx()),
                            )
                        }
                        .graphicsLayer { alpha = smoothStep(0.55f, 1f, opening.progress) }
                        .padding(top = 10.dp),
                )
            } else {
                addSpot[0] = null
            }

            for ((index, key) in keys.list.withIndex()) {
                key(key) {
                    val target = tileOf(key)
                    val spot = spots.getOrPut(key) {
                        val from = addSpot[0]?.takeIf { key == keys.newest }
                        if (from != null) {
                            TileSpot(TileBox(from.corner.value.x, from.corner.value.y, from.width.value, from.width.value * aspect))
                        } else {
                            TileSpot(target)
                        }
                    }
                    LaunchedEffect(target) { spot.glideTo(target, stiffness) }
                    PageTile(
                        layout = shown,
                        page = index,
                        isMain = index == mainPage,
                        isEmpty = shown.isEmptyPage(index),
                        asking = asking == key,
                        rect = { rectOf(index, key, spot) },
                        progress = { opening.progress },
                        chrome = {
                            when {
                                leaving == key -> 0f
                                held == null -> chrome()
                                held?.key == key -> 0f
                                else -> chrome() * 0.4f
                            }
                        },
                        appear = { if (index == opening.focus) 1f else smoothStep(APPEAR_FROM, APPEAR_TO, opening.progress) },
                        // The page in the hand above all, then the one going to or from the screen.
                        layer = when {
                            held?.key == key -> 2f
                            index == opening.focus -> 1f
                            else -> 0f
                        },
                        lift = { if (held?.key == key || (held == null && raise.value > 0f && spot.corner.isRunning)) raise.value else 0f },
                        leave = { if (leaving == key) leave.value else 0f },
                        screen = Size(width, height),
                        appsByKey = appsByKey,
                        spec = spec,
                        repository = repository,
                        widgets = widgets,
                        canRemove = count > 1,
                        onMakeMain = {
                            if (opening.open) apply({ it.withMainPage(keys.list.indexOf(key)) }, emptyList()) { }
                        },
                        onRemove = remove@{
                            if (!opening.open || leaving != null || count <= 1) return@remove
                            val page = keys.list.indexOf(key)
                            if ((optimistic ?: layout).isEmptyPage(page) || asking == key) {
                                asking = null
                                leaving = key
                                scope.launch {
                                    leave.snapTo(0f)
                                    leave.animateTo(1f, spring(stiffness = Spring.StiffnessMedium * stiffness, visibilityThreshold = 0.001f))
                                    val at = keys.list.indexOf(key)
                                    if (at >= 0) {
                                        val gone = (optimistic ?: layout).itemsOn(at)
                                        apply({ it.removePage(at) }, gone) { it.removed(at) }
                                    }
                                    spots.remove(key)
                                    leaving = null
                                }
                            } else {
                                asking = key
                            }
                        },
                        onKeep = { asking = null },
                    )
                }
            }
        }
    }
}

/** The first thing a touch on "Pages" turned out to be. */
private enum class PageTouch { TAP, LIFT, NONE }

/** The small page under [point], with its place. */
private fun PagesNow.tileAt(point: Offset): Pair<Long, TileBox>? {
    for (index in keys.indices.reversed()) {
        val box = tiles.getOrNull(index) ?: continue
        if (point.x >= box.x && point.x <= box.x + box.width && point.y >= box.y && point.y <= box.y + box.height) {
            return keys[index] to box
        }
    }
    return null
}

/** The place among the pages (not the one for a new page) nearest to [point]. */
private fun PagesNow.nearestPage(point: Offset): Int {
    var best = 0
    var bestDistance = Float.MAX_VALUE
    for (index in 0 until count) {
        val box = tiles.getOrNull(index) ?: continue
        val distance = (Offset(box.x + box.width / 2f, box.y + box.height / 2f) - point).getDistance()
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/**
 * One small page: a picture of the page on glass, with what is under it: the house that makes
 * it the start page and the bin that takes it away, together in the middle.
 */
@Composable
private fun PageTile(
    layout: HomeLayout,
    page: Int,
    isMain: Boolean,
    isEmpty: Boolean,
    asking: Boolean,
    rect: () -> Rect,
    progress: () -> Float,
    chrome: () -> Float,
    appear: () -> Float,
    layer: Float,
    lift: () -> Float,
    leave: () -> Float,
    screen: Size,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    widgets: KiriWidgets?,
    canRemove: Boolean,
    onMakeMain: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    val corner = TILE_CORNER
    val shape = RoundedCornerShape(corner)
    val name = stringResource(R.string.pages_page, page + 1)
    // The page itself: drawn as large as the screen and made smaller as a whole.
    Box(
        Modifier
            .zIndex(layer)
            .placed { rect() }
            .semantics { contentDescription = name }
            .graphicsLayer {
                val raised = 1f + 0.06f * lift()
                val gone = leave()
                val shownNow = appear()
                val scale = raised * (1f - 0.16f * gone) * lerp(0.9f, 1f, shownNow)
                scaleX = scale
                scaleY = scale
                alpha = (1f - gone) * shownNow
            },
    ) {
        // The glass under it, which comes with the buttons.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chrome().coerceAtLeast(lift()) }
                .kiriGlass(GlassStyle.FUME, shape, PLATE_OPACITY, vivid = true),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Square on the screen, rounded once small.
                    val round = smoothStep(0f, 1f, progress()) * corner.toPx()
                    this.shape = RoundedCornerShape(round)
                    clip = true
                },
        ) {
            HomePagePicture(
                layout = layout,
                page = page,
                appsByKey = appsByKey,
                spec = spec,
                repository = repository,
                widgets = widgets,
                modifier = Modifier.layout { measurable, constraints ->
                    val full = measurable.measure(Constraints.fixed(screen.width.roundToInt(), screen.height.roundToInt()))
                    val scale = if (screen.width > 0f) constraints.maxWidth / screen.width else 1f
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        full.placeWithLayer(0, 0) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                },
            )
        }
        if (isEmpty) {
            BasicText(
                text = stringResource(R.string.pages_empty),
                style = KiriType.caption.copy(color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = chrome() },
            )
        }
        // Asked before a page with something on it goes: on the page itself.
        val ask by animateFloatAsState(if (asking) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "ask")
        if (ask > 0.001f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ask }
                    .drawBehind { drawRoundRect(AskShade, cornerRadius = CornerRadius(corner.toPx())) }
                    .padding(10.dp),
            ) {
                BasicText(
                    text = stringResource(R.string.pages_ask),
                    style = KiriType.caption.copy(color = Color.White, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.pages_ask_note),
                    style = KiriType.caption.copy(color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontSize = KiriType.caption.fontSize * 0.88f),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(stringResource(R.string.pages_ask_yes), Warn, enabled = asking, onClick = onRemove)
                TextButton(stringResource(R.string.pages_ask_no), Color.White, enabled = asking, onClick = onKeep)
            }
        }
    }

    // The house and the bin, together under the middle of the page.
    val number = page + 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .zIndex(layer)
            .placed {
                val box = rect()
                Rect(Offset(box.left, box.bottom), Size(box.width, BUTTONS.toPx()))
            }
            .graphicsLayer {
                val shownNow = chrome() * appear()
                alpha = shownNow
                // Out of the way of touches while it cannot be seen.
                if (shownNow < 0.02f) translationY = -100_000f
            },
    ) {
        val mainLabel = if (isMain) stringResource(R.string.pages_is_main, number) else stringResource(R.string.pages_make_main, number)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onMakeMain,
                )
                .semantics { contentDescription = mainLabel },
        ) {
            HouseGlyph(chosen = isMain)
        }
        val removeLabel = stringResource(R.string.pages_remove, number)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { alpha = if (canRemove) 1f else 0.3f }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    enabled = canRemove,
                    onClick = onRemove,
                )
                .semantics { contentDescription = removeLabel },
        ) {
            GlyphIcon(Glyph.UNINSTALL, Color.White)
        }
    }
}

/** A word that is a button, without a background: the Kiri way. */
@Composable
private fun TextButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = KiriType.body.copy(color = color, textAlign = TextAlign.Center),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * A house: the start page. The chosen one is drawn heavier and brighter, with a short line
 * under it, the way a choice is marked in Kiri; it changes over with a spring.
 */
@Composable
private fun HouseGlyph(chosen: Boolean) {
    val weight by animateFloatAsState(if (chosen) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "main")
    Canvas(Modifier.size(22.dp)) {
        val stroke = lerp(1.5f, 2.3f, weight).dp.toPx()
        val color = Color.White.copy(alpha = lerp(0.62f, 1f, weight))
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(0.19f * w, 0.47f * h)
            lineTo(0.5f * w, 0.2f * h)
            lineTo(0.81f * w, 0.47f * h)
            lineTo(0.81f * w, 0.8f * h)
            lineTo(0.19f * w, 0.8f * h)
            close()
        }
        drawPath(roof, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(0.42f * w, 0.8f * h), Offset(0.42f * w, 0.6f * h), stroke, StrokeCap.Round)
        drawLine(color, Offset(0.42f * w, 0.6f * h), Offset(0.58f * w, 0.6f * h), stroke, StrokeCap.Round)
        drawLine(color, Offset(0.58f * w, 0.6f * h), Offset(0.58f * w, 0.8f * h), stroke, StrokeCap.Round)
        if (weight > 0.01f) {
            val half = 0.17f * w * (0.3f + 0.7f * weight)
            drawLine(
                color = Color.White.copy(alpha = weight),
                start = Offset(0.5f * w - half, 0.98f * h),
                end = Offset(0.5f * w + half, 0.98f * h),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Puts this element where [rect] says, in the coordinates of the parent, at that size. Read
 * while placing, so a moving element costs no recomposition.
 */
private fun Modifier.placed(rect: androidx.compose.ui.unit.Density.() -> Rect): Modifier = this
    .layout { measurable, _ ->
        val box = rect()
        val width = box.width.roundToInt().coerceAtLeast(1)
        val height = box.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
    .offsetBy(rect)

private fun Modifier.offsetBy(rect: androidx.compose.ui.unit.Density.() -> Rect): Modifier =
    offset { rect().topLeft.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** Room for the title above the pages, under the status bar. */
private val HEADER = 88.dp

/** Room under each page for its buttons. */
private val BUTTONS = 40.dp

/** How round the corners of a small page are. */
private val TILE_CORNER = 14.dp

/** How much tint the glass under the small pages gets. */
private const val PLATE_OPACITY = 20

/** How much the wallpaper darkens behind the pages, at most. */
private const val SCRIM = 0.28f

/** The pages other than the one on show appear in their places over this part of the way. */
private const val APPEAR_FROM = 0.4f
private const val APPEAR_TO = 0.92f

private val Scrim = Color(0xFF040C10)
private val AskShade = Color(0xD90A1A20)
private val Warn = Color(0xFFFFB4A6)
