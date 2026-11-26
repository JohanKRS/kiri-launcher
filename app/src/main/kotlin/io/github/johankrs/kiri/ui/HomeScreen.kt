package io.github.johankrs.kiri.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.HomeGridGeometry
import io.github.johankrs.kiri.core.HomeRoom
import io.github.johankrs.kiri.core.gesture.Gesture
import io.github.johankrs.kiri.core.layout.CellPosition
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.GridSize
import io.github.johankrs.kiri.core.layout.HomeItem
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.canMerge
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.appLabelStyle
import io.github.johankrs.kiri.widgets.KiriWidgets
import io.github.johankrs.kiri.widgets.system.SystemWidgetFace
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Text drawn straight on the wallpaper: white, with a soft shadow so it reads on any picture. */
internal val OnWallpaper = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 4f)

/** How app names are written on the home screen. */
/** The style of the name of an app over the wallpaper, in the look the person chose. */
@Composable
@ReadOnlyComposable
internal fun homeLabelStyle(): TextStyle = appLabelStyle().copy(color = Color.White, shadow = OnWallpaper)

/** An item picked up by a long press: what it is and where its cell was, in root coordinates. */
private class Lift(val item: HomeItem, val cellBounds: Rect)

/**
 * Where the cells of the home screen are on screen, kept up to date by [HomeSurface]. It lets
 * something carried in from outside, such as an app dragged out of the drawer, find the cell
 * under it. Plain values on purpose: they are read at the moment of a drop, never observed.
 */
@Stable
class HomeDropZone {
    internal var columns = 1
    internal var rows = 1

    /** The size of one cell, and the room between cells side by side and one under the other. */
    internal var cellPx = Size.Zero
    internal var gapXPx = 0f
    internal var gapYPx = 0f

    /** The page currently shown under the finger, kept live while pages turn. */
    var currentPage = 0
        internal set

    /** Top-left corner of the home screen, in root coordinates, and its width. */
    internal var surfaceOrigin = Offset.Zero
    internal var surfaceWidth = 0

    /**
     * Where the first cell of some page starts, measured from the home screen. Any page may
     * report it, also one waiting beside the page on show.
     */
    internal var gridOffset = Offset.Zero

    /**
     * Where the grid of the page on show starts, in root coordinates. Pages sit side by side, a
     * whole screen apart, so the remainder of that distance is where each one's grid starts.
     */
    private val gridOrigin: Offset
        get() {
            val width = surfaceWidth.toFloat()
            val x = if (width > 0f) ((gridOffset.x % width) + width) % width else gridOffset.x
            return surfaceOrigin + Offset(x, gridOffset.y)
        }

    /** The size of one cell, in pixels. Zero until the home screen was laid out. */
    val cellSize: Size get() = cellPx

    /** The cell of [page] under [point] (root coordinates), kept inside the grid. */
    fun cellAt(point: Offset, page: Int = currentPage): CellPosition? {
        val cell = cellSize
        val pitchX = cell.width + gapXPx
        val pitchY = cell.height + gapYPx
        if (cell.width <= 0f || cell.height <= 0f || pitchX <= 0f || pitchY <= 0f) return null
        // Each cell has half the room around it, so the nearest cell is the one found.
        val inGrid = point - gridOrigin
        return CellPosition(
            page = page,
            column = floor((inGrid.x + gapXPx / 2f) / pitchX).toInt().coerceIn(0, columns - 1),
            row = floor((inGrid.y + gapYPx / 2f) / pitchY).toInt().coerceIn(0, rows - 1),
        )
    }

    /** The middle of [cell] on screen, in root coordinates, as when its page is the one on show. */
    fun centerOf(cell: CellPosition): Offset {
        val size = cellSize
        return gridOrigin + Offset(
            x = cell.column * (size.width + gapXPx) + size.width / 2f,
            y = cell.row * (size.height + gapYPx) + size.height / 2f,
        )
    }
}

/**
 * A carried item after the finger let go: it glides from where it was released ([offset] starts
 * there) to [to], both measured from the cell it was lifted from. Going [into] a folder, or into
 * an app to make one, it gets smaller on the way, as if it went inside.
 */
private class Landing(val lift: Lift, private val from: Offset, val to: Offset, val into: Boolean = false) {
    val offset = Animatable(from, Offset.VectorConverter)

    /** How much of the way it has come, from 0 to 1. Read while drawing. */
    val travelled: Float
        get() {
            val whole = (to - from).getDistance()
            return if (whole < 1f) 1f else (1f - (to - offset.value).getDistance() / whole).coerceIn(0f, 1f)
        }
}

/** Where a carried item ends up: its new cell, and how far that is from the cell it left. */
private class Drop(val cell: CellPosition, val rest: Offset)

