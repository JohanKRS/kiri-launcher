package io.github.johankrs.kiri.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.layout.FOLDER_NAME_MAX
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.folders
import io.github.johankrs.kiri.core.look.FolderGrid
import io.github.johankrs.kiri.core.look.FolderLook
import io.github.johankrs.kiri.core.look.IconShape
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.backdrop
import io.github.johankrs.kiri.ui.theme.glassBody
import io.github.johankrs.kiri.ui.theme.glassShadowOf
import io.github.johankrs.kiri.ui.theme.glassTrim
import io.github.johankrs.kiri.ui.theme.appLabelStyle
import io.github.johankrs.kiri.ui.theme.kiriGlass
import io.github.johankrs.kiri.ui.theme.kiriGlassPlate
import io.github.johankrs.kiri.ui.theme.spec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Folders on the home screen. Closed, a folder is a tile with the small icons of its apps; a
 * tap, or pulling it up, opens it into a pane of glass that grows out of it, each small icon
 * growing into its own place. The same rule places the apps closed and open (see [FolderGrid]),
 * so opening only has to make everything larger.
 */

/** How every folder looks, for whatever draws one. */
internal val LocalFolderLook = staticCompositionLocalOf { FolderLook() }

/** The folder that is open, or on its way; null where folders cannot open (a preview). */
internal val LocalFolderOpening = staticCompositionLocalOf<FolderOpening?> { null }

/** Dark glass takes on the colour of the wallpaper; light glass would only turn it grey. */
internal val GlassStyle.vivid: Boolean get() = this != GlassStyle.NEVOA

/** The outline of closed folders: their own, or the one the icons have. */
@Composable
internal fun folderShape(look: FolderLook, repository: AppRepository): IconShape =
    look.shape ?: (LocalIconSettings.current ?: repository.iconSettings)?.look?.shape ?: IconShape.SQUIRCLE

/** The name of [folder] as shown: never blank. */
@Composable
internal fun folderName(folder: ItemContent.Folder): String = folder.name.ifBlank { stringResource(R.string.folder_default) }

/** The panel of an open folder keeps one corner all the way, so its edge of glass is made once. */
private val PANEL_CORNER = 24.dp
private val PanelShape = RoundedCornerShape(PANEL_CORNER)
private val PANEL_MARGIN = 12.dp

/** Apps side by side in an open folder never come nearer than this, however narrow the screen. */
private val LEAST_GAP = 4.dp
private val PANEL_TITLE = 40.dp
private val LABEL_GAP = 6.dp

/**
 * How far along its way (see [lead]) the glass of the panel has taken over from that of the
 * closed folder, and the edge of the panel from the edge of the folder.
 */
private const val BODY_TURN = 0.35f
private const val TRIM_TURN = 0.6f

/** How far a finger pulls a closed folder up to open it all the way, and the panel down to close it. */
internal val FOLDER_OPEN_DISTANCE = 190.dp
internal val FOLDER_CLOSE_DISTANCE = 280.dp

/** How near the middle of an app or folder something carried must come to go into it. */
internal const val MERGE_REACH = 0.32f

/**
 * A closed folder: its ground, unless it has none, and the small icons of its first apps. When
 * it holds more than fit, the last place says how many more there are.
 *
 * Icons are asked for at [iconSizePx], the size of the icons of the home screen, so they are
 * the pictures already made for it. [groundAlpha] and [minisAlpha] are read only while drawing.
 */
@Composable
internal fun FolderFace(
    folder: ItemContent.Folder,
    appsByKey: Map<String, AppEntry>,
    repository: AppRepository,
    iconSizePx: Int,
    modifier: Modifier = Modifier,
    look: FolderLook = LocalFolderLook.current,
    groundAlpha: (() -> Float)? = null,
    minisAlpha: (() -> Float)? = null,
) {
    val apps = remember(folder.appKeys, appsByKey) { folder.appKeys.mapNotNull { appsByKey[it] } }
    val closed = remember(apps.size, look) { FolderGrid.closed(apps.size, look) }
    val outline = folderShape(look, repository).outline()
    val ground = (folder.ground ?: look.ground).glass
    Box(modifier) {
        if (ground != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (groundAlpha != null) alpha = groundAlpha() }
                    .kiriGlass(ground, outline, look.groundOpacity, vivid = ground.vivid),
            )
        }
        Layout(
            content = {
                for (index in 0 until closed.drawn.coerceAtMost(apps.size)) {
                    val app = apps[index]
                    key(app.key) {
                        val picture by rememberAppIcon(app, repository, iconSizePx)
                        IconFace(picture, Modifier.fillMaxSize())
                    }
                }
                if (closed.more > 0) MoreBadge(closed.more, outline, ground)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { if (minisAlpha != null) alpha = minisAlpha() },
        ) { measurables, constraints ->
            val side = minOf(constraints.maxWidth, constraints.maxHeight)
            val left = (constraints.maxWidth - side) / 2
            val top = (constraints.maxHeight - side) / 2
            val placeables = measurables.mapIndexed { index, measurable ->
                val slot = closed.slots[if (index < closed.drawn) index else closed.slots.lastIndex]
                val px = (slot.size * side).roundToInt().coerceAtLeast(0)
                measurable.measure(Constraints.fixed(px, px))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val slot = closed.slots[if (index < closed.drawn) index else closed.slots.lastIndex]
                    placeable.place(left + (slot.x * side).roundToInt(), top + (slot.y * side).roundToInt())
                }
            }
        }
    }
}

