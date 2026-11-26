package io.github.johankrs.kiri.core

import kotlin.math.max
import kotlin.math.min

/**
 * The room the home screen has for its grid, in dp: the screen without the system bars, before
 * the margins of the grid are taken off, and how tall the names under the icons are (with the
 * space above them), nothing when names are not shown.
 */
data class HomeRoom(val widthDp: Float, val heightDp: Float, val labelBlockDp: Float)

/**
 * Where the cells of the home screen are, in dp, inside its margins.
 *
 * The icons go from one side margin to the other: the room between two of them follows from
 * the number of columns, the margins and the size of the icons. Between rows there is the same
 * room ([GridSpec.evenGaps]), or a room of its own ([GridSpec.rowGapDp]); the rows then take
 * the height they need, and what is left of it stays free above, below or on both sides of
 * them ([GridSpec.place]).
 *
 * A cell is as wide as an icon. It is as tall as an icon with its name, and [CELL_PAD_DP] above
 * and below, which an app keeps around itself: [gapYDp] is the room between rows less those
 * two, and can be less than nothing. An app's name has the width of its cell and the room
 * beside it ([labelWidthDp]). A widget goes from the top of the icons of its first row to the
 * bottom of the names of its last, and from the left of its first icon to the right of its last.
 */
data class HomeGridGeometry(
    val iconDp: Float,
    val cellWidthDp: Float,
    val cellHeightDp: Float,
    val gapXDp: Float,
    val gapYDp: Float,
    /** How far below the top margin the first row's cells start. */
    val topDp: Float,
) {
    val pitchXDp: Float get() = cellWidthDp + gapXDp
    val pitchYDp: Float get() = cellHeightDp + gapYDp

    /** The room between two icons side by side, and between a name and the icon under it. */
    val shownGapXDp: Float get() = gapXDp
    val shownGapYDp: Float get() = gapYDp + 2 * CELL_PAD_DP

    /** How wide an app's name can be: its cell and the room beside it, less a little. */
    val labelWidthDp: Float get() = max(cellWidthDp, cellWidthDp + gapXDp - LABEL_INSET_DP)

    companion object {
        /** The room an app keeps above its icon and below its name. */
        const val CELL_PAD_DP = 6f

        /** Two names side by side keep at least this much between them. */
        const val LABEL_INSET_DP = 4f

        /** The size of the icons on [spec] in [widthDp] (inside the margins), and the room between them. */
        fun across(spec: GridSpec, widthDp: Float): Pair<Float, Float> {
            val columns = spec.columns.coerceAtLeast(1)
            val icon = spec.iconSizeDp.toFloat()
            if (columns * icon > widthDp) return max(widthDp / columns, 1f) to 0f
            return icon to if (columns > 1) (widthDp - columns * icon) / (columns - 1) else 0f
        }

        /** The room between rows that [spec] asks for, the names left out. */
        private fun wantedRowGap(spec: GridSpec, gapX: Float): Float =
            if (spec.evenGaps) gapX else spec.rowGapDp.toFloat()

        /** The height [rows] rows take, apps' room above and below included. */
        private fun heightOf(rows: Int, content: Float, rowGap: Float): Float =
            rows * content + (rows - 1) * rowGap + 2 * CELL_PAD_DP

        /**
         * The cells of [spec] in [widthDp] by [heightDp], which are inside the margins, with
         * names [labelBlockDp] tall. When the rows do not fit with the room asked between them,
         * that room gets smaller; when they do not fit even touching, they share the height.
         */
        fun of(spec: GridSpec, widthDp: Float, heightDp: Float, labelBlockDp: Float): HomeGridGeometry {
            val (icon, gapX) = across(spec, widthDp)
            val rows = spec.rows.coerceAtLeast(1)
            val content = icon + labelBlockDp
            val most = if (rows > 1) (heightDp - 2 * CELL_PAD_DP - rows * content) / (rows - 1) else Float.MAX_VALUE
            val rowGap = min(max(wantedRowGap(spec, gapX), 0f), most)
            if (rowGap < 0f) {
                return HomeGridGeometry(icon, icon, max(heightDp / rows, 1f), gapX, 0f, 0f)
            }
            val free = max(0f, heightDp - heightOf(rows, content, rowGap))
            val top = when (spec.place) {
                GridPlace.TOP -> 0f
                GridPlace.MIDDLE -> free / 2f
                GridPlace.BOTTOM -> free
            }
            return HomeGridGeometry(
                iconDp = icon,
                cellWidthDp = icon,
                cellHeightDp = content + 2 * CELL_PAD_DP,
                gapXDp = gapX,
                gapYDp = rowGap - 2 * CELL_PAD_DP,
                topDp = top,
            )
        }

        /** The cells of [spec] in [room], whose margins come off first. */
        fun of(spec: GridSpec, room: HomeRoom): HomeGridGeometry =
            of(spec, insideWidth(spec, room), insideHeight(spec, room), room.labelBlockDp)

        /**
         * True when everything [spec] asks for fits in [room] as asked: the icons side by side,
         * and the rows with the room between them they ask for. A setting that would break
         * this is not offered.
         */
        fun fits(spec: GridSpec, room: HomeRoom): Boolean {
            val width = insideWidth(spec, room)
            val height = insideHeight(spec, room)
            if (spec.columns * spec.iconSizeDp > width + 0.01f) return false
            val (icon, gapX) = across(spec, width)
            val content = icon + room.labelBlockDp
            return heightOf(spec.rows.coerceAtLeast(1), content, max(wantedRowGap(spec, gapX), 0f)) <= height + 0.01f
        }

        private fun insideWidth(spec: GridSpec, room: HomeRoom) = room.widthDp - 2 * spec.marginHorizontalDp
        private fun insideHeight(spec: GridSpec, room: HomeRoom) = room.heightDp - spec.marginTopDp - spec.marginBottomDp
    }
}