/** The room between an icon and its name, as an app draws it (see `AppCell`). */
private const val NAME_GAP_DP = 6f

/** Moves things into place the way the whole launcher does: quick, soft, no bounce at the end. */
private fun <T> glide(visibilityThreshold: T): SpringSpec<T> = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = visibilityThreshold,
)

/**
 * The home screen: pages of items over the wallpaper, each item in its own cell.
 *
 * Swiping sideways changes page. Swipes up and down, a double tap and a long press on an empty
 * place are gestures. A swipe first asks [onSwipeStart] whether someone wants to follow the
 * finger; if so it reports every movement to [onSwipeMove] and the release to [onSwipeEnd], so
 * that what it opens can move with the hand. Otherwise, and for the other gestures, it reports
 * [onGesture] once the gesture is complete, with where on screen it was made when it has a
 * place (a tap has one, a swipe does not), in root coordinates.
 *
 * A long press on an item reports [onItemLongPress] with where the item is on screen; if the
 * finger then moves, the item is carried along and dropped where it is released, which reports
 * [onMoveItem]. Holding it at the left or right edge turns the page, and one empty page is
 * always on offer at the end while carrying.
 *
 * Something carried in from outside (an app dragged out of the drawer) is announced by
 * [incomingPointer], which tells where the finger is: the pages then turn at the edges for it
 * too, and [dropZone] tells its owner which cell is under the finger.
 *
 * [onPagePosition] is told where the pages are while they turn, fractions included.
 *
 * An app dropped right on another app or on a folder goes into it: [onMergeItem] makes that so
 * and returns the layout it made, which is shown at once. Without it, items only move.
 *
 * A new [page] is turned to, unless [jumpToPage] says to be there at once (while something
 * else covers the home screen).
 *
 * With [interactive] off it is a picture of page [page], used by the settings as a live preview.
 *
 * [layout] must already fit the grid of [spec] (see `HomeLayout.visibleOn`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeSurface(
    layout: HomeLayout,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    interactive: Boolean,
    page: Int,
    onPageChange: (Int) -> Unit,
    onLaunch: (app: AppEntry, iconBounds: Rect?) -> Unit,
    onItemLongPress: (item: HomeItem, anchor: Rect) -> Unit,
    onDragStart: () -> Unit,
    onMoveItem: (id: Long, to: CellPosition) -> Unit,
    onGesture: (gesture: Gesture, at: Offset?) -> Unit,
    onSwipeStart: (Gesture) -> Boolean,
    onSwipeMove: (alongPx: Float) -> Unit,
    onSwipeEnd: (velocityAlongPx: Float) -> Unit,
    emptyHint: @Composable () -> String,
    modifier: Modifier = Modifier,
    widgets: KiriWidgets? = null,
    dropZone: HomeDropZone? = null,
    incomingPointer: (() -> Offset)? = null,
    onPagePosition: ((position: Float, pages: Int) -> Unit)? = null,
    onMergeItem: ((liftedId: Long, targetId: Long) -> HomeLayout?)? = null,
    jumpToPage: () -> Boolean = { false },
    onRoom: ((HomeRoom) -> Unit)? = null,
) {
    val labelStyle = homeLabelStyle()

    // After a drop the result is shown at once, without waiting for it to be saved and read
    // back. The saved layout replaces it as soon as it arrives.
    var optimistic by remember { mutableStateOf<HomeLayout?>(null) }
    LaunchedEffect(layout) { optimistic = null }
    LaunchedEffect(optimistic) {
        if (optimistic != null) {
            delay(2_000)
            optimistic = null
        }
    }
    val shownLayout = optimistic ?: layout

    var lift by remember { mutableStateOf<Lift?>(null) }
    // True once the lifted item actually started moving with the finger.
    var dragging by remember { mutableStateOf(false) }
    // How far the finger has carried it. Read only while placing, so moving costs no recomposition.
    val dragOffset = remember { mutableStateOf(Offset.Zero) }
    var landing by remember { mutableStateOf<Landing?>(null) }
    // The app or folder the carried app would go into if let go now.
    var mergeTarget by remember { mutableStateOf<Long?>(null) }
    // -1 or +1 while the item is held at the left or right edge of the screen.
    var edgeDirection by remember { mutableIntStateOf(0) }
    var surfaceOrigin by remember { mutableStateOf(Offset.Zero) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    // Something is in the air, from here or from outside: the pages make room and turn for it.
    val carrying = dragging || incomingPointer != null

    val pageCount = shownLayout.pageCount.coerceAtLeast(1) + if (carrying) 1 else 0
    val itemsByPage = remember(shownLayout) { shownLayout.items.groupBy { it.position.page } }
    // Every page reserves the same height for names, so the rows line up from page to page.
    val folderLook = LocalFolderLook.current
    val folderDefault = stringResource(R.string.folder_default)
    val labelsOnHome = remember(shownLayout, appsByKey, folderLook.showName, folderDefault) {
        labelsOf(shownLayout, appsByKey, folderLook.showName, folderDefault)
    }

    if (!interactive) {
        val shown = page.coerceIn(0, pageCount - 1)
        Box(modifier.fillMaxSize()) {
            HomePage(
                items = itemsByPage[shown].orEmpty(),
                appsByKey = appsByKey,
                labelsOnHome = labelsOnHome,
                spec = spec,
                widgets = widgets,
                repository = repository,
                labelStyle = labelStyle,
                interactive = false,
                hiddenItemId = null,
                onLaunch = { _, _ -> },
                onItemLift = { _, _, _ -> },
                dropZone = null,
                onRoom = onRoom,
            )
            // The preview keeps them lit, to show where they will be when the pages turn.
            PageDots(count = pageCount, selected = { shown }, spec = spec, interactive = false, lit = { 1f })
        }
        return
    }

    val gesture by rememberUpdatedState(onGesture)
    val swipeStart by rememberUpdatedState(onSwipeStart)
    val swipeMove by rememberUpdatedState(onSwipeMove)
    val swipeEnd by rememberUpdatedState(onSwipeEnd)
    val pageChanged by rememberUpdatedState(onPageChange)
    val dragStarted by rememberUpdatedState(onDragStart)
    val moveItem by rememberUpdatedState(onMoveItem)
    val mergeItem by rememberUpdatedState(onMergeItem)
    val currentLayout by rememberUpdatedState(shownLayout)
    val currentSpec by rememberUpdatedState(spec)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    val edgeWidthPx = with(density) { 28.dp.toPx() }
    // The cells as the pages laid them out, for carrying items between them.
    val cells = dropZone ?: remember { HomeDropZone() }
    SideEffect {
        cells.columns = spec.columns
        cells.rows = spec.rows
    }

    val pagerState = rememberPagerState(initialPage = page.coerceIn(0, pageCount - 1)) { pageCount }
    // Report settled pages so a multi-page animateScrollToPage(0) is never cancelled mid-flight.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { pageChanged(it) }
    }
    // Where the pages are, fractions included, for whoever follows them (the wallpaper). The
    // extra page that appears while something is carried does not count.
    val pagePosition by rememberUpdatedState(onPagePosition)
    val realPages by rememberUpdatedState(shownLayout.pageCount.coerceAtLeast(1))
    LaunchedEffect(pagerState, dropZone) {
        snapshotFlow {
            val current = pagerState.currentPage
            dropZone?.currentPage = current
            (current + pagerState.currentPageOffsetFraction) to realPages
        }.collect { (position, pages) -> pagePosition?.invoke(position, pages) }
    }
    // The icons are told how the pages are turned, to lean and catch the light as they go.
    val glass = rememberGlassMotion(horizontal = true)
    LaunchedEffect(pagerState, glass) {
        var last = Float.NaN
        snapshotFlow {
            val pageSize = pagerState.layoutInfo.pageSize
            if (pageSize <= 0) {
                Float.NaN
            } else {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction) * pageSize
            }
        }.collect { now ->
            if (!now.isNaN()) {
                if (!last.isNaN()) glass.moved(now - last)
                last = now
            }
        }
    }
    // Someone else asked for a page: Home was pressed, or an app was just added to that page.
    LaunchedEffect(page, pageCount) {
        if (page in 0 until pageCount && page != pagerState.currentPage) {
            if (jumpToPage()) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
        }
    }
    // Something carried in from outside: watch whether it is held at an edge.
    LaunchedEffect(incomingPointer) {
        val pointer = incomingPointer ?: return@LaunchedEffect
        try {
            snapshotFlow { pointer().x - surfaceOrigin.x }.collect { x ->
                edgeDirection = when {
                    x < edgeWidthPx -> -1
                    x > surfaceWidth - edgeWidthPx -> 1
                    else -> 0
                }
            }
        } finally {
            edgeDirection = 0
        }
    }
    // Held at an edge while carrying an item: keep turning pages that way.
    LaunchedEffect(carrying, edgeDirection) {
        if (carrying && edgeDirection != 0) {
            while (true) {
                delay(650)
                val target = pagerState.currentPage + edgeDirection
                if (target !in 0 until pagerState.pageCount) break
                pagerState.animateScrollToPage(target)
            }
        }
    }
    // Let go: the carried item glides into its cell, then the real one takes over.
    LaunchedEffect(landing) {
        val current = landing ?: return@LaunchedEffect
        current.offset.animateTo(current.to, glide(Offset(0.5f, 0.5f)))
        if (landing === current) landing = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                surfaceOrigin = it.positionInRoot()
                surfaceWidth = it.size.width
                dropZone?.surfaceOrigin = it.positionInRoot()
                dropZone?.surfaceWidth = it.size.width
            }
            // Carrying a lifted item. This looks at the touches before the pages and the icons
            // do, and once the item moves it keeps them for itself so nothing else reacts.
            .pointerInput(edgeWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    lift = null
                    var previous = down.position
                    var liftedAt: Offset? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val lifted = lift
                            if (lifted == null) {
                                if (!change.pressed) break
                                previous = change.position
                                continue
                            }
                            val start = liftedAt ?: previous.also { liftedAt = it }
                            val moved = change.position - start
                            if (!dragging && change.pressed && moved.getDistance() > viewConfiguration.touchSlop) {
                                dragStarted()
                                landing = null
                                dragOffset.value = moved
                                dragging = true
                            }
                            if (!dragging) {
                                if (!change.pressed) break
                                continue
                            }
                            change.consume()
                            val into = if (mergeItem != null) {
                                mergeTargetOf(lifted, moved, pagerState.currentPage, currentSpec, cells, currentLayout)
                            } else {
                                null
                            }
                            if (change.pressed) {
                                dragOffset.value = moved
                                if (into?.first != mergeTarget) mergeTarget = into?.first
                                edgeDirection = when {
                                    change.position.x < edgeWidthPx -> -1
                                    change.position.x > size.width - edgeWidthPx -> 1
                                    else -> 0
                                }
                            } else if (into != null) {
                                // Let go right on an app or a folder: it goes inside.
                                val result = mergeItem?.invoke(lifted.item.id, into.first)
                                if (result != null) {
                                    optimistic = result
                                    landing = Landing(lifted, from = moved, to = into.second, into = true)
                                } else if (pagerState.currentPage == lifted.item.position.page) {
                                    landing = Landing(lifted, from = moved, to = Offset.Zero)
                                }
                                break
                            } else {
                                val drop = dropTarget(
                                    lifted = lifted,
                                    offset = moved,
                                    page = pagerState.currentPage,
                                    spec = currentSpec,
                                    cells = cells,
                                )
                                var rest = Offset.Zero
                                var changed = false
                                if (drop != null) {
                                    val grid = GridSize(currentSpec.columns, currentSpec.rows)
                                    val result = currentLayout.move(lifted.item.id, drop.cell, grid)
                                    if (result != currentLayout) {
                                        optimistic = result
                                        moveItem(lifted.item.id, drop.cell)
                                        rest = drop.rest
                                        changed = true
                                    }
                                }
                                // A drop that changes nothing glides back to where it came from,
                                // unless we turned to a different page where that cell is not shown.
                                if (changed || pagerState.currentPage == lifted.item.position.page) {
                                    landing = Landing(lifted, from = moved, to = rest)
                                }
                                break
                            }
                        }
                    } finally {
                        lift = null
                        dragging = false
                        edgeDirection = 0
                        mergeTarget = null
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { at -> gesture(Gesture.DOUBLE_TAP, surfaceOrigin + at) },
                    onLongPress = { at -> gesture(Gesture.LONG_PRESS, surfaceOrigin + at) },
                )
            }
            .pointerInput(swipeThresholdPx) {
                val tracker = VelocityTracker()
                var total = 0f
                // The swipe someone is following with the finger, if any.
                var live: Gesture? = null
                var decided = false
                val finish: (Boolean) -> Unit = { completed ->
                    val followed = live
                    if (followed != null) {
                        val down = if (completed) tracker.calculateVelocity().y else 0f
                        swipeEnd(if (followed == Gesture.SWIPE_UP) -down else down)
                    } else if (completed) {
                        when {
                            total < -swipeThresholdPx -> gesture(Gesture.SWIPE_UP, null)
                            total > swipeThresholdPx -> gesture(Gesture.SWIPE_DOWN, null)
                            else -> Unit
                        }
                    }
                    live = null
                }
                detectVerticalDragGestures(
                    onDragStart = {
                        total = 0f
                        live = null
                        decided = false
                        tracker.resetTracking()
                    },
                    onDragEnd = { finish(true) },
                    onDragCancel = { finish(false) },
                ) { change, dragAmount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    total += dragAmount
                    if (!decided && total != 0f) {
                        decided = true
                        val direction = if (total < 0f) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
                        if (swipeStart(direction)) live = direction
                    }
                    when (live) {
                        Gesture.SWIPE_UP -> swipeMove(-dragAmount)
                        Gesture.SWIPE_DOWN -> swipeMove(dragAmount)
                        else -> Unit
                    }
                }
            },
    ) {
        val settling = if (dragging) null else landing

        // The pages next to the one on show are kept ready, so turning the page has nothing to build.
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            CompositionLocalProvider(LocalGlassMotion provides glass) {
                HomePage(
                    items = itemsByPage[index].orEmpty(),
                    appsByKey = appsByKey,
                    labelsOnHome = labelsOnHome,
                    spec = spec,
                    widgets = widgets,
                    repository = repository,
                    labelStyle = labelStyle,
                    interactive = true,
                    hiddenItemId = if (dragging) lift?.item?.id else settling?.lift?.item?.id,
                    mergeTargetId = mergeTarget,
                    onLaunch = onLaunch,
                    onItemLift = { item, cellBounds, anchor ->
                        lift = Lift(item, cellBounds)
                        onItemLongPress(item, anchor)
                    },
                    dropZone = cells,
                    onRoom = onRoom,
                )
            }
        }

        // The dots come up while the pages turn and are gone a moment after they stop.
        val dotsLit = remember { Animatable(0f) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress }.collectLatest { turning ->
                if (turning) {
                    dotsLit.animateTo(1f, tween(DOTS_IN_MS))
                } else {
                    delay(DOTS_STAY_MS)
                    dotsLit.animateTo(0f, tween(DOTS_OUT_MS))
                }
            }
        }
        PageDots(
            count = pageCount,
            selected = { pagerState.currentPage },
            spec = spec,
            interactive = true,
            lit = { dotsLit.value },
        )

        // The item being carried, drawn above everything so it can cross from page to page.
        val carried = if (dragging) lift else settling?.lift
        if (carried != null) {
            val bounds = carried.cellBounds
            val labels = rememberLabelMetricsOf(labelsOnHome, bounds.width.roundToInt(), labelStyle, spec.showLabels)
            // It grows a little while in the hand and shrinks back as it lands. Something large
            // grows less, or it would cover its neighbours.
            val raised = if (carried.item.span == CellSpan.Single) 1.1f else 1.03f
            val raise = remember { Animatable(1f) }
            LaunchedEffect(dragging) { raise.animateTo(if (dragging) raised else 1f, glide(0.001f)) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        val shift = settling?.offset?.value ?: dragOffset.value
                        IntOffset(
                            x = (bounds.left - surfaceOrigin.x + shift.x).roundToInt(),
                            y = (bounds.top - surfaceOrigin.y + shift.y).roundToInt(),
                        )
                    }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        // On its way into a folder it gets smaller and fainter as it arrives.
                        val inside = settling?.takeIf { it.into }?.travelled ?: 0f
                        val scale = raise.value * (1f - 0.55f * inside)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.7f * smoothStep(0.5f, 1f, inside)
                    },
            ) {
                ItemFace(
                    item = carried.item,
                    appsByKey = appsByKey,
                    widgets = widgets,
                    spec = spec,
                    repository = repository,
                    labelStyle = labelStyle,
                    labels = labels,
                    interactive = false,
                    onLaunch = { _, _ -> },
                    onLongPress = null,
                )
            }
        }

        if (shownLayout.items.isEmpty()) {
            BasicText(
                text = emptyHint(),
                style = KiriType.caption.copy(color = Color.White, shadow = OnWallpaper, textAlign = TextAlign.Center),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
            )
        }
    }
}

/** The names shown on the home screen, so that every page reserves the same height for them. */
private fun labelsOf(
    layout: HomeLayout,
    appsByKey: Map<String, AppEntry>,
    showFolderNames: Boolean,
    folderDefault: String,
): List<String> = layout.items.mapNotNull { item ->
    when (val content = item.content) {
        is ItemContent.App -> appsByKey[content.appKey]?.label
        is ItemContent.Folder -> content.name.ifBlank { folderDefault }.takeIf { showFolderNames }
        is ItemContent.Widget, is ItemContent.AppWidget -> null
    }
}

