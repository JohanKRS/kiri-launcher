package io.github.johankrs.kiri.core.layout

import io.github.johankrs.kiri.core.look.FolderGround

/** How many columns and rows one page of the home screen has. */
data class GridSize(val columns: Int, val rows: Int) {
    init {
        require(columns >= 1 && rows >= 1) { "A grid needs at least one cell, got $columns x $rows" }
    }
}

/** How many cells an item occupies. Apps take one; widgets can take more. */
data class CellSpan(val columns: Int = 1, val rows: Int = 1) {
    init {
        require(columns >= 1 && rows >= 1) { "A span needs at least one cell, got $columns x $rows" }
    }

    fun clampedTo(grid: GridSize): CellSpan =
        CellSpan(columns.coerceAtMost(grid.columns), rows.coerceAtMost(grid.rows))

    companion object {
        val Single = CellSpan()
    }
}

/** The top-left cell of an item: page, column and row, all counted from zero. */
data class CellPosition(val page: Int, val column: Int, val row: Int)

/** What sits in a cell. New kinds of item (folders, widgets) are added here. */
sealed interface ItemContent {
    /** A shortcut to an app, identified by `AppEntry.key`. */
    data class App(val appKey: String) : ItemContent

    /** One of Kiri's own widgets, identified by `WidgetSpec.id`. */
    data class Widget(val widgetId: String) : ItemContent

    /**
     * A folder of apps, by `AppEntry.key`, in the order they are shown. [id] stays the same
     * through renames and in both orientations. [ground] is what this folder alone sits on
     * when closed; null follows all folders.
     */
    data class Folder(
        val id: String,
        val name: String,
        val appKeys: List<String>,
        val ground: FolderGround? = null,
    ) : ItemContent

    /**
     * A widget that an installed app offers, shown by Android through Kiri. [appWidgetId] is the
     * number Android gave this one copy of it, and [provider] names who draws it, written as
     * `package/class`, so the copy can still be named after its app is gone.
     */
    data class AppWidget(val appWidgetId: Int, val provider: String) : ItemContent {
        /** The package of the app the widget belongs to. */
        val packageName: String get() = provider.substringBefore('/')
    }
}

data class HomeItem(
    val id: Long,
    val position: CellPosition,
    val content: ItemContent,
    val span: CellSpan = CellSpan.Single,
) {
    fun overlaps(position: CellPosition, span: CellSpan): Boolean =
        this.position.page == position.page &&
            this.position.column < position.column + span.columns &&
            position.column < this.position.column + this.span.columns &&
            this.position.row < position.row + span.rows &&
            position.row < this.position.row + this.span.rows

    fun covers(cell: CellPosition): Boolean = overlaps(cell, CellSpan.Single)
}

/**
 * Everything placed on the home screen. Immutable: each change returns a new layout, which keeps
 * the rules easy to test and makes undo cheap later.
 *
 * The pages belong to the person: one stays, empty, until it is taken away (see `removePage`),
 * also after the last item on it went elsewhere. [pages] is how many there are at least; items
 * placed beyond them add pages of their own. [mainPage] is where Home goes back to.
 */
