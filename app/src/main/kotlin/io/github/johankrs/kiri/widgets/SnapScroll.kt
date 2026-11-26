package io.github.johankrs.kiri.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * How far a widget that holds more lines than it shows has been scrolled, counted in lines.
 *
 * It always comes to rest on a whole line: a widget never stands with something cut in half.
 * Whoever lays the lines out says how many there are and how tall one is, and reads [at] while
 * placing them; [snapScroll] moves it with the finger.
 */
@Stable
class SnapScroll {
    private var position by mutableFloatStateOf(0f)

    /** Lines that do not fit at once: how far it can go. Zero when everything is on show. */
    var hidden by mutableIntStateOf(0)

    /** Lines on show at once. */
    var shown by mutableIntStateOf(1)

    /** The height of a line, in pixels. */
    var linePx = 1f

    /** True from when a finger starts moving it until it has come to rest. */
    var moving by mutableStateOf(false)
        private set

    /** Lines scrolled away at the top. A whole number at rest. */
    val at: Float get() = position.coerceIn(0f, hidden.toFloat())

    /** Whether it is standing on a whole line. */
    val resting: Boolean get() = at == at.roundToInt().toFloat()

    /** Whether it can still go the way of [lines]: down the list when positive. */
    fun canGo(lines: Float): Boolean = when {
        lines > 0f -> at < hidden
        lines < 0f -> at > 0f
        else -> false
    }

    /** Follows a finger that moved [pixels], where going down the list is positive. */
    fun dragBy(pixels: Float) {
        moving = true
        position = (at + pixels / linePx.coerceAtLeast(1f)).coerceIn(0f, hidden.toFloat())
    }

    /**
     * Glides to rest on a whole line from a finger that let go at [velocity] pixels a second,
     * going down the list when positive. A flick always goes on by a line at least, and never
     * by more than what is on show: what comes next is never flown past unseen.
     */
    suspend fun settle(velocity: Float) {
        val from = at
        val lines = velocity / linePx.coerceAtLeast(1f)
        val nearest = from.roundToInt()
        var target = (from + lines * GLIDE_SECONDS).roundToInt()
        if (abs(lines) > FLICK_LINES && target == nearest) target += sign(lines).toInt()
        target = target.coerceIn(nearest - shown, nearest + shown).coerceIn(0, hidden)
        moving = true
        try {
            animate(
                initialValue = from,
                targetValue = target.toFloat(),
                initialVelocity = lines,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f, visibilityThreshold = 0.002f),
            ) { value, _ -> position = value.coerceIn(0f, hidden.toFloat()) }
            position = target.toFloat()
        } finally {
            moving = false
        }
    }

    /** Moves by whole [lines] at once, as asked by something other than a finger. */
    suspend fun stepBy(lines: Int) {
        val target = (at.roundToInt() + lines).coerceIn(0, hidden)
        moving = true
        try {
            animate(at, target.toFloat(), animationSpec = spring(stiffness = 260f, visibilityThreshold = 0.002f)) { value, _ ->
                position = value
            }
            position = target.toFloat()
        } finally {
            moving = false
        }
    }

    private companion object {
        /** A finger that lets go while moving carries the list on for as far as it goes in this time. */
        const val GLIDE_SECONDS = 0.22f

        /** From this many lines a second on, letting go is a flick. */
        const val FLICK_LINES = 1.2f
    }
}

/**
 * Lets a finger scroll [scroll] up and down. Only a movement the list can still follow is
 * taken: at its top, a finger going down is left to whatever is behind the widget, such as the
 * home screen's own swipe, and the same at its bottom.
 */
@Composable
fun Modifier.snapScroll(scroll: SnapScroll): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(scroll) {
            coroutineScope {
                var settling: Job? = null
                awaitEachGesture {
                    // Seen before what is under the finger: a touch that stops a glide is not a tap.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (scroll.hidden <= 0) return@awaitEachGesture
                    val gliding = settling?.isActive == true
                    settling?.cancel()
                    if (gliding) down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val start = if (gliding) {
                        down
                    } else {
                        // The rest of the touch going down, which what is under the finger may
                        // take for itself: only what follows is looked at for a movement.
                        awaitPointerEvent()
                        // A finger going up brings on what is further down.
                        awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                            if (scroll.canGo(-over)) {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scroll.dragBy(-over)
                            }
                        }
                    }
                    if (start == null) return@awaitEachGesture
                    var last = start
                    val lifted = verticalDrag(start.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scroll.dragBy(-change.positionChange().y)
                        change.consume()
                        last = change
                    }
                    val tracked = -tracker.calculateVelocity().y
                    val took = last.uptimeMillis - down.uptimeMillis
                    val velocity = when {
                        !lifted -> 0f
                        tracked != 0f -> tracked
                        // A flick so short that it left too few points to tell a speed from:
                        // it is taken from where the finger went in the time it took.
                        took in 1..QUICK_MS -> -(last.position.y - down.position.y) * 1000f / took
                        else -> 0f
                    }
                    settling = launch { scroll.settle(velocity) }
                }
            }
        }
        .semantics {
            if (scroll.hidden > 0) {
                verticalScrollAxisRange = ScrollAxisRange(value = { scroll.at }, maxValue = { scroll.hidden.toFloat() })
                scrollBy { _, y ->
                    val lines = sign(y).toInt()
                    if (lines == 0 || !scroll.canGo(lines.toFloat())) {
                        false
                    } else {
                        scope.launch { scroll.stepBy(lines) }
                        true
                    }
                }
            }
        }
}

/**
 * A thin mark along the right side that says there is more than what is on show, and where in
 * it one is: quiet at rest, clearer while moving. It stands [outside] the right edge of what it
 * is put on, and is only there while [scroll] has something hidden.
 */
@Composable
fun Modifier.scrollMark(scroll: SnapScroll, color: Color, outside: Dp): Modifier {
    val strength by animateFloatAsState(
        targetValue = if (scroll.moving) MARK_MOVING else MARK_RESTING,
        animationSpec = tween(durationMillis = if (scroll.moving) 120 else 600),
        label = "scroll mark",
    )
    return drawWithContent {
        drawContent()
        val hidden = scroll.hidden
        if (hidden <= 0) return@drawWithContent
        val all = (scroll.shown + hidden).toFloat()
        val width = MarkWidth.toPx()
        // Shorter than the side it runs along, so it keeps clear of the corners of the pane.
        val inset = size.height * 0.08f
        val track = size.height - inset * 2f
        val length = (track * scroll.shown / all).coerceAtLeast(width * 3f)
        val top = inset + (track - length) * (scroll.at / hidden)
        drawRoundRect(
            color = color.copy(alpha = color.alpha * strength),
            topLeft = Offset(size.width + outside.toPx() - width / 2f, top),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}

/** A finger that is down for no longer than this and leaves no speed to read still flicked. */
private const val QUICK_MS = 180L

private val MarkWidth = 2.5.dp
private const val MARK_RESTING = 0.38f
private const val MARK_MOVING = 0.9f
