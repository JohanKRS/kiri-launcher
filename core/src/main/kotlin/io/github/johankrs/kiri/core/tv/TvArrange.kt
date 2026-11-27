package io.github.johankrs.kiri.core.tv

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A box on the TV's screen. Every size of the TV's home screen is given in the units of a screen
 * [TvArrange.WIDTH] wide, as most TVs are in dp: on any other the whole screen is drawn larger.
 */
data class TvBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

enum class TvDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Where the banners of the TV's home screen are: [boxes] in order, each as wide as
 * [bannerWidth] and as high as [bannerHeight] (the name, when shown, goes under it).
 */
class TvPlacement(
    val boxes: List<TvBox>,
    val bannerWidth: Float,
    val bannerHeight: Float,
    /** How far the banners are moved along to keep the focus in sight: hand it back next time. */
    val scroll: Float,
    /** In the grid, a banner whose top is above this went under the top of the screen: it is not shown. */
    val shownFrom: Float,
    /** How much larger the banner with the focus is. */
    val focusScale: Float,
)

/** How the TV's home screen is laid out. Pure geometry: what draws it moves things where this says. */
object TvArrange {
    const val WIDTH = 960f

    /** The usual height of a TV's screen, in the same units. */
    const val HEIGHT = 540f

    private const val MARGIN = 56f
    private const val SPREAD_MARGIN = 30f
    private const val TOP = 92f
    private const val TOP_WITH_WIDGETS = 262f
    private const val BOTTOM = 40f
    private const val BOTTOM_WITH_WIDGETS = 28f

    /** The room under a banner for its name. */
    const val NAME_ROOM = 30f
    private const val GRID_GAP_X = 26f
    private const val GRID_GAP_Y = 30f
    private const val GRID_GAP_Y_WITH_WIDGETS = 22f
    private const val CAROUSEL_GAP = 30f
    private const val CAROUSEL_FLOOR = 46f

    const val GRID_FOCUS_SCALE = 1.1f
    const val CAROUSEL_FOCUS_SCALE = 1.16f

    /** How high a widget is: they all are, whatever their width. */
    const val WIDGET_HEIGHT = 140f
    private const val WIDGET_GAP = 18f
    private const val WIDGETS_TOP_CAROUSEL = 116f

    /**
     * Where the widgets go: one row at the top of the screen, aligned like the banners. [widths]
     * are the widgets' own, left to right; when they would not fit side by side, they share the
     * width there is, so the row never runs off the screen.
     */
    fun widgets(widths: List<Float>, align: TvAlign, arrangement: TvArrangement): List<TvBox> {
        val n = widths.size
        if (n == 0) return emptyList()
        val top = if (arrangement == TvArrangement.CAROUSEL) WIDGETS_TOP_CAROUSEL else TOP
        val margin = if (align == TvAlign.SPREAD) SPREAD_MARGIN else MARGIN
        val room = WIDTH - 2 * margin - WIDGET_GAP * (n - 1)
        val sum = widths.sum()
        val ws = if (sum > room) widths.map { floor(it * room / sum) } else widths
        val total = ws.sum() + WIDGET_GAP * (n - 1)
        val xs = FloatArray(n)
        var x = when {
            align == TvAlign.CENTER || n == 1 && align == TvAlign.SPREAD -> (WIDTH - total) / 2f
            else -> margin
        }
        val gap = if (align == TvAlign.SPREAD && n > 1) (WIDTH - 2 * margin - ws.sum()) / (n - 1) else WIDGET_GAP
        for (i in 0 until n) {
            xs[i] = x
            x += ws[i] + gap
        }
        return List(n) { i -> TvBox(xs[i], top, ws[i], WIDGET_HEIGHT) }
    }

    /** How high a banner is for its width: banners are 16:9. */
    fun heightOf(width: Float): Float = Math.round(width * 9f / 16f).toFloat()

    /**
     * Where [count] banners go on a screen [height] high (in units of a [WIDTH] wide one),
     * with the focus on [focus]. [scroll] is what the last placement gave back. [withWidgets]
     * is when widgets take the top of the screen.
     */
    fun place(
        count: Int,
        look: TvLook,
        height: Float,
        focus: Int,
        scroll: Float,
        withWidgets: Boolean = false,
    ): TvPlacement {
        val bw = look.bannerWidthDp
        val bh = heightOf(bw)
        val nameRoom = if (look.names == TvNameShow.NEVER) 0f else NAME_ROOM
        val focused = focus.coerceIn(0, max(0, count - 1))
        return when (look.arrangement) {
            TvArrangement.GRID -> grid(count, look.align, bw, bh, nameRoom, height, focused, scroll, withWidgets, look.gapScale)
            TvArrangement.CAROUSEL -> carousel(count, look.align, bw, bh, nameRoom, height, focused, scroll, look.gapScale)
        }
    }