/**
 * A still picture of the page [page] of [layout], drawn as the home screen draws it and as large
 * as the screen: the small pages of "Pages" are this, made smaller. Widgets of other apps are
 * pictures of themselves here (see `SystemWidgetFace`).
 */
@Composable
internal fun HomePagePicture(
    layout: HomeLayout,
    page: Int,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    widgets: KiriWidgets?,
    modifier: Modifier = Modifier,
) {
    val labelStyle = homeLabelStyle()
    val folderLook = LocalFolderLook.current
    val folderDefault = stringResource(R.string.folder_default)
    val labelsOnHome = remember(layout, appsByKey, folderLook.showName, folderDefault) {
        labelsOf(layout, appsByKey, folderLook.showName, folderDefault)
    }
    val items = remember(layout, page) { layout.items.filter { it.position.page == page } }
    Box(modifier) {
        HomePage(
            items = items,
            appsByKey = appsByKey,
            labelsOnHome = labelsOnHome,
            spec = spec,
            widgets = widgets,
            repository = repository,
            labelStyle = labelStyle,
            interactive = false,
            hiddenItemId = null,
            onLaunch = { _, _ -> },
            onItemLift = { _, _, _ -> },
            dropZone = null,
        )
    }
}

/**
 * The cell where a carried item lands: its own cell plus the whole cells the finger travelled.
 * Every page has the same grid, so this also holds after the page was turned.
 */
