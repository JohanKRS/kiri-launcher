package io.github.johankrs.kiri.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the home screen follows a change in the number of rows or columns. */
class GridChangeTest {

    private val six = GridSize(columns = 5, rows = 6)
    private val clock = ItemContent.Widget("clock")

    private fun app(name: String) = ItemContent.App("pkg.$name/Main")

    /**
     * The usual page: a clock across the top two rows, a free row, then three rows of apps, the
     * last two full. Arranged on [six].
     */
    private fun usualPage(): HomeLayout {
        val items = mutableListOf(HomeItem(1, CellPosition(0, 0, 0), clock, CellSpan(5, 2)))
        var id = 2L
        for (column in 0..2) items += HomeItem(id++, CellPosition(0, column, 3), app("a$column"))
        for (row in 4..5) for (column in 0..4) items += HomeItem(id++, CellPosition(0, column, row), app("r${row}c$column"))
        return HomeLayout(items, grid = six)
    }

    private fun HomeLayout.at(content: ItemContent): CellPosition = items.single { it.content == content }.position

    private fun HomeLayout.isValidOn(grid: GridSize): Boolean =
        items.all { isFree(it.position, it.span, grid, ignoreId = it.id) }

    @Test
    fun `one more row goes where the page is free, and the bottom apps stay at the bottom`() {
        val seven = GridSize(5, 7)
        val shown = usualPage().fitTo(seven)

        assertEquals(CellPosition(0, 0, 0), shown.at(clock))
        assertEquals(CellPosition(0, 0, 4), shown.at(app("a0")))
        assertEquals(CellPosition(0, 4, 6), shown.at(app("r5c4")))
        assertEquals(seven, shown.grid)
        assertTrue(shown.isValidOn(seven))
    }

    @Test
    fun `one row less is taken from the free row, and nothing changes page`() {
        val five = GridSize(5, 5)
        val shown = usualPage().fitTo(five)

        assertEquals(CellPosition(0, 0, 0), shown.at(clock))
        assertEquals(CellPosition(0, 2, 2), shown.at(app("a2")))
        assertEquals(CellPosition(0, 0, 4), shown.at(app("r5c0")))
        assertEquals(1, shown.pageCount)
        assertTrue(shown.isValidOn(five))
    }

    @Test
    fun `going back to the rows it was arranged on puts everything back`() {
        val page = usualPage()
        // Shown on other rows and back, without a change in between.
        assertEquals(page.items.toSet(), page.fitTo(six).items.toSet())
        // Saved after a change on other rows, and then shown on six again.
        for (rows in listOf(5, 7, 9)) {
            val saved = page.fitTo(GridSize(5, rows))
            assertEquals(page.items.toSet(), saved.fitTo(six).items.toSet(), "from $rows rows")
        }
    }

    @Test
    fun `with no free row left the widget keeps its page and the apps nearest to it move first`() {
        val four = GridSize(5, 4)
        val shown = usualPage().fitTo(four)

        assertEquals(CellPosition(0, 0, 0), shown.at(clock))
        assertEquals(usualPage().items.size, shown.items.size)
        assertTrue(shown.isValidOn(four))
        // The free row went first; then the row of three, the lightest, so the two full rows
        // stay at the bottom and only those three move, to the next page.
        assertEquals(CellPosition(0, 0, 2), shown.at(app("r4c0")))
        assertEquals(CellPosition(0, 4, 3), shown.at(app("r5c4")))
        assertEquals(setOf(app("a0"), app("a1"), app("a2")), shown.items.filter { it.position.page == 1 }.map { it.content }.toSet())
        assertEquals(2, shown.pageCount)
    }