    private fun grid(
        count: Int,
        align: TvAlign,
        bw: Float,
        bh: Float,
        nameRoom: Float,
        height: Float,
        focus: Int,
        scroll: Float,
        withWidgets: Boolean,
        gapScale: Float,
    ): TvPlacement {
        val top = if (withWidgets) TOP_WITH_WIDGETS else TOP
        val bottom = if (withWidgets) BOTTOM_WITH_WIDGETS else BOTTOM
        val gx = GRID_GAP_X * gapScale
        val gy = ((if (withWidgets) GRID_GAP_Y_WITH_WIDGETS else GRID_GAP_Y) - if (nameRoom == 0f) 8f else 0f) * gapScale
        val cols = max(1, floor((WIDTH - 2 * MARGIN + gx) / (bw + gx)).toInt())
        val rows = max(1, ceil(count / cols.toFloat()).toInt())
        val cellH = bh + nameRoom
        val contentH = rows * cellH + (rows - 1) * gy
        val room = height - top - bottom
        val fullGap = if (cols > 1) (WIDTH - 2 * SPREAD_MARGIN - cols * bw) / (cols - 1) else 0f

        val xs = FloatArray(count) { i ->
            val r = i / cols
            val c = i % cols
            val inRow = if (r == rows - 1) count - r * cols else cols
            when (align) {
                TvAlign.START -> MARGIN + c * (bw + gx)
                TvAlign.CENTER -> (WIDTH - (inRow * bw + (inRow - 1) * gx)) / 2f + c * (bw + gx)
                TvAlign.SPREAD -> when {
                    rows == 1 && inRow > 1 -> SPREAD_MARGIN + c * (bw + (WIDTH - 2 * SPREAD_MARGIN - inRow * bw) / (inRow - 1))
                    rows == 1 -> (WIDTH - bw) / 2f
                    else -> SPREAD_MARGIN + c * (bw + fullGap)
                }
            }
        }

        var moved = scroll
        val y0: Float
        if (contentH <= room) {
            // All of it fits: it sits in the middle of the room, or right under the widgets.
            moved = 0f
            y0 = if (withWidgets) top else top + (room - contentH) / 2f - 6f
        } else {
            val fy = (focus / cols) * (cellH + gy)
            if (fy - moved < 0f) moved = fy
            if (fy + cellH - moved > room) moved = fy + cellH - room
            moved = moved.coerceIn(0f, contentH - room)
            y0 = top - moved
        }
        val boxes = List(count) { i -> TvBox(xs[i], y0 + (i / cols) * (cellH + gy), bw, bh) }
        return TvPlacement(boxes, bw, bh, moved, shownFrom = top - 12f, focusScale = GRID_FOCUS_SCALE)
    }

    private fun carousel(
        count: Int,
        align: TvAlign,
        bw: Float,
        bh: Float,
        nameRoom: Float,
        height: Float,
        focus: Int,
        scroll: Float,
        gapScale: Float,
    ): TvPlacement {
        val g = CAROUSEL_GAP * gapScale
        // Low on the screen, so the wallpaper shows above it.
        val y = height - CAROUSEL_FLOOR - bh - max(nameRoom, 26f)
        if (align == TvAlign.CENTER) {
            // The banner with the focus stays in the middle and the others slide past it.
            val boxes = List(count) { i -> TvBox((WIDTH - bw) / 2f + (i - focus) * (bw + g), y, bw, bh) }
            return TvPlacement(boxes, bw, bh, 0f, shownFrom = Float.NEGATIVE_INFINITY, focusScale = CAROUSEL_FOCUS_SCALE)
        }
        var step = bw + g
        var left = MARGIN
        if (align == TvAlign.SPREAD) {
            val fit = max(2, floor((WIDTH - 2 * SPREAD_MARGIN + g) / (bw + g)).toInt())
            val shown = min(fit, count)
            step = if (shown > 1) bw + (WIDTH - 2 * SPREAD_MARGIN - shown * bw) / (shown - 1) else bw + g
            left = SPREAD_MARGIN
        }
        val travel = WIDTH - left - bw - left
        // The focus keeps half a banner of what comes next in sight, on either side.
        val peek = min(step * 0.5f, travel / 3f)
        val fx = focus * step
        var moved = scroll
        if (fx - moved < peek) moved = fx - peek
        if (fx - moved > travel - peek) moved = fx - (travel - peek)
        moved = moved.coerceIn(0f, max(0f, (count - 1) * step - travel))
        val boxes = List(count) { i -> TvBox(left + i * step - moved, y, bw, bh) }
        return TvPlacement(boxes, bw, bh, moved, shownFrom = Float.NEGATIVE_INFINITY, focusScale = CAROUSEL_FOCUS_SCALE)
    }
}