/** The last place of a folder with more apps than places: "+N". */
@Composable
private fun MoreBadge(more: Int, outline: Shape, ground: GlassStyle?) {
    val onGlass = (ground ?: GlassStyle.FUME).spec()
    val backing = if (ground != null) {
        Modifier.background(onGlass.content.copy(alpha = 0.16f), outline)
    } else {
        Modifier.kiriGlassPlate(GlassStyle.FUME, outline, 45)
    }
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().then(backing)) {
        val size = with(LocalDensity.current) { (constraints.maxWidth * 0.4f).toSp() }
        BasicText(
            text = "+$more",
            style = KiriType.caption.copy(
                color = onGlass.content,
                fontSize = size,
                lineHeight = size,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * A folder in its cell of the home screen: the closed folder where an app has its icon, and its
 * name underneath. A tap opens it, and so does pulling it up, following the finger. A long press
 * reports where the folder is, as for an app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderCell(
    folder: ItemContent.Folder,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    labelStyle: TextStyle,
    labelHeight: Dp,
    fittedLabelStyle: TextStyle?,
    repository: AppRepository,
    interactive: Boolean,
    onLongClick: ((faceBounds: Rect) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val look = LocalFolderLook.current
    val opening = LocalFolderOpening.current
    val density = LocalDensity.current
    val iconSizePx = with(density) { spec.iconSizeDp.dp.roundToPx() }
    val name = folderName(folder)
    val facePlace = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Inside the shrink of a press, which is about its middle.
    val pressedPlace = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Where the face is at rest, though a finger may be shrinking it now: the folder opens from
    // there and closes back to there, where it will be once nothing presses it.
    val faceBounds = {
        facePlace[0]?.takeIf { it.isAttached }?.let { face ->
            restingBounds(face, pressedPlace[0]?.takeIf { it.isAttached })
        }
    }

    val cellModifier = if (interactive) {
        var focused by remember { mutableStateOf(false) }
        val interactions = remember { MutableInteractionSource() }
        val pressed by interactions.collectIsPressedAsState()
        val pressScale = animateFloatAsState(
            targetValue = if (pressed) 0.9f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
            label = "press",
        )
        val outOfSight = LocalOutOfSight.current
        val folderNow by rememberUpdatedState(folder)
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.background(color = labelStyle.color.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val scale = pressScale.value
                scaleX = scale
                scaleY = scale
            }
            .onPlaced { pressedPlace[0] = it }
            .focusProperties { if (outOfSight()) canFocus = false }
            .then(
                if (opening != null) {
                    Modifier.pullToOpen(
                        onStart = { faceBounds()?.let { opening.beginPull(folderNow.id, it) } ?: false },
                        onPull = opening::pullTo,
                        onRelease = opening::releasePull,
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = { faceBounds()?.let { bounds -> opening?.open(folderNow.id, bounds) } },
                onLongClick = if (onLongClick != null) { { faceBounds()?.let(onLongClick) } } else null,
            )
    } else {
        modifier.fillMaxWidth()
    }

    val described = stringResource(R.string.folder_apps, name, folder.appKeys.size)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = cellModifier
            .semantics { contentDescription = described }
            .padding(vertical = 6.dp),
    ) {
        val showing = opening?.let { o -> { o.isShowing(folder.id) } }
        FolderFace(
            folder = folder,
            appsByKey = appsByKey,
            repository = repository,
            iconSizePx = iconSizePx,
            look = look,
            modifier = Modifier
                .squareUpTo(spec.iconSizeDp.dp)
                .onPlaced { facePlace[0] = it },
            // While it is open or on its way the panel takes its place: it starts as this very
            // ground and turns into the panel, and the small icons grow from here into it.
            groundAlpha = if (opening != null && showing != null) {
                { if (showing()) 0f else 1f }
            } else {
                null
            },
            minisAlpha = showing?.let { { if (it()) 0f else 1f } },
        )
        if (spec.showLabels) {
            Spacer(Modifier.height(LABEL_GAP))
            BasicText(
                text = if (look.showName) name else "",
                style = fittedLabelStyle ?: labelStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = labelHeight),
            )
        }
    }
}

/**
 * The bounds of [face] on screen as they are when nothing presses it. [pressed] is the box a
 * press shrinks about its middle; its middle stays put, and how much smaller it now looks than
 * it is says by how much. Without it, the bounds as they are.
 */
private fun restingBounds(face: LayoutCoordinates, pressed: LayoutCoordinates?): Rect {
    val corner = face.localToRoot(Offset.Zero)
    val far = face.localToRoot(Offset(face.size.width.toFloat(), face.size.height.toFloat()))
    val seen = Rect(corner, far)
    if (pressed == null || pressed.size.width <= 0) return seen
    val width = pressed.size.width.toFloat()
    val middle = pressed.localToRoot(Offset(width / 2f, pressed.size.height / 2f))
    val scale = (pressed.localToRoot(Offset(width, 0f)).x - pressed.localToRoot(Offset.Zero).x) / width
    if (scale <= 0.01f || abs(scale - 1f) < 0.001f) return seen
    return Rect(middle + (seen.topLeft - middle) / scale, middle + (seen.bottomRight - middle) / scale)
}

/**
 * Watches for a finger pulling this element up, and only up: such a pull is taken from the home
 * screen (which would open the drawer) and reported, [onPull] with how far it went and
 * [onRelease] with its speed upwards. [onStart] may still turn it down. Sideways and downwards
 * moves, and anything after a long press, are left alone.
 */
private fun Modifier.pullToOpen(
    onStart: () -> Boolean,
    onPull: (distancePx: Float) -> Unit,
    onRelease: (velocityUpPx: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var startY = Float.NaN
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (!startY.isNaN()) onRelease(0f)
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            if (startY.isNaN()) {
                if (!change.pressed || change.isConsumed) break
                if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                if (moved.y >= 0f || abs(moved.y) <= abs(moved.x)) break
                if (!onStart()) break
                startY = change.position.y
                change.consume()
                continue
            }
            change.consume()
            if (change.pressed) {
                onPull(startY - change.position.y)
            } else {
                onRelease(-tracker.calculateVelocity().y)
                break
            }
        }
    }
}

/** The folder [folderId] on show, and where its closed face is, in root coordinates. */
@Immutable
internal class ShownFolder(val folderId: String, val face: Rect, val renaming: Boolean)

/**
 * Which folder is open, or on its way open or closed, and how far: 0 closed, 1 open, a little
 * more while it springs into place. A finger moves it directly; otherwise it glides on a spring
 * that starts at the speed the finger had.
 */
@Stable
internal class FolderOpening(private val scope: CoroutineScope) {
    var shown by mutableStateOf<ShownFolder?>(null)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    /** The person's choice of speed, as a factor on the stiffness of the spring. */
    var stiffnessScale = 1f

    /** How far a pull opens it all the way, and a drag of the panel closes it, in pixels. */
    var openDistancePx = 1f
    var closeDistancePx = 1f

    private var settling: Job? = null

    fun isShowing(id: String): Boolean = shown?.folderId == id

    /** How far open, between 0 and 1, for what only fades in with it. Read while drawing. */
    val openness: Float get() = if (shown == null) 0f else progress.coerceIn(0f, 1f)

    /** Opens the folder [folderId], whose closed face is at [face]. */
    fun open(folderId: String, face: Rect, renaming: Boolean = false) {
        if (shown?.folderId != folderId) {
            settling?.cancel()
            progress = 0f
        }
        shown = ShownFolder(folderId, face, renaming)
        settle(true)
    }

    /** A finger started pulling the closed folder up. False when another one is on show. */
    fun beginPull(folderId: String, face: Rect): Boolean {
        if (shown != null) return false
        settling?.cancel()
        progress = 0f
        shown = ShownFolder(folderId, face, renaming = false)
        return true
    }

    fun pullTo(distancePx: Float) {
        progress = stretch(distancePx / openDistancePx)
    }

    fun releasePull(velocityUpPx: Float) {
        val velocity = velocityUpPx / openDistancePx
        settle(velocity > FLING || (progress > 0.35f && velocity > -FLING), velocity)
    }

    /** Stops wherever it was going: a touch holds it where it is. */
    fun hold() {
        settling?.cancel()
    }

    /** The panel dragged [downPx] further down than where it was, at [from], when the finger came. */
    fun dragFrom(from: Float, downPx: Float) {
        progress = stretch(from - downPx / closeDistancePx)
    }

    fun releaseDrag(velocityDownPx: Float) {
        val velocity = -velocityDownPx / closeDistancePx
        val open = when {
            velocity < -FLING -> false
            velocity > FLING -> true
            else -> progress >= 0.62f
        }
        settle(open, velocity)
    }

    fun settle(open: Boolean, velocity: Float = 0f) {
        settling?.cancel()
        if (shown == null) return
        settling = scope.launch {
            animate(
                initialValue = progress,
                targetValue = if (open) 1f else 0f,
                initialVelocity = velocity,
                // It comes to rest with a touch of spring when it opens, and none when it closes.
                animationSpec = spring(
                    dampingRatio = if (open) 0.8f else 1f,
                    stiffness = Spring.StiffnessMediumLow * 1.5f * stiffnessScale,
                    visibilityThreshold = 0.001f,
                ),
            ) { value, _ -> progress = value }
            if (!open) closeNow()
        }
    }

    fun close() = settle(false)

    /** Gone at once, as when an app was opened from it. */
    fun closeNow() {
        settling?.cancel()
        shown = null
        progress = 0f
    }

    /** Opens again the same folder with its name ready to be changed. */
    fun rename() {
        val now = shown ?: return
        shown = ShownFolder(now.folderId, now.face, renaming = true)
    }

    private companion object {
        /** A release faster than this, in whole openings a second, decides by itself. */
        const val FLING = 1.1f

        /** Past fully open it only gives a little, as if held back. */
        fun stretch(value: Float): Float = (if (value > 1f) 1f + (value - 1f) * 0.15f else value).coerceIn(0f, 1.06f)
    }
}

/** Where an app of a closed folder is: the corner and side of its small icon, on screen. */
private class ClosedPlace(val corner: Offset, val side: Float, val hidden: Boolean)

/** Where everything of an open folder goes, in root coordinates. */
@Immutable
private class PanelGeometry(
    val face: Rect,
    val panel: Rect,
    val columns: Int,
    val count: Int,
    val cellWidth: Float,
    val icon: Float,
    val labelBlock: Float,
    val rowGap: Float,
    val pad: Float,
    val titleHeight: Float,
    val closed: FolderGrid.Closed,
) {
    val title: Rect get() = Rect(panel.left + pad, panel.top + pad * 0.5f, panel.right - pad, panel.top + pad + titleHeight)

    /** The panel on its way: grown [lead] of the way from the closed folder. */
    fun panelAt(lead: Float): Rect = Rect(
        left = lerp(face.left, panel.left, lead),
        top = lerp(face.top, panel.top, lead),
        right = lerp(face.right, panel.right, lead),
        bottom = lerp(face.bottom, panel.bottom, lead),
    )

    /**
     * The top-left corner of the icon of the app at [index], from the corner of the panel. The
     * last row is centred. The panel can move as a whole (the keyboard pushes it up while the
     * name is changed), and what is placed this way moves with it, on the same frame.
     */
    fun slotInPanel(index: Int): Offset {
        val (column, row) = FolderGrid.cell(index, count, columns)
        return Offset(
            x = pad + column * cellWidth + (cellWidth - icon) / 2f,
            y = pad + titleHeight + row * (icon + labelBlock + rowGap),
        )
    }

    /** The top-left corner of the icon of the app at [index], on screen. */
    fun slot(index: Int): Offset = panel.topLeft + slotInPanel(index)

    /**
     * Where the app at [index], in the saved order, is in the closed folder: to the pixel where
     * [FolderFace] puts its small icon, so the icon that lands there and the one that then
     * takes its place are the same.
     */
    fun closedPlace(index: Int): ClosedPlace {
        val width = face.width.roundToInt()
        val height = face.height.roundToInt()
        val side = minOf(width, height)
        val left = face.left + (width - side) / 2
        val top = face.top + (height - side) / 2
        if (index in 0 until closed.drawn) {
            val slot = closed.slots[index]
            return ClosedPlace(
                Offset(left + (slot.x * side).roundToInt(), top + (slot.y * side).roundToInt()),
                (slot.size * side).roundToInt().toFloat(),
                hidden = false,
            )
        }
        // Those that did not fit come out of the last place, small.
        val slot = closed.slots.last()
        val corner = Offset(left + (slot.x + slot.size * 0.3f) * side, top + (slot.y + slot.size * 0.3f) * side)
        return ClosedPlace(corner, slot.size * 0.4f * side, hidden = true)
    }

    /** The app whose place (icon and name) is under [point], or -1. */
    fun slotAt(point: Offset): Int = (0 until count).firstOrNull { index ->
        val corner = slot(index)
        val left = corner.x - (cellWidth - icon) / 2f
        point.x >= left && point.x < left + cellWidth && point.y >= corner.y && point.y < corner.y + icon + labelBlock
    } ?: -1

    /** The place nearest to [point]. */
    fun nearestSlot(point: Offset): Int = (0 until count).minByOrNull { index ->
        (slot(index) + Offset(icon / 2f, icon / 2f) - point).getDistanceSquared()
    } ?: 0
}

/**
 * The open folder, over everything on the home screen: the home darkens a little behind it and
 * the panel grows out of the closed folder, with each small icon growing into its place. The
 * glass comes first and the names settle last.
 *
 * A tap opens an app, or outside closes the folder, and so do Back and dragging the panel down.
 * A touch while it moves holds it where it is. A tap on the name changes it. Holding an app
 * lifts it: moved around inside it takes another place, and taken out of the panel it closes
 * the folder and is carried onto the home screen by [onCarryOut].
 */
@Composable
internal fun FolderOverlay(
    opening: FolderOpening,
    folder: ItemContent.Folder,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    onLaunch: (app: AppEntry, iconBounds: Rect?) -> Unit,
    onRename: (name: String) -> Unit,
    onReorder: (order: List<String>) -> Unit,
    onCarryOut: (app: AppEntry, iconBounds: Rect) -> Unit,
) {
    val shown = opening.shown ?: return
    val look = LocalFolderLook.current
    val launchApp by rememberUpdatedState(onLaunch)
    val reorder by rememberUpdatedState(onReorder)
    val carryOut by rememberUpdatedState(onCarryOut)
    val rename by rememberUpdatedState(onRename)
    val currentLook by rememberUpdatedState(look)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val glass = look.panelGlass
    val onGlass = glass.spec(look.panelOpacity)
    val labelStyle = appLabelStyle().copy(color = onGlass.content)

    // The order while an app is moved around inside; null follows the saved one.
    var order by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(folder.appKeys) { order = null }
    val keys = order ?: folder.appKeys
    val apps = remember(keys, appsByKey) { keys.mapNotNull { appsByKey[it] } }
    if (apps.isEmpty()) {
        LaunchedEffect(Unit) { opening.closeNow() }
        return
    }

    // The app held by the finger, where it started and how far it went; and one carried out.
    var liftedKey by remember { mutableStateOf<String?>(null) }
    val liftOrigin = remember { mutableStateOf(Offset.Zero) }
    val liftOffset = remember { mutableStateOf(Offset.Zero) }
    var carriedOut by remember { mutableStateOf<String?>(null) }

    var renaming by remember(shown.renaming) { mutableStateOf(shown.renaming) }
    val name = folderName(folder)
    var draftName by remember { mutableStateOf(TextFieldValue(name)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(renaming) {
        if (renaming) {
            draftName = TextFieldValue(name, selection = TextRange(0, name.length))
            runCatching { focus.requestFocus() }
        }
    }
    val finishRename: () -> Unit = {
        if (renaming) {
            renaming = false
            if (draftName.text.isNotBlank() && draftName.text != name) rename(draftName.text)
        }
    }
    BackHandler {
        finishRename()
        opening.close()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // The bars and cutouts decide the size of the panel. The keyboard, while the name is
        // changed, only pushes it up: it comes and goes a frame at a time, and the panel must
        // keep its size and its apps their places meanwhile.
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safeLeft = insets.getLeft(density, direction).toFloat()
        val safeRight = insets.getRight(density, direction).toFloat()
        val safeTop = insets.getTop(density).toFloat()
        val safeBottom = insets.getBottom(density).toFloat()
        val keyboard = WindowInsets.ime.getBottom(density).toFloat()

        val count = apps.size
        val columns = FolderGrid.columns(count, most = spec.columns, chosen = look.columns)
        val pad = with(density) { look.panelMarginDp.dp.toPx() }
        val margin = with(density) { PANEL_MARGIN.toPx() }
        // The apps inside are as large as the icons around the folder: the closed folder has
        // the size of an icon where it is, on the home screen or in the drawer.
        val iconFull = with(density) { spec.iconSizeDp.dp.toPx() }
            .let { full -> if (shown.face.width > 1f) minOf(full, shown.face.width) else full }
        val gap = with(density) { look.panelGapDp.dp.toPx() }
        val leastGap = with(density) { LEAST_GAP.toPx() }
        val roomWidth = widthPx - safeLeft - safeRight - 2f * margin - 2f * pad
        val cellWidth = minOf(iconFull + gap, roomWidth / columns).coerceAtLeast(1f)
        val labelWidthPx = (cellWidth - with(density) { 4.dp.toPx() }).roundToInt().coerceAtLeast(1)
        val labelTexts = remember(apps) { apps.map { it.label } }
        val labels = rememberLabelMetricsOf(labelTexts, labelWidthPx, labelStyle, look.panelNames)
        val labelBlock = if (look.panelNames) with(density) { (LABEL_GAP + labels.height).toPx() } else 0f
        // The name shows while it is changed even where the folder is set to show none.
        val titleHeight = if (look.panelTitle || renaming) with(density) { PANEL_TITLE.toPx() } else 0f
        val rowGapFull = with(density) { look.panelRowGapDp.dp.toPx() }

        val geometry = remember(shown.face, widthPx, heightPx, safeLeft, safeRight, safeTop, safeBottom, keyboard, count, columns, cellWidth, labelBlock, titleHeight, look) {
            val rows = FolderGrid.rows(count, columns)
            // Too many rows for the screen: the icons and the room between them get smaller.
            val room = heightPx - safeTop - safeBottom - 2f * margin
            val fixed = 2f * pad + titleHeight + rows * labelBlock
            val variable = rows * iconFull + (rows - 1) * rowGapFull
            val shrink = if (variable > 0f && fixed + variable > room) ((room - fixed) / variable).coerceIn(0.35f, 1f) else 1f
            // Apps side by side keep a little room between them however narrow the screen.
            val icon = (iconFull * shrink).coerceAtMost(cellWidth - leastGap).coerceAtLeast(1f)
            val rowGap = rowGapFull * shrink
            val width = columns * cellWidth + 2f * pad
            val height = 2f * pad + titleHeight + rows * (icon + labelBlock) + (rows - 1) * rowGap
            val face = shown.face
            val left = (face.center.x - width / 2f)
                .coerceIn(safeLeft + margin, max(safeLeft + margin, widthPx - safeRight - margin - width))
            // Above the keyboard if it fits; if not, the top (with the name) stays in sight.
            val bottom = max(safeBottom, keyboard)
            val top = (face.center.y - height / 2f)
                .coerceIn(safeTop + margin, max(safeTop + margin, heightPx - bottom - margin - height))
            PanelGeometry(
                face = face,
                panel = Rect(left, top, left + width, top + height),
                columns = columns,
                count = count,
                cellWidth = cellWidth,
                icon = icon,
                labelBlock = labelBlock,
                rowGap = rowGap,
                pad = pad,
                titleHeight = titleHeight,
                closed = FolderGrid.closed(count, look),
            )
        }
        val currentGeometry by rememberUpdatedState(geometry)
        val currentApps by rememberUpdatedState(apps)
        val currentFolder by rememberUpdatedState(folder)
        val isRenaming by rememberUpdatedState(renaming)

        // Where each app goes in the open panel, gliding there when the order changes.
        val places = remember { HashMap<String, Animatable<Offset, AnimationVector2D>>() }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = look.dimAlpha * opening.openness) }
                .pointerInput(opening) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g = currentGeometry
                        val list = currentApps
                        if (isRenaming && g.title.contains(down.position)) return@awaitEachGesture
                        opening.hold()
                        val heldAt = opening.progress
                        val open = heldAt > 0.96f
                        val inPanel = g.panelAt(lead(heldAt)).contains(down.position)
                        val hit = if (open) g.slotAt(down.position).takeIf { it in list.indices } ?: -1 else -1
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var crossedAt = down.position

                        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var result = Touch.GONE
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    result = Touch.TAP
                                    break
                                }
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    crossedAt = change.position
                                    result = Touch.DRAG
                                    break
                                }
                            }
                            result
                        } ?: Touch.HELD

                        when (outcome) {
                            Touch.TAP -> when {
                                hit >= 0 -> {
                                    finishRename()
                                    val corner = g.slot(hit)
                                    launchApp(list[hit], Rect(corner, Size(g.icon, g.icon)))
                                    // Closed behind the app that opens, so it is not there on return.
                                    scope.launch {
                                        delay(LAUNCH_CLOSE_DELAY_MS)
                                        opening.closeNow()
                                    }
                                }

                                !inPanel -> {
                                    finishRename()
                                    opening.close()
                                }

                                open && currentLook.panelTitle && g.title.contains(down.position) -> renaming = true

                                else -> opening.settle(opening.progress >= 0.5f)
                            }

                            Touch.DRAG -> {
                                val moved = crossedAt - down.position
                                if (abs(moved.y) >= abs(moved.x)) {
                                    finishRename()
                                    // The panel follows the finger, up or down.
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        change.consume()
                                        if (!change.pressed) break
                                        opening.dragFrom(heldAt, change.position.y - crossedAt.y)
                                    }
                                    opening.releaseDrag(tracker.calculateVelocity().y)
                                } else {
                                    waitForAllUp()
                                    opening.settle(opening.progress >= 0.5f)
                                }
                            }

                            Touch.HELD -> if (hit >= 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                finishRename()
                                val heldKey = list[hit].key
                                val moving = list.map { it.key }.toMutableList()
                                var at = hit
                                val origin = g.slot(hit)
                                liftOrigin.value = origin
                                liftOffset.value = Offset.Zero
                                liftedKey = heldKey
                                var carried = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val delta = change.position - down.position
                                    liftOffset.value = delta
                                    val out = CARRY_OUT_MARGIN.toPx()
                                    if (!g.panel.inflate(out).contains(change.position)) {
                                        // Out of the panel: the folder closes and the app goes to the home screen.
                                        carriedOut = heldKey
                                        carried = true
                                        carryOut(list[hit], Rect(origin + delta, Size(g.icon, g.icon)))
                                        opening.close()
                                        break
                                    }
                                    val target = g.nearestSlot(origin + Offset(g.icon / 2f, g.icon / 2f) + delta)
                                    if (target != at) {
                                        moving.remove(heldKey)
                                        moving.add(target, heldKey)
                                        at = target
                                        order = moving.toList()
                                    }
                                }
                                if (!carried) {
                                    val from = origin + liftOffset.value
                                    val to = g.slotInPanel(at)
                                    val place = places[heldKey]
                                    scope.launch {
                                        place?.snapTo(from - currentGeometry.panel.topLeft)
                                        liftedKey = null
                                        place?.animateTo(to, glideSpring(opening.stiffnessScale))
                                    }
                                    if (moving != currentFolder.appKeys) reorder(moving.toList())
                                }
                            } else {
                                waitForAllUp()
                                opening.settle(opening.progress >= 0.5f)
                            }

                            Touch.GONE -> opening.settle(opening.progress >= 0.5f)
                        }
                    }
                },
        ) {
            val ground = (folder.ground ?: look.ground).glass
            val closedShape = folderShape(look, repository)
            val closedOutline = closedShape.outline()

            // The pane of glass, growing out of the closed folder. The glass leads, the icons
            // follow. It starts as the closed folder itself, its outline, its glass and its edge,
            // and turns little by little into the panel; closing, it turns back the same way.
            val way: () -> Float = { lead(opening.progress).coerceIn(0f, 1f) }
            val morph = remember(closedShape) { FolderMorphShape(closedShape, PANEL_CORNER, way) }
            // Without a ground of its own there is nothing to turn from: the panel only fades in.
            val panelIn: () -> Float = if (ground == null) {
                { smoothStep(0f, 0.6f, opening.progress) }
            } else {
                { smoothStep(0f, BODY_TURN, way()) }
            }
            val panelTrim: () -> Float = if (ground == null) panelIn else { { smoothStep(0.05f, TRIM_TURN, way()) } }
            Box(
                Modifier
                    .offset { geometry.panelAt(lead(opening.progress)).topLeft.rounded() }
                    .layout { measurable, _ ->
                        val now = geometry.panelAt(lead(opening.progress))
                        val width = now.width.roundToInt().coerceAtLeast(0)
                        val height = now.height.roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    },
            ) {
                if (ground != null) {
                    Box(Modifier.fillMaxSize().glassShadowOf(closedOutline) { 1f - panelTrim() })
                }
                Box(Modifier.fillMaxSize().glassShadowOf(PanelShape, panelTrim))
                if (ground != null) {
                    // Under the glass of the panel until that is all there.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (way() < BODY_TURN) 1f else 0f }
                            .glassBody(ground, morph, look.groundOpacity, vivid = ground.vivid),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = panelIn() }
                        .glassBody(glass, morph, look.panelOpacity, vivid = glass.vivid),
                )
                if (ground != null) {
                    Box(Modifier.fillMaxSize().glassTrim(ground, closedOutline, clip = morph) { 1f - panelTrim() })
                }
                Box(Modifier.fillMaxSize().glassTrim(glass, PanelShape, clip = morph, fade = panelTrim))
            }

            if (look.panelTitle || renaming) {
                val titleStyle = KiriType.body.copy(color = onGlass.content, fontSize = 18.sp, lineHeight = 22.sp)
                val titleWidth = with(density) { (geometry.panel.width - 2f * geometry.pad).coerceAtLeast(0f).toDp() }
                val titleModifier = Modifier
                    .offset {
                        val t = lead(opening.progress).coerceAtMost(1f)
                        val from = geometry.face.topLeft + Offset(6.dp.toPx(), 8.dp.toPx())
                        val to = Offset(geometry.panel.left + geometry.pad + 2.dp.toPx(), geometry.panel.top + geometry.pad - 2.dp.toPx())
                        Offset(lerp(from.x, to.x, t), lerp(from.y, to.y, t)).rounded()
                    }
                    .width(titleWidth)
                    .graphicsLayer { alpha = smoothStep(0.55f, 1f, opening.progress) }
                if (renaming) {
                    BasicTextField(
                        value = draftName,
                        onValueChange = { if (it.text.length <= FOLDER_NAME_MAX) draftName = it },
                        singleLine = true,
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(onGlass.content),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finishRename() }),
                        modifier = titleModifier.focusRequester(focus),
                        decorationBox = { field ->
                            Box {
                                if (draftName.text.isEmpty()) {
                                    BasicText(stringResource(R.string.folder_rename_hint), style = titleStyle.copy(color = onGlass.contentMuted))
                                }
                                field()
                            }
                        },
                    )
                } else {
                    BasicText(text = name, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
                }
            }

            val iconPx = with(density) { spec.iconSizeDp.dp.roundToPx() }
            val labelWidth = with(density) { labelWidthPx.toDp() }
            for ((index, app) in apps.withIndex()) {
                key(app.key) {
                    // From the corner of the panel, so the app goes wherever the panel goes.
                    val target = geometry.slotInPanel(index)
                    val place = remember { Animatable(target, Offset.VectorConverter) }
                    SideEffect { places[app.key] = place }
                    LaunchedEffect(target) {
                        if (liftedKey != app.key && place.targetValue != target) {
                            place.animateTo(target, glideSpring(opening.stiffnessScale))
                        }
                    }
                    val from = geometry.closedPlace(folder.appKeys.indexOf(app.key))
                    // Where the icon is at this moment, and how large: read only while placing.
                    val corner: () -> Offset = {
                        val q = opening.progress
                        val t = if (from.hidden) smoothStep(0.15f, 1f, q) * (if (q > 1f) q else 1f) else q
                        val to = if (liftedKey == app.key) {
                            liftOrigin.value + liftOffset.value
                        } else {
                            currentGeometry.panel.topLeft + place.value
                        }
                        Offset(lerp(from.corner.x, to.x, t), lerp(from.corner.y, to.y, t))
                    }
                    val side: () -> Float = {
                        val q = opening.progress
                        val t = if (from.hidden) smoothStep(0.15f, 1f, q) else q
                        lerp(from.side, geometry.icon, t)
                    }
                    val picture by rememberAppIcon(app, repository, iconPx)
                    IconFace(
                        picture = picture,
                        modifier = Modifier
                            .offset { corner().rounded() }
                            .layout { measurable, _ ->
                                val px = side().roundToInt().coerceAtLeast(0)
                                val placeable = measurable.measure(Constraints.fixed(px, px))
                                layout(px, px) { placeable.place(0, 0) }
                            }
                            .graphicsLayer {
                                val q = opening.progress
                                alpha = when {
                                    carriedOut == app.key -> 0f
                                    from.hidden -> smoothStep(0.2f, 0.75f, q)
                                    else -> 1f
                                }
                                val raised = if (liftedKey == app.key) 1.1f else 1f
                                scaleX = raised
                                scaleY = raised
                            },
                    )
                    if (look.panelNames) {
                        BasicText(
                            text = app.label,
                            style = labels.styleFor(app.label),
                            modifier = Modifier
                                .offset {
                                    val at = corner()
                                    val size = side()
                                    val gap = LABEL_GAP.toPx() * opening.progress.coerceIn(0f, 1f)
                                    Offset(at.x + size / 2f - labelWidthPx / 2f, at.y + size + gap).rounded()
                                }
                                .width(labelWidth)
                                .heightIn(min = labels.height)
                                .graphicsLayer {
                                    alpha = if (carriedOut == app.key || liftedKey == app.key) {
                                        0f
                                    } else {
                                        smoothStep(0.62f, 1f, opening.progress)
                                    }
                                },
                        )
                    }
                }
            }

            // The "+N" of the closed folder gives way to the apps it stood for.
            val more = geometry.closed.more
            if (more > 0) {
                val last = geometry.closed.slots.last()
                val outline = folderShape(look, repository).outline()
                Box(
                    Modifier
                        .offset {
                            val q = opening.progress
                            val side = geometry.face.width
                            val from = Offset(geometry.face.left + last.x * side, geometry.face.top + last.y * side)
                            val to = geometry.slot(geometry.closed.drawn)
                            Offset(lerp(from.x, to.x, q), lerp(from.y, to.y, q)).rounded()
                        }
                        .layout { measurable, _ ->
                            val q = opening.progress
                            val px = lerp(last.size * geometry.face.width, geometry.icon, q).roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(px, px))
                            layout(px, px) { placeable.place(0, 0) }
                        }
                        .graphicsLayer { alpha = 1f - smoothStep(0f, 0.3f, opening.progress) },
                ) { MoreBadge(more, outline, ground) }
            }
        }
    }
}