    @Test
    fun `a full page grows at the end, and between its widget and the apps under it`() {
        val apps = (0 until 6).flatMap { row -> (0 until 5).map { column -> HomeItem(row * 5L + column + 1, CellPosition(0, column, row), app("$row-$column")) } }
        val full = HomeLayout(apps, grid = six)
        val grown = full.fitTo(GridSize(5, 7))
        assertEquals(full.items.toSet(), grown.items.toSet())

        val withClock = HomeLayout(
            listOf(HomeItem(1, CellPosition(0, 0, 0), clock, CellSpan(5, 2))) +
                (2 until 6).flatMap { row -> (0 until 5).map { column -> HomeItem(row * 5L + column + 10, CellPosition(0, column, row), app("$row-$column")) } },
            grid = six,
        )
        val taller = withClock.fitTo(GridSize(5, 7))
        assertEquals(CellPosition(0, 0, 0), taller.at(clock))
        assertEquals(CellPosition(0, 0, 3), taller.at(app("2-0")))
        assertEquals(CellPosition(0, 4, 6), taller.at(app("5-4")))
    }

    @Test
    fun `fewer columns keep what fits and put the rest in the nearest free cell`() {
        val four = GridSize(4, 6)
        val shown = usualPage().fitTo(four)

        // The clock gets narrower but stays; the last column goes into the free row above.
        assertEquals(CellSpan(4, 2), shown.items.single { it.content == clock }.span)
        assertEquals(CellPosition(0, 0, 0), shown.at(clock))
        assertEquals(CellPosition(0, 0, 4), shown.at(app("r4c0")))
        assertEquals(CellPosition(0, 3, 3), shown.at(app("r4c4")))
        assertEquals(1, shown.pageCount)
        assertTrue(shown.isValidOn(four))
    }

    @Test
    fun `more columns add room on the right and move nothing`() {
        val page = usualPage()
        val shown = page.fitTo(GridSize(6, 6))
        assertEquals(page.items.map { it.position }.toSet(), shown.items.map { it.position }.toSet())
    }

    @Test
    fun `free columns between items are where columns come and go`() {
        val left = HomeItem(1, CellPosition(0, 0, 5), app("left"))
        val right = HomeItem(2, CellPosition(0, 4, 5), app("right"))
        val layout = HomeLayout(listOf(left, right), grid = six)

        assertEquals(CellPosition(0, 3, 5), layout.fitTo(GridSize(4, 6)).at(app("right")))
        assertEquals(CellPosition(0, 5, 5), layout.fitTo(GridSize(6, 6)).at(app("right")))
        assertEquals(CellPosition(0, 0, 5), layout.fitTo(GridSize(6, 6)).at(app("left")))
    }

    @Test
    fun `a layout from before the grid was kept reads as arranged on the grid it is shown on`() {
        val old = usualPage().copy(grid = null)
        assertEquals(old.items.toSet(), old.fitTo(six).items.toSet())
        // Items past the edge say the grid was larger: the free row goes, and the apps stay below.
        val shown = old.fitTo(GridSize(5, 5))
        assertEquals(CellPosition(0, 0, 4), shown.at(app("r5c0")))
    }

    @Test
    fun `every page keeps its own items and its empty pages`() {
        val second = HomeItem(99, CellPosition(2, 0, 5), app("second"))
        val layout = usualPage().copy(items = usualPage().items + second, pages = 3)
        val shown = layout.fitTo(GridSize(5, 5))

        assertEquals(3, shown.pageCount)
        assertEquals(CellPosition(2, 0, 4), shown.at(app("second")))
    }

    @Test
    fun `the grid a layout was arranged on is saved with it`() {
        val page = usualPage()
        val read = HomeLayoutCodec.decode(HomeLayoutCodec.encode(page))!!
        assertEquals(six, read.grid)
        assertEquals(page, read)
        assertEquals(null, HomeLayoutCodec.decode(HomeLayoutCodec.encode(page.copy(grid = null)))!!.grid)
    }

    @Test
    fun `moving the pages keeps the grid`() {
        val page = usualPage()
        assertEquals(six, page.addPage().grid)
        assertEquals(six, page.addPage().movePage(1, 0).grid)
        assertEquals(six, page.addPage().removePage(1).grid)
    }
}