data class HomeLayout(
    val items: List<HomeItem> = emptyList(),
    val pages: Int = 0,
    val mainPage: Int = 0,
    /**
     * The grid the items were arranged on, when known. Shown on another grid, rows and columns
     * come and go where the pages are free (see [fitTo]); null for a layout from before this
     * was kept, taken as arranged on the grid it is shown on.
     */
    val grid: GridSize? = null,
) {

    /** Number of pages, empty ones included. There is always at least one. */
    val pageCount: Int get() = maxOf(1, pages, (items.maxOfOrNull { it.position.page } ?: -1) + 1)

    // Two layouts are the same when they show the same: [pages] may be written down or left
    // to the items, and a main page past the end is the last one. The grid they were arranged
    // on is not what they show.
    override fun equals(other: Any?): Boolean =
        other is HomeLayout && items == other.items && pageCount == other.pageCount && shownMain == other.shownMain

    override fun hashCode(): Int = (items.hashCode() * 31 + pageCount) * 31 + shownMain

    private val shownMain: Int get() = mainPage.coerceIn(0, pageCount - 1)

    fun itemAt(cell: CellPosition): HomeItem? = items.firstOrNull { it.covers(cell) }

    fun item(id: Long): HomeItem? = items.firstOrNull { it.id == id }

    /** The numbers Android gave the app widgets placed here. */
    val appWidgetIds: Set<Int>
        get() = items.mapNotNullTo(HashSet()) { (it.content as? ItemContent.AppWidget)?.appWidgetId }

    /** True when an item of [span] at [position] is inside the page and touches nothing else. */
    fun isFree(position: CellPosition, span: CellSpan, grid: GridSize, ignoreId: Long? = null): Boolean {
        val inside = position.page >= 0 && position.column >= 0 && position.row >= 0 &&
            position.column + span.columns <= grid.columns &&
            position.row + span.rows <= grid.rows
        return inside && items.none { it.id != ignoreId && it.overlaps(position, span) }
    }

    /**
     * The first place where [span] fits, reading each page left to right, top to bottom, starting
     * at [fromPage]. When every existing page is full, the answer is on a new page.
     */
    fun firstFree(span: CellSpan, grid: GridSize, fromPage: Int = 0): CellPosition {
        val fitted = span.clampedTo(grid)
        var page = fromPage.coerceAtLeast(0)
        while (true) {
            for (row in 0..grid.rows - fitted.rows) {
                for (column in 0..grid.columns - fitted.columns) {
                    val candidate = CellPosition(page, column, row)
                    if (isFree(candidate, fitted, grid)) return candidate
                }
            }
            page++
        }
    }

    /**
     * Adds [content] in the first free place, looking from [fromPage] onwards. A page beyond the
     * last one counts as "a new page", so no empty page is ever left in between.
     */
    fun add(
        content: ItemContent,
        grid: GridSize,
        span: CellSpan = CellSpan.Single,
        fromPage: Int = 0,
    ): HomeLayout {
        val fitted = span.clampedTo(grid)
        val place = firstFree(fitted, grid, fromPage.coerceIn(0, pageCount))
        return copy(items = items + HomeItem(nextId(), place, content, fitted))
    }

    /**
     * Puts [content] at the cell [at], as when it is dropped there. Content already on the home
     * screen moves there instead of appearing twice. When the cell is taken, the nearest free
     * place from that page onwards is used, so a drop is never lost.
     */
    fun place(content: ItemContent, at: CellPosition, grid: GridSize): HomeLayout {
        items.firstOrNull { it.content == content }?.let { existing ->
            return move(existing.id, at, grid)
        }
        // An app inside a folder comes out of it to be put here.
        if (content is ItemContent.App && containsApp(content.appKey)) {
            return withAppTakenOut(content.appKey).place(content, at, grid)
        }
        val cell = at.copy(page = at.page.coerceIn(0, pageCount))
        return if (isFree(cell, CellSpan.Single, grid)) {
            copy(items = items + HomeItem(nextId(), cell, content))
        } else {
            add(content, grid, fromPage = cell.page)
        }
    }

    /** True when [content] is on the home screen; an app also when it is inside a folder. */
    fun contains(content: ItemContent): Boolean =
        if (content is ItemContent.App) containsApp(content.appKey) else items.any { it.content == content }

    /** Removes every item showing [content]. An app also leaves the folders it is in. */
    fun without(content: ItemContent): HomeLayout {
        if (content is ItemContent.App) return withAppTakenOut(content.appKey)
        val kept = items.filter { it.content != content }
        return if (kept.size == items.size) this else copy(items = kept).keepingPagesOf(this)
    }

    /**
     * The layout as it should be shown on [grid]: shortcuts to apps that are gone are left out
     * and everything is made to fit. Pass no [isInstalled] while the app list is still unknown.
     */
    fun visibleOn(grid: GridSize, isInstalled: ((appKey: String) -> Boolean)? = null): HomeLayout =
        (if (isInstalled != null) withoutMissingApps(isInstalled) else this).fitTo(grid)

    /**
     * Moves an item to [to]. A free destination is taken, and a destination holding exactly one
     * item of the same size swaps with it. Otherwise an item larger than one cell (a widget)
     * still lands there and what it covers makes room, while a single app stays where it was.
     */
    fun move(id: Long, to: CellPosition, grid: GridSize): HomeLayout {
        val moving = item(id) ?: return this
        if (moving.position == to) return this

        if (isFree(to, moving.span, grid, ignoreId = id)) {
            return replace(moving.copy(position = to)).keepingPagesOf(this)
        }

        val inTheWay = items.filter { it.id != id && it.overlaps(to, moving.span) }
        val other = inTheWay.singleOrNull()
        if (other != null && other.span == moving.span && other.position == to) {
            return replace(moving.copy(position = to))
                .replace(other.copy(position = moving.position))
        }

        val inside = to.page >= 0 && to.column >= 0 && to.row >= 0 &&
            to.column + moving.span.columns <= grid.columns &&
            to.row + moving.span.rows <= grid.rows
        if (moving.span == CellSpan.Single || !inside) return this
        return makingRoomFor(moving.copy(position = to), grid)
    }

    /**
     * Gives an item a new size. Its top-left cell stays where it is, unless the item would then
     * run past the right or bottom edge: there it shifts back just enough to stay on the page.
     * Items that end up underneath make room: each moves to the nearest free place at or after
     * its own page, so nothing is ever covered or lost.
     */
    fun resize(id: Long, span: CellSpan, grid: GridSize): HomeLayout {
        val item = item(id) ?: return this
        val fitted = span.clampedTo(grid)
        val position = item.position.copy(
            column = item.position.column.coerceAtMost(grid.columns - fitted.columns),
            row = item.position.row.coerceAtMost(grid.rows - fitted.rows),
        )
        if (fitted == item.span && position == item.position) return this

        return makingRoomFor(item.copy(position = position, span = fitted), grid)
    }

    /**
     * Puts [placed], an item of this layout with a new place or size, where it asks to be. The
     * items it then covers each move to the nearest free place at or after their own page.
     */
    private fun makingRoomFor(placed: HomeItem, grid: GridSize): HomeLayout {
        var result = HomeLayout(listOf(placed))
        val displaced = mutableListOf<HomeItem>()
        for (other in items.filter { it.id != placed.id }.sortedWith(readingOrder)) {
            if (result.isFree(other.position, other.span, grid)) {
                result = result.copy(items = result.items + other)
            } else {
                displaced += other
            }
        }
        for (other in displaced) {
            val place = result.firstFree(other.span, grid, fromPage = other.position.page)
            result = result.copy(items = result.items + other.copy(position = place))
        }
        // Same order as before, so nothing changes but sizes and places.
        val byId = result.items.associateBy { it.id }
        return copy(items = items.map { byId.getValue(it.id) }).keepingPagesOf(this)
    }

    /** Takes the item [id] away. Its page stays, even when nothing else is left on it. */
    fun remove(id: Long): HomeLayout = copy(items = items.filter { it.id != id }).keepingPagesOf(this)

    /**
     * Drops shortcuts to apps that are no longer installed. Folders lose them too, and a folder
     * left with a single app becomes that app.
     */
    fun withoutMissingApps(isInstalled: (appKey: String) -> Boolean): HomeLayout {
        var changed = false
        val kept = items.mapNotNull { item ->
            when (val content = item.content) {
                is ItemContent.App -> item.takeIf { isInstalled(content.appKey) }.also { if (it == null) changed = true }
                is ItemContent.Widget, is ItemContent.AppWidget -> item
                is ItemContent.Folder -> {
                    val apps = content.appKeys.filter(isInstalled)
                    if (apps.size == content.appKeys.size) {
                        item
                    } else {
                        changed = true
                        val standing = items.mapNotNullTo(HashSet()) { other ->
                            (other.content as? ItemContent.App)?.appKey?.takeIf(isInstalled)
                        }
                        item.withFolderApps(content, apps, standing)
                    }
                }
            }
        }
        return if (!changed) this else copy(items = kept).keepingPagesOf(this)
    }

    /**
     * The layout as it goes on [grid], for example after a row was taken away. Rows and columns
     * come and go where a page is free, in its widest free stretch, so what was at the bottom
     * stays at the bottom and what was on the right stays on the right. Only when a page has no
     * free row (or column) left does anything have to move: larger items choose first, and the
     * rest go to the nearest free place on the same page, or, when the page is full, to the
     * first free place on the pages after it. Nothing is ever lost and every page stays.
     *
     * The answer remembers [grid], so it can go back the same way to the grid it came from.
     */
    fun fitTo(grid: GridSize): HomeLayout {
        val from = arrangedFor(grid)
        var result = HomeLayout()
        val later = mutableListOf<HomeItem>()
        for ((_, onPage) in items.groupBy { it.position.page }.toSortedMap()) {
            val rows = onPage.shifted(from.rows, grid.rows, alongRows = true)
            val columns = rows.kept.shifted(from.columns, grid.columns, alongRows = false)
            val wanted = columns.kept.map { it.clampedInto(grid) }.sortedWith(placingOrder)
            val crowded = mutableListOf<HomeItem>()
            for (item in wanted) {
                if (result.isFree(item.position, item.span, grid)) result = result.with(item) else crowded += item
            }
            crowded += (rows.out + columns.out).map { it.clampedInto(grid) }.sortedWith(placingOrder)
            for (item in crowded) {
                val place = result.nearestFree(item, grid)
                if (place != null) result = result.with(item.copy(position = place)) else later += item
            }
        }
        for (item in later) {
            result = result.with(item.copy(position = result.firstFree(item.span, grid, fromPage = item.position.page + 1)))
        }
        // Same order as before, so nothing changes but places and sizes.
        val byId = result.items.associateBy { it.id }
        return copy(items = items.map { byId.getValue(it.id) }, grid = grid).keepingPagesOf(this)
    }

    /**
     * The grid these items were arranged on: the one written down, or else [shownOn], made
     * larger wherever an item reaches past it.
     */
    private fun arrangedFor(shownOn: GridSize): GridSize {
        val known = grid ?: shownOn
        val columns = items.maxOfOrNull { it.position.column + it.span.columns } ?: 0
        val rows = items.maxOfOrNull { it.position.row + it.span.rows } ?: 0
        return GridSize(maxOf(known.columns, columns), maxOf(known.rows, rows))
    }

    /** The free place for [item] nearest to where it is, on its own page; null when none is. */
    private fun nearestFree(item: HomeItem, grid: GridSize): CellPosition? {
        var best: CellPosition? = null
        var bestDistance = Int.MAX_VALUE
        for (row in 0..grid.rows - item.span.rows) {
            for (column in 0..grid.columns - item.span.columns) {
                val candidate = CellPosition(item.position.page, column, row)
                if (!isFree(candidate, item.span, grid)) continue
                val dx = column - item.position.column
                val dy = row - item.position.row
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = candidate
                }
            }
        }
        return best
    }

    private fun with(item: HomeItem): HomeLayout = copy(items = items + item)

    /**
     * This layout, made from [before], still with every page [before] had, empty ones too, and
     * with its main page. Every change to the items ends with it, so no page is ever lost.
     */
    internal fun keepingPagesOf(before: HomeLayout): HomeLayout {
        val count = maxOf(pageCount, before.pageCount)
        return if (pages == count && mainPage == before.mainPage) this else copy(pages = count, mainPage = before.mainPage)
    }

    private fun replace(item: HomeItem): HomeLayout =
        copy(items = items.map { if (it.id == item.id) item else it })

    private fun nextId(): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L

    private companion object {
        val readingOrder: Comparator<HomeItem> =
            compareBy({ it.position.page }, { it.position.row }, { it.position.column })

        /** Larger items choose their place first, so an app never pushes a widget away. */
        val placingOrder: Comparator<HomeItem> =
            compareByDescending<HomeItem> { it.span.columns * it.span.rows }.then(readingOrder)
    }
}