private fun dropTarget(lifted: Lift, offset: Offset, page: Int, spec: GridSpec, cells: HomeDropZone): Drop? {
    val item = lifted.item
    val pitchX = cells.cellSize.width + cells.gapXPx
    val pitchY = cells.cellSize.height + cells.gapYPx
    if (pitchX <= 0f || pitchY <= 0f) return null
    val lastColumn = (spec.columns - item.span.columns).coerceAtLeast(0)
    val lastRow = (spec.rows - item.span.rows).coerceAtLeast(0)
    val column = (item.position.column + (offset.x / pitchX).roundToInt()).coerceIn(0, lastColumn)
    val row = (item.position.row + (offset.y / pitchY).roundToInt()).coerceIn(0, lastRow)
    return Drop(
        cell = CellPosition(page, column, row),
        rest = Offset((column - item.position.column) * pitchX, (row - item.position.row) * pitchY),
    )
}

/**
 * The app or folder that the item carried by [offset] goes into if it is let go now, with how
 * far that is from where the item was: the middle of what was carried must be near enough to
 * its middle (see [MERGE_REACH]). Null when it would only move.
 */
private fun mergeTargetOf(
    lifted: Lift,
    offset: Offset,
    page: Int,
    spec: GridSpec,
    cells: HomeDropZone,
    layout: HomeLayout,
): Pair<Long, Offset>? {
    if (lifted.item.span != CellSpan.Single) return null
    val drop = dropTarget(lifted, offset, page, spec, cells) ?: return null
    val target = layout.itemAt(drop.cell) ?: return null
    if (!layout.canMerge(lifted.item.id, target.id)) return null
    val pitch = minOf(cells.cellSize.width + cells.gapXPx, cells.cellSize.height + cells.gapYPx)
    return if ((offset - drop.rest).getDistance() < MERGE_REACH * pitch) target.id to drop.rest else null
}

