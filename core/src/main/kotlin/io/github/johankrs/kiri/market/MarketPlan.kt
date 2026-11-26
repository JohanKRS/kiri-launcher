package io.github.johankrs.kiri.market

import kotlin.math.floor
import kotlin.math.min

/** The shapes the widget takes. */
enum class MarketLayout {
    /** One item, with its price as large as the space allows. */
    SINGLE,

    /** Small blocks, one per item: symbol, price and change stacked. For short or narrow spaces. */
    TILES,

    /** One line per item with name, chart, price and change, in one or more columns. */
    LIST,
}

/** What the widget shows in a given space. */
data class MarketPlan(
    val layout: MarketLayout,
    /** Items side by side: columns of the list or of the tiles. */
    val columns: Int,
    /** Tighter items: the list drops the name and puts the change beside the price; tiles lose a line. */
    val dense: Boolean,
    val showChart: Boolean,
    /** The title and the time of the last update, above the list. */
    val showHeader: Boolean,
    /** How many items are expected to fit. */
    val shown: Int,
    /** How much larger than the base size everything is drawn, in percent. Never under 100. */
    val scalePercent: Int = 100,
    /** Each item has room for one more line: its price before the open or after the close. */
    val showExtended: Boolean = false,
)

/**
 * Decides what the market widget shows in the space it is given, so that it can take any size.
 *
 * Two rules hold at every size:
 * - **Text never shrinks.** The base text size is the smallest; a small space gets fewer things,
 *   not smaller ones. Spare space makes everything larger, up to [MAX_SCALE_PERCENT].
 * - **Items come before decoration.** The title, the name and the chart only appear when they do
 *   not push out an item that would otherwise fit.
 *
 * The price outside regular hours is part of an item, not decoration: when some item has one,
 * every item gets a line more of height, and fewer may fit. It is only left out where not even
 * one item would fit whole with it.
 *
 * Sizes are in dp at the base text size: the caller divides the real size by the person's font
 * scale, so larger system text simply counts as less room.
 */
object MarketPlanner {
    const val MAX_SCALE_PERCENT = 130
    private val SCALES = listOf(MAX_SCALE_PERCENT, 115)

    // The list.
    const val LIST_PADDING_H = 16f
    const val LIST_PADDING_V = 6f
    const val COLUMN_GAP = 20f
    const val ROW = 50f
    const val DENSE_ROW = 34f
    /** What the line with the price outside regular hours adds to a line of the list. */
    const val EXTENDED_LINE = 18f
    /** The same line in a tile or under a single item, where it sits right under the change. */
    const val EXTENDED_TILE_LINE = 16f
    const val HEADER = 50f
    /** Width of the price and its change at the end of a line, with the space before it. */
    const val PRICE_BLOCK = 94f
    /** Width of the small chart, with the space before it. */
    const val CHART_BLOCK = 64f
    private const val LIST_MIN_WIDTH = 220f
    private const val COLUMN_MIN = 200f
    private const val CHART_MIN = 250f
    private const val DENSE_MIN = 238f
    private const val DENSE_CHART_MIN = 302f
    private const val HEADER_MIN_HEIGHT = 200f
    private const val MAX_LIST_COLUMNS = 3

    // The tiles.
    const val TILES_PADDING = 6f
    private const val TILE = 60f
    private const val DENSE_TILE = 44f
    private const val TILE_WIDTH = 112f
    private const val DENSE_TILE_WIDTH = 136f
    private const val MAX_TILE_COLUMNS = 6

    // The single item.
    private const val SINGLE_MIN_HEIGHT = 140f
    private const val STACK_MIN_HEIGHT = 250f

    /** The heights that change when items carry the line with the price outside regular hours. */
    private class Heights(val extended: Boolean) {
        private val line = if (extended) EXTENDED_LINE else 0f
        private val tileLine = if (extended) EXTENDED_TILE_LINE else 0f
        val row = ROW + line
        val denseRow = DENSE_ROW + line
        val tile = TILE + tileLine
        val denseTile = DENSE_TILE + tileLine
        val singleMin = SINGLE_MIN_HEIGHT + tileLine

        /** Under this the list cannot hold two full lines. */
        val listMin = LIST_PADDING_V * 2 + row * 2
    }

    /**
     * @param extended some item has a price outside regular hours to show.
     * @param rowHeight height of the tallest line of the list when names get [nameWidth] of
     *   width, without the line for the price outside regular hours, for callers that can
     *   measure how names wrap. Defaults to one line of name.
     */
    fun plan(
        width: Float,
        height: Float,
        itemCount: Int,
        chartWanted: Boolean,
        extended: Boolean = false,
        rowHeight: (nameWidth: Float) -> Float = { ROW },
    ): MarketPlan {
        val heightsByWidth = HashMap<Int, Float>(8)
        val cachedRowHeight: (Float) -> Float = { nameWidth ->
            val key = (nameWidth * 10f).toInt()
            heightsByWidth.getOrPut(key) { rowHeight(nameWidth) }
        }
        if (extended && itemCount > 0) {
            val heights = Heights(extended = true)
            // Only where an item fits whole with its extra line; otherwise the line gives way.
            if (fit(width, height, itemCount, chartWanted, cachedRowHeight, heights, strict = true) != null) {
                return plan(width, height, itemCount, chartWanted, cachedRowHeight, heights)
            }
        }
        return plan(width, height, itemCount, chartWanted, cachedRowHeight, Heights(extended = false))
    }

