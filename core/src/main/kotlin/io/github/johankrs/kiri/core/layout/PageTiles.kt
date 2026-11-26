package io.github.johankrs.kiri.core.layout

/** Where one small page goes on the screen of pages: its corner and its size, in pixels. */
data class TileBox(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * How the small pages are laid out where they are put in order: the largest that fit the
 * area all at once, without scrolling, as many in a row as gives them the most room, the last
 * row centred, as in the folders.
 */
object PageTiles {
    /** The most small pages side by side. */
    const val MAX_COLUMNS = 5

    /**
     * The places of [count] tiles inside an area [areaWidth] by [areaHeight] whose corner is at
     * [left], [top]. Each tile is as tall as [aspect] times its width and has [below] more
     * room under it (for its buttons); [gapX] and [gapY] go between them. None is wider than
     * [maxWidth]. Left over height is shared above and below, a little more of it below.
     */
    fun arrange(
        count: Int,
        left: Float,
        top: Float,
        areaWidth: Float,
        areaHeight: Float,
        aspect: Float,
        gapX: Float,
        gapY: Float,
        below: Float,
        maxWidth: Float,
    ): List<TileBox> {
        if (count <= 0 || areaWidth <= 0f || areaHeight <= 0f || aspect <= 0f) return emptyList()
        var columns = 1
        var width = 0f
        for (tried in 1..minOf(count, MAX_COLUMNS)) {
            val rows = (count + tried - 1) / tried
            val byWidth = (areaWidth - (tried - 1) * gapX) / tried
            val byHeight = ((areaHeight - (rows - 1) * gapY) / rows - below) / aspect
            val fits = minOf(byWidth, byHeight, maxWidth)
            // Half a pixel more is no reason for another row.
            if (fits > width + 0.5f) {
                width = fits
                columns = tried
            }
        }
        width = width.coerceAtLeast(1f)
        val height = width * aspect
        val rows = (count + columns - 1) / columns
        val total = rows * (height + below) + (rows - 1) * gapY
        val startY = top + ((areaHeight - total) / 2f).coerceAtLeast(0f) * TOP_SHARE
        return List(count) { index ->
            val row = index / columns
            val column = index % columns
            val inRow = if (row == rows - 1) count - row * columns else columns
            val rowWidth = inRow * width + (inRow - 1) * gapX
            val startX = left + (areaWidth - rowWidth) / 2f
            TileBox(
                x = startX + column * (width + gapX),
                y = startY + row * (height + below + gapY),
                width = width,
                height = height,
            )
        }
    }

    /** How much of the height left over goes above the tiles, the rest below. */
    private const val TOP_SHARE = 0.55f
}