/** Items of a page on a new number of rows or columns: those that kept their line, and [out]. */
private class Shifted(val kept: List<HomeItem>, val out: List<HomeItem>)

/**
 * These items, all on one page arranged with [from] rows (or columns, when not [alongRows]),
 * as they go with [to]. A row is added to, or taken from, the widest stretch of free rows: the
 * items after it move along with it and nothing else changes. With no free row left, the row
 * with the fewest items that no widget reaches across goes, the upper one of two alike (of
 * columns, the one further right), and its items are [Shifted.out], to be found another place.
 */
private fun List<HomeItem>.shifted(from: Int, to: Int, alongRows: Boolean): Shifted {
    if (from == to || isEmpty()) return Shifted(this, emptyList())
    fun startOf(item: HomeItem) = if (alongRows) item.position.row else item.position.column
    fun HomeItem.by(step: Int) = copy(
        position = if (alongRows) position.copy(row = position.row + step) else position.copy(column = position.column + step),
    )
    if (to > from) {
        val band = widestFreeBand(from, alongRows)
        // A page with no free row grows between its widgets and the apps under them, so the
        // apps at the bottom stay there; without widgets above them, it grows at the end.
        val after = band?.last ?: (if (alongRows) lastWidgetRow() else -1).takeIf { it >= 0 } ?: (from - 1)
        return Shifted(map { if (startOf(it) > after) it.by(to - from) else it }, emptyList())
    }
    var kept = this
    val out = mutableListOf<HomeItem>()
    var count = from
    repeat(from - to) {
        val gone = kept.widestFreeBand(count, alongRows)?.last
            ?: kept.lightestLine(count, alongRows)?.also { line ->
                val leaving = kept.filter { startOf(it) == line }
                out += leaving
                kept = kept - leaving.toSet()
            }
            ?: return Shifted(kept, out)
        kept = kept.map { if (startOf(it) > gone) it.by(-1) else it }
        count--
    }
    return Shifted(kept, out)
}

