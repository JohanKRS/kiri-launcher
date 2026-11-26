package io.github.johankrs.kiri.widgets.market

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** The corners of a pane once it is fully open. */
internal val PaneCorner = 28.dp

/** How dark the home screen gets behind an open pane. */
private const val SCRIM_ALPHA = 0.4f

/** Opening carries a little past the end and settles; closing goes straight home. */
private val Opening = spring<Float>(dampingRatio = 0.82f, stiffness = 240f)
private val Closing = spring<Float>(dampingRatio = 1f, stiffness = 360f)

/**
 * Something the widget opens over the home screen without leaving it: the widget's own glass
 * grows from where it is ([origin], on screen) into the pane, and shrinks back when closed.
 * The home screen stays behind, dimmed from edge to edge. Back, or a tap beside the pane, closes.
 *
 * [onCover] tells the widget when its picture here has taken its place, so it can step out of
 * the way, and when it must be back. [onClosed] comes once the way back is over.
 *
 * @param start the widget as it looks right now, inert: what the pane grows out of.
 * @param pane the pane itself, with its own glass. Call `close` to send it back.
 */
@Composable
fun MarketOverlay(
    origin: Rect,
    originCorner: Dp,
    onCover: (Boolean) -> Unit,
    onClosed: () -> Unit,
    start: @Composable () -> Unit,
    pane: @Composable (close: () -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val cover by rememberUpdatedState(onCover)
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(closing) {
        if (!closing) {
            // Only once the picture of the widget is on screen may the widget itself go.
            withFrameNanos { }
            cover(true)
            progress.animateTo(1f, Opening)
        } else {
            progress.animateTo(0f, Closing)
            // The widget comes back under its picture before the picture leaves.
            cover(false)
            withFrameNanos { }
            withFrameNanos { }
            closed()
        }
    }
    DisposableEffect(Unit) { onDispose { cover(false) } }

    OverTheWindow(returnTo = origin) {
        BackHandler { closing = true }
        MorphScene(
            progress = { progress.value },
            origin = origin,
            originCorner = originCorner,
            onDismiss = { closing = true },
            start = start,
            pane = { pane { closing = true } },
        )
    }
}

/**
 * Shows [content] over everything else in this window, edge to edge and under the system bars.
 *
 * It is a layer in the launcher's own window and not a window of its own, so what it draws
 * lands in the same frame as the home screen under it, and its dimming covers the whole screen.
 * While it is up, nothing behind it takes the focus or is read aloud; when it goes, the focus of
 * a remote or a keyboard is sent back to what is at [returnTo] on screen.
 */
@Composable
private fun OverTheWindow(returnTo: Rect, content: @Composable () -> Unit) {
    val anchor = LocalView.current
    val parent = rememberCompositionContext()
    val current by rememberUpdatedState(content)
    val backTo by rememberUpdatedState(returnTo)
    DisposableEffect(anchor) {
        val root = anchor.rootView.findViewById<ViewGroup>(android.R.id.content) ?: anchor.rootView as ViewGroup
        val behind = (0 until root.childCount).map { root.getChildAt(it) }
        val before = behind.map { view ->
            Triple(view, (view as? ViewGroup)?.descendantFocusability, view.importantForAccessibility)
        }
        val layer = ComposeView(anchor.context).apply {
            // What the pane remembers (the range of the chart, the amount typed) is saved under
            // a name of its own, apart from the home screen's.
            setTag(androidx.compose.ui.R.id.compose_view_saveable_id_tag, "kiri-market-pane")
            setParentCompositionContext(parent)
            setContent { current() }
        }
        root.addView(layer, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        for (view in behind) {
            (view as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // With a remote or a keyboard the focus has to come along; a finger needs none.
        val keys = !anchor.isInTouchMode
        if (keys) layer.requestFocus()
        onDispose {
            root.removeView(layer)
            for ((view, focusability, accessibility) in before) {
                if (focusability != null) (view as ViewGroup).descendantFocusability = focusability
                view.importantForAccessibility = accessibility
            }
            if (keys) {
                // From just above the widget, downwards: the first thing found is the widget.
                val at = IntArray(2).also(anchor::getLocationOnScreen)
                val top = backTo.top.roundToInt() - at[1]
                val above = android.graphics.Rect(
                    backTo.left.roundToInt() - at[0],
                    top - 1,
                    backTo.right.roundToInt() - at[0],
                    top,
                )
                anchor.requestFocus(View.FOCUS_DOWN, above)
            }
        }
    }
}

/**
 * Where the growing glass is right now. The layout writes it and the drawing reads it on the
 * same frame via `progress()`, so plain fields suffice without triggering extra invalidations.
 */
private class MorphGeometry {
    var rect: Rect = Rect.Zero
    var corner: Float = 0f

    /** How far past [rect] the shadows of the glass on the way still reach. */
    var room: Float = 0f
}

private fun smooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * One moment of the way between the widget and the pane, at [progress] from 0 (the widget, in
 * its place) to 1 (the pane, open). It holds no state, so any moment can be drawn on its own.
 *
 * The glass is one shape that moves and grows. Inside it the pane fades in over the widget, which
 * then fades out under it, each scaled to the width of the shape, so nothing is ever squeezed.
 */
@Composable
fun MorphScene(
    progress: () -> Float,
    origin: Rect,
    originCorner: Dp,
    onDismiss: () -> Unit,
    start: @Composable () -> Unit,
    pane: @Composable () -> Unit,
) {
    val geometry = remember { MorphGeometry() }
    val shapePath = remember { Path() }
    // The widget's picture is only needed on the way; once open it is taken out.
    val onTheWay by remember(progress) { derivedStateOf { progress() < 0.999f } }
    Box(Modifier.fillMaxSize()) {
        // A tap beside the pane closes it. The pane sits over this, as a sibling, so taps on
        // the pane never reach it.
        // It only listens for taps: with a remote, the focus goes to the pane, never to it.
        val dismiss by rememberUpdatedState(onDismiss)
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawRect(Color.Black, alpha = SCRIM_ALPHA * progress().coerceIn(0f, 1f)) }
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        Layout(
            content = {
                Box(Modifier.clearAndSetSemantics { }) { if (onTheWay) start() }
                // Taps on the pane itself stay in it.
                Box(Modifier.pointerInput(Unit) { }) { pane() }
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .drawWithContent {
                    // Read here so every step of the way is drawn in its own frame.
                    val p = progress()
                    if (p >= 0.999f) {
                        // Resting open, nothing needs cutting.
                        drawContent()
                    } else {
                        // The shape is let out by as far as a shadow reaches, so the shadows of
                        // the widget and of the pane are there all the way: none of them shows
                        // up at once when the pane comes to rest, or goes at once as it leaves.
                        val room = geometry.room
                        shapePath.rewind()
                        shapePath.addRoundRect(
                            RoundRect(geometry.rect.inflate(room), CornerRadius(geometry.corner + room)),
                        )
                        clipPath(shapePath) { this@drawWithContent.drawContent() }
                    }
                },
        ) { measurables, constraints ->
            val paneWidth = minOf(constraints.maxWidth, MaxPaneWidth.roundToPx())
            val opened = measurables[1].measure(Constraints(maxWidth = paneWidth, maxHeight = constraints.maxHeight))
            val opening = Size(opened.width.toFloat(), opened.height.toFloat())
            val hasOrigin = origin.width >= 1f && origin.height >= 1f
            val widget = measurables[0].measure(
                if (hasOrigin) {
                    Constraints.fixed(origin.width.roundToInt(), origin.height.roundToInt())
                } else {
                    Constraints.fixed(0, 0)
                },
            )
            val targetCorner = PaneCorner.toPx()
            val startCorner = originCorner.toPx()

            layout(constraints.maxWidth, constraints.maxHeight) {
                val p = progress()
                val area = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val widgetRect = if (hasOrigin) {
                    origin.translate(-(coordinates?.positionOnScreen() ?: Offset.Zero))
                } else {
                    null
                }
                // The pane grows from where the widget is. It is never taller than the room
                // there is: past that, it scrolls inside itself.
                val target = restingPlace(widgetRect, opening, area)
                // Without a place to grow from, the pane simply appears where it rests.
                val from = widgetRect ?: target
                val rect = lerp(from, target, p)
                geometry.rect = rect
                geometry.corner = lerp(startCorner, targetCorner, p.coerceIn(0f, 1f))
                // The pane is drawn at its own size and scaled: so is its shadow.
                val paneScale = if (target.width > 0f) rect.width / target.width else 1f
                geometry.room = ShadowRoom.toPx() * maxOf(1f, paneScale)

                if (hasOrigin) {
                    widget.placeWithLayer(rect.left.roundToInt(), rect.top.roundToInt()) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = if (from.width > 0f) rect.width / from.width else 1f
                        scaleX = scale
                        scaleY = scale
                        // It stays under the pane until the pane's glass covers it, so the
                        // glass is never thin on the way.
                        alpha = 1f - smooth((p - 0.15f) / 0.4f)
                    }
                }
                opened.placeWithLayer(rect.left.roundToInt(), rect.top.roundToInt()) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val scale = if (target.width > 0f) rect.width / target.width else 1f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (hasOrigin) smooth(p / 0.4f) else smooth(p / 0.6f)
                }
            }
        }
    }
}

private val MaxPaneWidth = 480.dp

/**
 * A little more than the shadow under a tile of glass reaches past its edge (about 7 dp: see
 * `GlassBevel.paneShadow`).
 */
private val ShadowRoom = 9.dp

/**
 * Where the open pane of [pane] size rests in an [area], for a widget at [widget].
 *
 * The pane sits in the area as the widget sits in it: a widget at the top opens downwards from
 * the top, one at the bottom opens upwards from the bottom, one in the middle opens both ways
 * evenly, and every place in between in proportion. So the pane always covers the widget it
 * comes from and never leaves the area. Without a widget it rests at the bottom, in the middle,
 * where a thumb reaches it.
 */
internal fun restingPlace(widget: Rect?, pane: Size, area: Size): Rect {
    fun along(start: Float, length: Float, open: Float, room: Float): Float {
        val spare = room - open
        if (spare <= 0f) return spare / 2f
        // How far along its own free room the widget is: 0 at the start, 1 at the end.
        val free = room - length
        val at = if (free <= 0f) 0.5f else (start / free).coerceIn(0f, 1f)
        return at * spare
    }
    if (widget == null) {
        return Rect(Offset((area.width - pane.width) / 2f, area.height - pane.height), pane)
    }
    return Rect(
        Offset(
            along(widget.left, widget.width, pane.width, area.width),
            along(widget.top, widget.height, pane.height, area.height),
        ),
        pane,
    )
}
