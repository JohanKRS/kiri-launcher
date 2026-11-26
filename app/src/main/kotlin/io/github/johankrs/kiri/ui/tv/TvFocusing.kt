package io.github.johankrs.kiri.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.johankrs.kiri.core.tv.TvBox
import io.github.johankrs.kiri.core.tv.TvDirection
import io.github.johankrs.kiri.core.tv.TvFocus

/** The keys of a remote the TV's home screen answers. */
internal enum class TvKey { UP, DOWN, LEFT, RIGHT, OK }

internal fun KeyEvent.tvKey(): TvKey? = when (key) {
    Key.DirectionUp -> TvKey.UP
    Key.DirectionDown -> TvKey.DOWN
    Key.DirectionLeft -> TvKey.LEFT
    Key.DirectionRight -> TvKey.RIGHT
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> TvKey.OK
    else -> null
}

internal fun TvKey.direction(): TvDirection? = when (this) {
    TvKey.UP -> TvDirection.UP
    TvKey.DOWN -> TvDirection.DOWN
    TvKey.LEFT -> TvDirection.LEFT
    TvKey.RIGHT -> TvDirection.RIGHT
    TvKey.OK -> null
}

/**
 * The places the focus can be on in a menu or a panel of the TV, found where they are drawn.
 * The whole TV's screen keeps the focus of the system itself, and moves this one: so the
 * remote can never reach what is behind a menu, and every arrow goes where it looks like it goes.
 */
@Stable
internal class TvTargets(first: String? = null) {
    var focused by mutableStateOf(first)

    private val placed = LinkedHashMap<String, LayoutCoordinates>()
    private var root: LayoutCoordinates? = null

    /** Goes on the panel all of them are measured from, inside whatever scales it as it opens. */
    val rootModifier: Modifier = Modifier.onGloballyPositioned { root = it }

    fun place(id: String, coordinates: LayoutCoordinates) {
        placed[id] = coordinates
    }

    fun forget(id: String) {
        placed.remove(id)
    }

    fun isPlaced(id: String): Boolean = placed[id]?.isAttached == true

    /** Moves the focus to the next place towards [direction]; false when there is none. */
    fun move(direction: TvDirection): Boolean {
        val from = focused?.let(::boxOf) ?: return false
        val ids = placed.keys.toList()
        val index = TvFocus.nearest(from, ids.map(::boxOf), direction)
        if (index < 0) return false
        focused = ids[index]
        return true
    }

    private fun boxOf(id: String): TvBox? {
        val base = root?.takeIf { it.isAttached } ?: return null
        val coordinates = placed[id]?.takeIf { it.isAttached } ?: return null
        val r = base.localBoundingBoxOf(coordinates, clipBounds = false)
        return TvBox(r.left, r.top, r.width, r.height)
    }
}

/** Makes this one of [targets], as [id]. */
@Composable
internal fun Modifier.tvTarget(targets: TvTargets, id: String): Modifier {
    DisposableEffect(targets, id) { onDispose { targets.forget(id) } }
    return onGloballyPositioned { targets.place(id, it) }
}