/**
 * The outline of a folder on its way between closed and open, in the box it has at the moment:
 * at 0 of [way] the outline of the closed folder ([closed]), at 1 the rounded corners of the
 * open panel ([corner]), and in between, around its middle, each point as far out as the same
 * share of the way between the two. Read whenever the outline is made, which is every time the
 * box changes size: on every frame of the way.
 */
private class FolderMorphShape(
    private val closed: IconShape,
    private val corner: Dp,
    private val way: () -> Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = way()
        val cornerPx = with(density) { corner.toPx() }
        if (t <= 0f) return closed.outline().createOutline(size, layoutDirection, density)
        if (t >= 1f) return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx)))
        val halfWidth = size.width / 2f
        val halfHeight = size.height / 2f
        val path = Path()
        for (step in 0 until MORPH_STEPS) {
            val angle = step * (2.0 * PI / MORPH_STEPS)
            val across = cos(angle).toFloat()
            val down = sin(angle).toFloat()
            val from = closedReach(closed, across, down, halfWidth, halfHeight)
            val to = roundedReach(across, down, halfWidth, halfHeight, cornerPx)
            val reach = from + (to - from) * t
            val x = halfWidth + reach * across
            val y = halfHeight + reach * down
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    private companion object {
        /** Points around: enough for a corner of the panel to stay round to the eye. */
        const val MORPH_STEPS = 360

        /** How far from the middle the outline of [shape] is, in the direction (across, down). */
        fun closedReach(shape: IconShape, across: Float, down: Float, halfWidth: Float, halfHeight: Float): Float =
            when (shape) {
                IconShape.SQUIRCLE -> {
                    // |x|⁴ + |y|⁴ = 1, stretched to the box: the squircle of the icons.
                    val x = abs(across) / halfWidth
                    val y = abs(down) / halfHeight
                    val sum = x * x * x * x + y * y * y * y
                    if (sum <= 0f) 0f else sum.toDouble().pow(-0.25).toFloat()
                }
                else -> roundedReach(across, down, halfWidth, halfHeight, shape.cornerShare * 2f * minOf(halfWidth, halfHeight))
            }

        /** How far from the middle a rectangle with corners rounded by [corner] is, in a direction. */
        fun roundedReach(across: Float, down: Float, halfWidth: Float, halfHeight: Float, corner: Float): Float {
            val radius = corner.coerceIn(0f, minOf(halfWidth, halfHeight))
            val ax = abs(across)
            val ay = abs(down)
            val toSide = if (ax > 1e-6f) halfWidth / ax else Float.MAX_VALUE
            val toTop = if (ay > 1e-6f) halfHeight / ay else Float.MAX_VALUE
            val flat = minOf(toSide, toTop)
            // On a straight side, or in a corner, where the ray meets the circle of the corner.
            if (ax * flat <= halfWidth - radius || ay * flat <= halfHeight - radius) return flat
            val cx = halfWidth - radius
            val cy = halfHeight - radius
            val along = ax * cx + ay * cy
            val inside = along * along - (cx * cx + cy * cy - radius * radius)
            return along + sqrt(inside.coerceAtLeast(0f))
        }
    }
}