/**
 * One page: every item sits at its column and row, inside the margins of [spec]. The item
 * [mergeTargetId] grows a little, to show that what is carried will go into it.
 */
@Composable
private fun HomePage(
    items: List<HomeItem>,
    appsByKey: Map<String, AppEntry>,
    labelsOnHome: List<String>,
    widgets: KiriWidgets?,
    spec: GridSpec,
    repository: AppRepository,
    labelStyle: TextStyle,
    interactive: Boolean,
    hiddenItemId: Long?,
    onLaunch: (app: AppEntry, iconBounds: Rect?) -> Unit,
    onItemLift: (item: HomeItem, cellBounds: Rect, anchor: Rect) -> Unit,
    dropZone: HomeDropZone?,
    mergeTargetId: Long? = null,
    onRoom: ((HomeRoom) -> Unit)? = null,
) {
    // Where the rows start below the top margin, kept for the moment the grid is measured.
    val rowsTop = remember { floatArrayOf(0f) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsSpace(interactive)
            .padding(
                start = spec.marginHorizontalDp.dp,
                end = spec.marginHorizontalDp.dp,
                top = spec.marginTopDp.dp,
                bottom = spec.marginBottomDp.dp,
            )
            // Where the grid sits, measured from what holds the pages.
            .onGloballyPositioned { grid ->
                val page = grid.parentLayoutCoordinates
                if (dropZone != null && page != null) {
                    dropZone.gridOffset = page.localPositionOf(grid, Offset(0f, rowsTop[0]))
                }
            },
    ) {
        val density = LocalDensity.current
        // The icons go from one side margin to the other; the names have the room beside them.
        val (iconAcross, gapAcross) = HomeGridGeometry.across(spec, maxWidth.value)
        val labelWidthPx = with(density) {
            maxOf(iconAcross, iconAcross + gapAcross - HomeGridGeometry.LABEL_INSET_DP).dp.roundToPx()
        }
        val labels = rememberLabelMetricsOf(labelsOnHome, labelWidthPx, labelStyle, spec.showLabels)
        val labelBlock = if (spec.showLabels) NAME_GAP_DP + labels.height.value else 0f
        val geometry = HomeGridGeometry.of(spec, maxWidth.value, maxHeight.value, labelBlock)
        val room = HomeRoom(
            widthDp = maxWidth.value + 2 * spec.marginHorizontalDp,
            heightDp = maxHeight.value + spec.marginTopDp + spec.marginBottomDp,
            labelBlockDp = labelBlock,
        )
        rowsTop[0] = with(density) { geometry.topDp.dp.toPx() }
        SideEffect {
            onRoom?.invoke(room)
            dropZone?.let { zone ->
                with(density) {
                    zone.cellPx = Size(geometry.cellWidthDp.dp.toPx(), geometry.cellHeightDp.dp.toPx())
                    zone.gapXPx = geometry.gapXDp.dp.toPx()
                    zone.gapYPx = geometry.gapYDp.dp.toPx()
                }
            }
        }
        val gapX = geometry.gapXDp.dp
        val gapY = geometry.gapYDp.dp
        val cellWidth = geometry.cellWidthDp.dp
        val cellHeight = geometry.cellHeightDp.dp
        val top = geometry.topDp.dp
        val pad = HomeGridGeometry.CELL_PAD_DP.dp
        val labelWidth = with(density) { labelWidthPx.toDp() }

        for (item in items) {
            key(item.id) {
                // Where this cell is on screen, kept for the moment the item is picked up.
                val placement = remember { arrayOfNulls<LayoutCoordinates>(1) }
                val hidden = item.id == hiddenItemId
                // An app takes the room beside its icon too, for its name. A widget goes from the
                // top of the first row's icons to the bottom of the last row's names.
                val appLike = item.content is ItemContent.App || item.content is ItemContent.Folder
                // When its cell changes, an item slides there instead of jumping.
                val cell = with(density) {
                    IntOffset(
                        x = ((cellWidth + gapX) * item.position.column - if (appLike) (labelWidth - cellWidth) / 2 else 0.dp).roundToPx(),
                        y = (top + (cellHeight + gapY) * item.position.row + if (appLike) 0.dp else pad).roundToPx(),
                    )
                }
                val place = remember { Animatable(cell, IntOffset.VectorConverter) }
                LaunchedEffect(cell) {
                    // The item that was carried is already shown landing by its own picture, so
                    // its cell goes straight to the new place and is there when that ends.
                    if (hidden) place.snapTo(cell) else place.animateTo(cell, glide(IntOffset.VisibilityThreshold))
                }
                // Its size goes the same way: a widget made larger, or every cell when the grid
                // changes. It is read only while laying out, so no frame composes anything.
                val target = with(density) {
                    if (appLike) {
                        IntSize(labelWidth.roundToPx(), cellHeight.roundToPx())
                    } else {
                        IntSize(
                            width = (cellWidth * item.span.columns + gapX * (item.span.columns - 1)).roundToPx(),
                            height = (cellHeight * item.span.rows + gapY * (item.span.rows - 1) - pad * 2).roundToPx(),
                        )
                    }
                }
                val extent = remember { Animatable(target, IntSize.VectorConverter) }
                LaunchedEffect(target) {
                    if (hidden) extent.snapTo(target) else extent.animateTo(target, glide(IntSize.VisibilityThreshold))
                }
                val welcome = animateFloatAsState(
                    targetValue = if (item.id == mergeTargetId) MERGE_GROW else 1f,
                    animationSpec = glide(0.001f),
                    label = "merge",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { place.value }
                        .layout { measurable, _ ->
                            val now = extent.value
                            val width = now.width.coerceAtLeast(0)
                            val height = now.height.coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) { placeable.place(0, 0) }
                        }
                        .onPlaced { placement[0] = it }
                        .graphicsLayer {
                            alpha = if (hidden) 0f else 1f
                            scaleX = welcome.value
                            scaleY = welcome.value
                        },
                ) {
                    ItemFace(
                        item = item,
                        appsByKey = appsByKey,
                        widgets = widgets,
                        spec = spec,
                        repository = repository,
                        labelStyle = labelStyle,
                        labels = labels,
                        interactive = interactive,
                        onLaunch = onLaunch,
                        onLongPress = { iconBounds ->
                            placement[0]?.takeIf { it.isAttached }?.let { coordinates ->
                                val cellBounds = coordinates.boundsInRoot()
                                onItemLift(item, cellBounds, iconBounds ?: cellBounds)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * What an item looks like: used in its cell and for the copy carried by the finger.
 *
 * [onLongPress] reports where the item's icon is on screen when it has one; a widget has none.
 */
@Composable
private fun ItemFace(
    item: HomeItem,
    appsByKey: Map<String, AppEntry>,
    widgets: KiriWidgets?,
    spec: GridSpec,
    repository: AppRepository,
    labelStyle: TextStyle,
    labels: LabelMetrics,
    interactive: Boolean,
    onLaunch: (app: AppEntry, iconBounds: Rect?) -> Unit,
    onLongPress: ((iconBounds: Rect?) -> Unit)?,
) {
    when (val content = item.content) {
        is ItemContent.App -> appsByKey[content.appKey]?.let { app ->
            AppCell(
                app = app,
                spec = spec,
                labelStyle = labelStyle,
                labelHeight = labels.height,
                repository = repository,
                interactive = interactive,
                onClick = { bounds -> onLaunch(app, bounds) },
                onLongClick = if (onLongPress != null) { { bounds: Rect -> onLongPress(bounds) } } else null,
                // A name is never cut: when it needs more room than the cell has, it is
                // allowed to spill over instead.
                modifier = Modifier.wrapContentHeight(unbounded = true),
                fittedLabelStyle = labels.styleFor(app.label),
            )
        }

        is ItemContent.Folder -> FolderCell(
            folder = content,
            appsByKey = appsByKey,
            spec = spec,
            labelStyle = labelStyle,
            labelHeight = labels.height,
            fittedLabelStyle = labels.styleFor(folderName(content)),
            repository = repository,
            interactive = interactive,
            onLongClick = if (onLongPress != null) { { bounds: Rect -> onLongPress(bounds) } } else null,
            modifier = Modifier.wrapContentHeight(unbounded = true),
        )

        is ItemContent.Widget -> widgets?.byId(content.widgetId)?.let { widget ->
            val held = if (interactive && onLongPress != null) {
                Modifier.longPressAnywhere { onLongPress(null) }
            } else {
                Modifier
            }
            // Out of sight, none of its buttons takes the focus (see [LocalOutOfSight]).
            val outOfSight = LocalOutOfSight.current
            Box(
                Modifier
                    .fillMaxSize()
                    .then(held)
                    .focusProperties { if (outOfSight()) canFocus = false },
            ) {
                widget.Content(item.span, Modifier.fillMaxSize())
            }
        }

        // A widget of another app. Only the one in its cell is the widget itself; the copy a
        // finger carries and the one in a preview are pictures of it.
        is ItemContent.AppWidget -> {
            val held = if (interactive && onLongPress != null) {
                Modifier.longPressAnywhere { onLongPress(null) }
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize().then(held)) {
                SystemWidgetFace(content, live = interactive, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Reports a long press anywhere on this element, also over parts of it that answer taps of
 * their own. Those parts keep their taps; only the tap that would end a long press is taken
 * from them. Touches that nothing inside answered stop here, so they are not mistaken for
 * gestures on an empty place of the home screen.
 */
@Composable
private fun Modifier.longPressAnywhere(onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val longPress by rememberUpdatedState(onLongPress)
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                    true
                }
                if (endedEarly == null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longPress()
                    // What the finger does next belongs to the long press, not to what is under it.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
        }
        .pointerInput(Unit) { detectTapGestures { } }
}

/**
 * Small dots that tell which page is showing, as bright as [lit] says (it is read only while
 * drawing). They never sit on the icons: they take the margin below the grid, or the one above
 * it when that is too thin, and stay away when neither has room. Hidden with a single page.
 */
@Composable
private fun PageDots(count: Int, selected: () -> Int, spec: GridSpec, interactive: Boolean, lit: () -> Float) {
    if (count < 2 || !spec.showPageDots) return
    val below = spec.marginBottomDp >= DOTS_ROOM_DP
    if (!below && spec.marginTopDp < DOTS_ROOM_DP) return
    val active = selected()
    Box(
        contentAlignment = if (below) Alignment.BottomCenter else Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsSpace(interactive),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // In the middle of the margin, so the grid and the edge of the screen are equally far.
                .height((if (below) spec.marginBottomDp else spec.marginTopDp).dp)
                .graphicsLayer { alpha = lit() },
        ) {
            repeat(count) { index ->
                Box(
                    Modifier
                        .size(DOT_DP.dp)
                        .background(
                            color = Color.White.copy(alpha = if (index == active) 0.95f else 0.4f),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

private const val DOT_DP = 6

/** How much an app or folder grows while something carried would go into it. */
private const val MERGE_GROW = 1.12f

/** The least margin the dots need: themselves and a little air on both sides. */
private const val DOTS_ROOM_DP = DOT_DP + 4

private const val DOTS_IN_MS = 120

/** How long the dots stay after the pages stop, and how long they take to go. */
private const val DOTS_STAY_MS = 700L
private const val DOTS_OUT_MS = 450

/**
 * Keeps clear of the status and navigation bars. The real screen follows the bars as they
 * change; a preview sits inside a screen that already did, so it copies the same space by value.
 * The keyboard is left out: it comes up over the home screen (to name a folder) and the pages
 * stay as they are under it, instead of squeezing above it.
 */
@Composable
private fun Modifier.systemBarsSpace(interactive: Boolean): Modifier {
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    return if (interactive) windowInsetsPadding(bars) else padding(bars.asPaddingValues())
}
