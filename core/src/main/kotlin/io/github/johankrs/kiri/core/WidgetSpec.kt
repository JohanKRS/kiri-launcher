package io.github.johankrs.kiri.core

import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.GridSize
import kotlin.math.ceil

/**
 * What the home grid needs to know about one of Kiri's own widgets, without any UI: who it is
 * and which sizes it accepts.
 */
data class WidgetSpec(
    /** Stable id, stored with the home layout. Never change it once released. */
    val id: String,
    val minSize: CellSpan,
    val maxSize: CellSpan,
    val defaultSize: CellSpan,
) {
    /** Returns [size] pulled back inside what this widget supports. */
    fun coerce(size: CellSpan): CellSpan = CellSpan(
        columns = size.columns.coerceIn(minSize.columns, maxSize.columns),
        rows = size.rows.coerceIn(minSize.rows, maxSize.rows),
    )
}

/**
 * How the sizes that apps give for their widgets, in dp, become cells of the home grid. A span
 * of n cells is n cells wide plus the n - 1 gaps between them.
 */
object WidgetCells {
    /** The fewest cells, each [cellDp] long with [gapDp] between them, that hold [sizeDp]. */
    fun holding(sizeDp: Float, cellDp: Float, gapDp: Float): Int {
        val pitch = cellDp + gapDp
        if (pitch <= 0f || sizeDp <= cellDp) return 1
        return ceil((sizeDp + gapDp) / pitch - 0.001f).toInt().coerceAtLeast(1)
    }

    /**
     * The span a widget starts with on [grid]: [targetCells] when its app says how many cells
     * it was made for, otherwise as many as hold [minWidthDp] by [minHeightDp].
     */
    fun startSpan(
        targetCells: CellSpan?,
        minWidthDp: Float,
        minHeightDp: Float,
        cellWidthDp: Float,
        cellHeightDp: Float,
        gapDp: Float,
        grid: GridSize,
        rowGapDp: Float = gapDp,
    ): CellSpan = (
        targetCells ?: CellSpan(
            columns = holding(minWidthDp, cellWidthDp, gapDp),
            rows = holding(minHeightDp, cellHeightDp, rowGapDp),
        )
        ).clampedTo(grid)

    /** One step of a size, kept between [min] and [max]. Null when that changes nothing. */
    fun stepped(from: CellSpan, columns: Int, rows: Int, min: CellSpan, max: CellSpan): CellSpan? {
        val next = CellSpan(
            columns = (from.columns + columns).coerceIn(min.columns.coerceAtMost(max.columns), max.columns),
            rows = (from.rows + rows).coerceIn(min.rows.coerceAtMost(max.rows), max.rows),
        )
        return next.takeIf { it != from }
    }
}