/**
 * Draws [content] for the folder on show, if any. Only this is composed again when a folder
 * opens or closes, not whatever holds it.
 */
@Composable
internal fun FolderLayer(opening: FolderOpening, content: @Composable (ShownFolder) -> Unit) {
    val shown = opening.shown ?: return
    content(shown)
}

/** What a touch on an open folder turned out to be. */
private enum class Touch { TAP, DRAG, HELD, GONE }

/** How far out of the panel a held app must go to be taken out of the folder. */
private val CARRY_OUT_MARGIN = 20.dp

/** How long an app takes to cover the screen as it opens, before the folder behind it closes. */
private const val LAUNCH_CLOSE_DELAY_MS = 600L

/** Moves things into place the way the whole launcher does: quick, soft, no bounce at the end. */
private fun glideSpring(stiffnessScale: Float) = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow * stiffnessScale,
    visibilityThreshold = Offset(0.5f, 0.5f),
)

/** The glass leads, the icons follow: the panel is a little further along than [progress]. */
private fun lead(progress: Float): Float =
    if (progress < 1f) 1f - (1f - progress.coerceAtLeast(0f)).pow(1.25f) else progress

internal fun smoothStep(from: Float, to: Float, value: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private fun Offset.rounded(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())

/** Waits until every finger is lifted. */
private suspend fun AwaitPointerEventScope.waitForAllUp() {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) return
    }
}