    private fun plan(
        width: Float,
        height: Float,
        itemCount: Int,
        chartWanted: Boolean,
        rowHeight: (Float) -> Float,
        heights: Heights,
    ): MarketPlan {
        val base = fit(width, height, itemCount, chartWanted, rowHeight, heights, strict = false)!!
        // The single item sizes its own price, so it has nothing to gain here.
        if (itemCount == 0 || base.layout == MarketLayout.SINGLE) return base
        for (percent in SCALES) {
            val scale = percent / 100f
            // Larger only when nothing is lost by it: same shape, same items, same extras.
            if (fit(width / scale, height / scale, itemCount, chartWanted, rowHeight, heights, strict = true) == base) {
                return base.copy(scalePercent = percent)
            }
        }
        return base
    }

    /**
     * The plan for a space. The smallest spaces still get one item even when it does not fit
     * whole; with [strict] such a space gets no plan instead, which is how a larger text size
     * is turned down.
     */
    private fun fit(
        width: Float,
        height: Float,
        itemCount: Int,
        chartWanted: Boolean,
        rowHeight: (Float) -> Float,
        heights: Heights,
        strict: Boolean,
    ): MarketPlan? {
        if (itemCount <= 0) return MarketPlan(MarketLayout.LIST, 1, false, false, false, 0)
        val narrow = width < LIST_MIN_WIDTH
        val single = MarketPlan(MarketLayout.SINGLE, 1, false, false, false, 1, showExtended = heights.extended)
        return when {
            !narrow -> {
                // A row of tiles holds more than a list of one or two lines: keep it until the
                // list catches up, so a taller widget never shows fewer items. In a short strip
                // the list must also do it with full lines, name and all, as when one item has
                // the whole strip to itself.
                val short = height < heights.listMin
                val list = list(width, height, itemCount, chartWanted, rowHeight, heights, strict || short)
                val tiles = tiles(width, height, itemCount, maxRows = 1, heights, strict)
                val listWins = list != null && (tiles == null || list.shown >= tiles.shown) &&
                    !(short && list.dense)
                if (listWins) list else tiles
            }
            height < heights.singleMin -> tiles(width, height, itemCount, maxRows = 1, heights, strict)
            itemCount == 1 || height < STACK_MIN_HEIGHT -> single
            // Too narrow even for a tile: the single item makes do with any width.
            else -> tiles(width, height, itemCount, maxRows = Int.MAX_VALUE, heights, strict) ?: single
        }
    }

    private fun tiles(
        width: Float,
        height: Float,
        itemCount: Int,
        maxRows: Int,
        heights: Heights,
        strict: Boolean,
    ): MarketPlan? {
        val dense = height < TILES_PADDING * 2 + heights.tile
        val tileWidth = if (dense) DENSE_TILE_WIDTH else TILE_WIDTH
        val fitColumns = floor((width - TILES_PADDING * 2) / tileWidth).toInt()
        val fitRows = floor((height - TILES_PADDING * 2) / (if (dense) heights.denseTile else heights.tile)).toInt()
        if (strict && (fitColumns < 1 || fitRows < 1)) return null
        val columns = fitColumns.coerceIn(1, MAX_TILE_COLUMNS)
        val rows = fitRows.coerceIn(1, maxRows)
        val shown = min(itemCount, columns * rows)
        return MarketPlan(MarketLayout.TILES, min(columns, shown), dense, false, false, shown, showExtended = heights.extended)
    }

    private fun list(
        width: Float,
        height: Float,
        itemCount: Int,
        chartWanted: Boolean,
        rowHeight: (Float) -> Float,
        heights: Heights,
        strict: Boolean,
    ): MarketPlan? {
        val extra = heights.row - ROW
        fun inner(columns: Int) = (width - LIST_PADDING_H * 2 - COLUMN_GAP * (columns - 1)) / columns
        fun chart(columns: Int, dense: Boolean) =
            chartWanted && inner(columns) >= (if (dense) DENSE_CHART_MIN else CHART_MIN)

        fun lineHeight(columns: Int, dense: Boolean): Float {
            if (dense) return heights.denseRow
            val nameWidth = inner(columns) - PRICE_BLOCK - (if (chart(columns, false)) CHART_BLOCK else 0f)
            return rowHeight(nameWidth).coerceAtLeast(ROW) + extra
        }

        fun capacity(columns: Int, dense: Boolean, header: Boolean): Int {
            val room = height - LIST_PADDING_V * 2 - (if (header) HEADER else 0f)
            return columns * floor(room / lineHeight(columns, dense)).toInt().coerceAtLeast(0)
        }

        val maxColumns = (1..MAX_LIST_COLUMNS).last { it == 1 || inner(it) >= COLUMN_MIN }

        // The fewest columns that show the most items; `dense` only when it shows more.
        fun best(dense: Boolean): Pair<Int, Int> {
            var bestColumns = 1
            var bestShown = 0
            for (columns in 1..maxColumns) {
                if (dense && inner(columns) < DENSE_MIN) continue
                val shown = min(itemCount, capacity(columns, dense, false))
                if (shown > bestShown) {
                    bestShown = shown
                    bestColumns = columns
                }
            }
            return bestColumns to bestShown
        }

        val (normalColumns, normalShown) = best(dense = false)
        val (denseColumns, denseShown) = best(dense = true)
        val dense = denseShown > normalShown
        if (strict && maxOf(normalShown, denseShown) < 1) return null
        val columns = if (dense) denseColumns else normalColumns
        val shown = (if (dense) denseShown else normalShown).coerceAtLeast(1)
        val header = height >= HEADER_MIN_HEIGHT && min(itemCount, capacity(columns, dense, true)) >= shown

        return MarketPlan(
            layout = MarketLayout.LIST,
            columns = columns,
            dense = dense,
            showChart = chart(columns, dense),
            showHeader = header,
            shown = shown,
            showExtended = heights.extended,
        )
    }
}