/** Where the focus goes when an arrow of the remote is pressed. */
object TvFocus {
    /**
     * The one of [candidates] that is next to [from] towards [direction], or -1 when there is
     * none: the closest ahead, a step to the side counting more than a step ahead. With [sameRow]
     * left and right stay in [from]'s row, as in a grid, instead of going up or down to another.
     */
    fun nearest(from: TvBox, candidates: List<TvBox?>, direction: TvDirection, sameRow: Boolean = false): Int {
        val sideways = direction == TvDirection.LEFT || direction == TvDirection.RIGHT
        var best = -1
        var bestScore = Float.MAX_VALUE
        candidates.forEachIndexed { index, box ->
            if (box == null || box == from) return@forEachIndexed
            if (sameRow && sideways && (box.bottom <= from.top || box.top >= from.bottom)) return@forEachIndexed
            val dx = box.centerX - from.centerX
            val dy = box.centerY - from.centerY
            val ahead: Boolean
            val main: Float
            val side: Float
            when (direction) {
                TvDirection.RIGHT -> {
                    ahead = box.left >= from.right - 6f || dx > max(8f, from.width * 0.5f)
                    main = dx
                    side = abs(dy)
                }
                TvDirection.LEFT -> {
                    ahead = box.right <= from.left + 6f || -dx > max(8f, from.width * 0.5f)
                    main = -dx
                    side = abs(dy)
                }
                TvDirection.DOWN -> {
                    ahead = box.top >= from.bottom - 6f || dy > max(8f, from.height * 0.5f)
                    main = dy
                    side = abs(dx)
                }
                TvDirection.UP -> {
                    ahead = box.bottom <= from.top + 6f || -dy > max(8f, from.height * 0.5f)
                    main = -dy
                    side = abs(dx)
                }
            }
            if (!ahead || main <= 0f) return@forEachIndexed
            val score = main + side * 2.2f
            if (score < bestScore) {
                bestScore = score
                best = index
            }
        }
        return best
    }
}

/**
 * "All apps" on a TV: the TV's inputs, then the apps, as tiles in rows of [COLUMNS] under the
 * name of each part. Sizes are in the units of [TvArrange].
 */
class TvAllPlacement(
    /** The tiles of the inputs, then those of the apps. */
    val tiles: List<TvBox>,
    /** Where the name of each part is, from the top: the inputs' (null when there are none), the apps'. */
    val inputsTitleTop: Float?,
    val appsTitleTop: Float,
    val height: Float,
) {
    /** How far to move the list up so that [focus] sits a little above the middle of a [room] high view. */
    fun scrollFor(focus: Int, room: Float): Float {
        val tile = tiles.getOrNull(focus) ?: return 0f
        return (tile.top - room / 2f + 50f).coerceIn(0f, max(0f, height - room + 20f))
    }

    companion object {
        const val COLUMNS = 6
        const val SIDE = 44f
        private const val GAP_X = 18f
        private const val GAP_Y = 16f
        private const val TITLE = 36f
        private const val PART_GAP = 18f

        /** The room for a tile's name under its banner, and the padding around both. */
        const val TILE_PADDING = 6f
        const val TILE_NAME = 23f

        fun place(inputs: Int, apps: Int): TvAllPlacement {
            val tileW = (TvArrange.WIDTH - 2 * SIDE - (COLUMNS - 1) * GAP_X) / COLUMNS
            val bannerW = tileW - 2 * TILE_PADDING
            val tileH = TILE_PADDING + TvArrange.heightOf(bannerW) + TILE_NAME + TILE_PADDING
            val tiles = ArrayList<TvBox>(inputs + apps)
            var y = 0f
            fun part(count: Int) {
                for (i in 0 until count) {
                    val r = i / COLUMNS
                    val c = i % COLUMNS
                    tiles += TvBox(SIDE + c * (tileW + GAP_X), y + r * (tileH + GAP_Y), tileW, tileH)
                }
                val rows = ceil(count / COLUMNS.toFloat()).toInt()
                y += rows * tileH + max(0, rows - 1) * GAP_Y + PART_GAP
            }
            var inputsTitle: Float? = null
            if (inputs > 0) {
                inputsTitle = y
                y += TITLE
                part(inputs)
            }
            val appsTitle = y
            y += TITLE
            part(apps)
            return TvAllPlacement(tiles, inputsTitle, appsTitle, y)
        }
    }
}