/**
 * A still picture of an open folder, for the settings: the panel with its name and apps, in
 * the look being adjusted.
 */
@Composable
internal fun FolderPanelPicture(
    name: String,
    apps: List<AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
    look: FolderLook = LocalFolderLook.current,
) {
    if (apps.isEmpty()) return
    val density = LocalDensity.current
    val glass = look.panelGlass
    val onGlass = glass.spec(look.panelOpacity)
    val labelStyle = appLabelStyle().copy(color = onGlass.content)
    val columns = FolderGrid.columns(apps.size, most = spec.columns, chosen = look.columns)
    val rows = FolderGrid.rows(apps.size, columns)
    val labelWidthPx = with(density) { (cellWidth - 4.dp).roundToPx() }.coerceAtLeast(1)
    val labels = rememberLabelMetricsOf(remember(apps) { apps.map { it.label } }, labelWidthPx, labelStyle, look.panelNames)
    val iconPx = with(density) { spec.iconSizeDp.dp.roundToPx() }
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier
            .kiriGlass(glass, PanelShape, look.panelOpacity, vivid = glass.vivid)
            .padding(look.panelMarginDp.dp),
    ) {
        if (look.panelTitle) {
            BasicText(
                text = name,
                maxLines = 1,
                style = KiriType.body.copy(color = onGlass.content, fontSize = 18.sp, lineHeight = 22.sp),
                modifier = Modifier
                    .height(PANEL_TITLE)
                    .padding(start = 2.dp),
            )
        }
        Layout(
            content = {
                for (app in apps) {
                    key(app.key) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val picture by rememberAppIcon(app, repository, iconPx)
                            IconFace(picture, Modifier.size(spec.iconSizeDp.dp))
                            if (look.panelNames) {
                                Spacer(Modifier.height(LABEL_GAP))
                                BasicText(
                                    text = app.label,
                                    style = labels.styleFor(app.label),
                                    modifier = Modifier
                                        .width(cellWidth - 4.dp)
                                        .heightIn(min = labels.height),
                                )
                            }
                        }
                    }
                }
            },
        ) { measurables, _ ->
            val cell = cellWidth.roundToPx()
            val gap = look.panelRowGapDp.dp.roundToPx()
            val placeables = measurables.map { it.measure(Constraints(maxWidth = cell)) }
            val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
            layout(cell * columns, rowHeight * rows + gap * (rows - 1)) {
                placeables.forEachIndexed { index, placeable ->
                    val (column, row) = FolderGrid.cell(index, apps.size, columns)
                    placeable.place(
                        x = (column * cell + (cell - placeable.width) / 2f).roundToInt(),
                        y = row * (rowHeight + gap),
                    )
                }
            }
        }
    }
}