/**
 * The row (or column, when not [alongRows]) below [count] with the fewest items, among those
 * no item larger than one line reaches across; null when every one is.
 */
private fun List<HomeItem>.lightestLine(count: Int, alongRows: Boolean): Int? {
    val crossed = BooleanArray(count)
    val load = IntArray(count)
    for (item in this) {
        val start = if (alongRows) item.position.row else item.position.column
        val length = if (alongRows) item.span.rows else item.span.columns
        if (length > 1) {
            for (index in start until (start + length).coerceAtMost(count)) crossed[index] = true
        } else if (start < count) {
            load[start]++
        }
    }
    val lines = (0 until count).filter { !crossed[it] }
    if (lines.isEmpty()) return null
    return lines.minWith(compareBy<Int> { load[it] }.thenBy { if (alongRows) it else -it })
}

/** The last row an item larger than one cell covers, or -1 when there is none. */
private fun List<HomeItem>.lastWidgetRow(): Int =
    filter { it.span != CellSpan.Single }.maxOfOrNull { it.position.row + it.span.rows - 1 } ?: -1

/**
 * The widest stretch of rows (or columns) below [count] that no item covers. Of two as wide,
 * the later one, lower on the page (or further right), which moves fewer items.
 */
private fun List<HomeItem>.widestFreeBand(count: Int, alongRows: Boolean): IntRange? {
    val used = BooleanArray(count)
    for (item in this) {
        val start = if (alongRows) item.position.row else item.position.column
        val length = if (alongRows) item.span.rows else item.span.columns
        for (index in start until (start + length).coerceAtMost(count)) used[index] = true
    }
    var best: IntRange? = null
    var index = 0
    while (index < count) {
        if (used[index]) {
            index++
            continue
        }
        val first = index
        while (index < count && !used[index]) index++
        val band = first until index
        if (best == null || band.count() >= best.count()) best = band
    }
    return best
}

/** [this] made to fit [grid]: no larger than it, and pulled back from past its edges. */
private fun HomeItem.clampedInto(grid: GridSize): HomeItem {
    val fitted = span.clampedTo(grid)
    val place = position.copy(
        column = position.column.coerceAtMost(grid.columns - fitted.columns),
        row = position.row.coerceAtMost(grid.rows - fitted.rows),
    )
    return if (fitted == span && place == position) this else copy(position = place, span = fitted)
}