/**
 * Folders over a piece of the wallpaper, for the settings: a few closed ones with their names,
 * and one of them open. The folders of the home screen are used when there are any; otherwise
 * some are made up from the first apps.
 */
@Composable
internal fun FolderSamples(
    layout: HomeLayout,
    apps: List<AppEntry>,
    appsByKey: Map<String, AppEntry>,
    spec: GridSpec,
    look: FolderLook,
    repository: AppRepository,
    modifier: Modifier = Modifier,
) {
    val names = listOf(
        stringResource(R.string.folder_games),
        stringResource(R.string.folder_photos),
        stringResource(R.string.folder_productivity),
    )
    val samples = remember(layout, apps, look.minis, names) {
        val real = layout.folders.filter { it.appKeys.size >= 2 }.take(SAMPLES)
        val sizes = listOf(4, 7, look.minis + 3)
        var start = 0
        val made = (real.size until SAMPLES).map { index ->
            val size = sizes[index]
            val keys = (apps.drop(start) + apps).distinctBy { it.key }.take(size).map { it.key }
            start += size
            ItemContent.Folder("sample$index", names[index], keys)
        }
        (real + made).filter { it.appKeys.size >= 2 }
    }
    if (samples.isEmpty()) return
    val small = spec.copy(iconSizeDp = spec.iconSizeDp.coerceAtMost(SAMPLE_ICON_DP))
    val iconPx = with(LocalDensity.current) { small.iconSizeDp.dp.roundToPx() }
    val nameStyle = homeLabelStyle()
    val faceCell = (small.iconSizeDp * 1.5f).dp
    CompositionLocalProvider(LocalFolderLook provides look) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KiriTheme.colors.previewBackdrop)
                // The blurred wallpaper, so that the glass on it matches what is around it.
                .backdrop()
                .padding(12.dp),
        ) {
            val open = samples.getOrElse(1) { samples.first() }
            val openApps = open.appKeys.mapNotNull { appsByKey[it] }
            val columns = FolderGrid.columns(openApps.size, most = spec.columns, chosen = look.columns)
            val room = maxWidth - faceCell - 12.dp - look.panelMarginDp.dp * 2
            val cellWidth = minOf((small.iconSizeDp + look.panelGapDp).dp, room / columns)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(faceCell),
                ) {
                    for (sample in samples) {
                        key(sample.id) {
                            FolderFace(
                                folder = sample,
                                appsByKey = appsByKey,
                                repository = repository,
                                iconSizePx = iconPx,
                                look = look,
                                modifier = Modifier.size(small.iconSizeDp.dp),
                            )
                            if (spec.showLabels && look.showName) {
                                BasicText(text = folderName(sample), style = nameStyle, maxLines = 2, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                FolderPanelPicture(
                    name = folderName(open),
                    apps = openApps,
                    spec = small,
                    repository = repository,
                    cellWidth = cellWidth,
                    look = look,
                )
            }
        }
    }
}

/** How many folders the settings show. */
private const val SAMPLES = 3

/** The icons of the settings preview are no larger than this, so that it all fits. */
private const val SAMPLE_ICON_DP = 46
