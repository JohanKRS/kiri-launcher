package io.github.johankrs.kiri.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeLayoutTest {
    private val grid = GridSize(columns = 3, rows = 2)

    private fun app(name: String) = ItemContent.App("pkg.$name/Main")

    private fun layoutOf(vararg names: String): HomeLayout =
        names.fold(HomeLayout()) { layout, name -> layout.add(app(name), grid) }

    private fun HomeLayout.positionOf(name: String): CellPosition =
        items.single { it.content == app(name) }.position

    private fun HomeLayout.idOf(name: String): Long = items.single { it.content == app(name) }.id

    @Test
    fun `apps fill a page left to right, top to bottom, then start a new page`() {
        val layout = layoutOf("a", "b", "c", "d", "e", "f", "g")

        assertEquals(CellPosition(0, 0, 0), layout.positionOf("a"))
        assertEquals(CellPosition(0, 2, 0), layout.positionOf("c"))
        assertEquals(CellPosition(0, 0, 1), layout.positionOf("d"))
        assertEquals(CellPosition(1, 0, 0), layout.positionOf("g"))
        assertEquals(2, layout.pageCount)
        assertEquals(layout.items.size, layout.items.map { it.id }.distinct().size)
    }

    @Test
    fun `an empty layout has one empty page and its first free cell is the first cell`() {
        assertEquals(1, HomeLayout().pageCount)
        assertEquals(CellPosition(0, 0, 0), HomeLayout().firstFree(CellSpan.Single, grid))
    }

    @Test
    fun `moving to a free cell just moves`() {
        val layout = layoutOf("a", "b")
        val moved = layout.move(layout.idOf("a"), CellPosition(0, 2, 1), grid)

        assertEquals(CellPosition(0, 2, 1), moved.positionOf("a"))
        assertEquals(CellPosition(0, 1, 0), moved.positionOf("b"))
    }

    @Test
    fun `moving onto another app swaps the two`() {
        val layout = layoutOf("a", "b")
        val moved = layout.move(layout.idOf("a"), layout.positionOf("b"), grid)

        assertEquals(CellPosition(0, 1, 0), moved.positionOf("a"))
        assertEquals(CellPosition(0, 0, 0), moved.positionOf("b"))
    }

    @Test
    fun `a move outside the page or onto a bigger item changes nothing`() {
        val base = layoutOf("a")
        val widget = HomeItem(99, CellPosition(0, 1, 0), app("widget"), CellSpan(columns = 2, rows = 2))
        val layout = base.copy(items = base.items + widget)

        assertSame(layout, layout.move(layout.idOf("a"), CellPosition(0, 3, 0), grid))
        assertSame(layout, layout.move(layout.idOf("a"), CellPosition(0, 1, 0), grid))
        assertSame(layout, layout.move(layout.idOf("a"), CellPosition(0, 2, 1), grid))
        assertSame(layout, layout.move(12345, CellPosition(0, 0, 1), grid))
    }

    @Test
    fun `an item with a span blocks every cell it covers`() {
        val widget = HomeItem(1, CellPosition(0, 0, 0), app("widget"), CellSpan(columns = 2, rows = 2))
        val layout = HomeLayout(listOf(widget))

        assertEquals(widget, layout.itemAt(CellPosition(0, 1, 1)))
        assertNull(layout.itemAt(CellPosition(0, 2, 0)))
        assertEquals(CellPosition(0, 2, 0), layout.firstFree(CellSpan.Single, grid))
        assertEquals(CellPosition(1, 0, 0), layout.firstFree(CellSpan(columns = 2, rows = 1), grid))
    }

    @Test
    fun `a span larger than the page is shrunk to the page`() {
        val layout = HomeLayout().add(app("huge"), grid, CellSpan(columns = 9, rows = 9))

        assertEquals(CellSpan(columns = 3, rows = 2), layout.items.single().span)
        assertEquals(CellPosition(0, 0, 0), layout.items.single().position)
    }

    @Test
    fun `a page left empty stays, until it is taken away`() {
        val layout = layoutOf("a", "b", "c", "d", "e", "f", "g")
        val secondPage = layout.items.filter { it.position.page == 1 }.map { it.id }
        val firstPage = layout.items.filter { it.position.page == 0 }.map { it.id }

        val lastEmptied = secondPage.fold(layout) { current, id -> current.remove(id) }
        assertEquals(2, lastEmptied.pageCount)

        val firstEmptied = firstPage.fold(layout) { current, id -> current.remove(id) }
        assertEquals(2, firstEmptied.pageCount)
        assertEquals(CellPosition(1, 0, 0), firstEmptied.positionOf("g"))
        // The empty page is the first place something new goes.
        assertEquals(CellPosition(0, 0, 0), firstEmptied.add(app("new"), grid).positionOf("new"))
    }

    @Test
    fun `moving the last item off a page keeps the page`() {
        val layout = layoutOf("a", "b", "c", "d", "e", "f", "g")
        val moved = layout.move(layout.idOf("g"), CellPosition(0, 0, 0), grid)

        // "a" was there, and swapped places with it.
        assertEquals(CellPosition(1, 0, 0), moved.positionOf("a"))
        val emptied = layout.remove(layout.idOf("a")).move(layout.idOf("g"), CellPosition(0, 0, 0), grid)
        assertEquals(CellPosition(0, 0, 0), emptied.positionOf("g"))
        assertEquals(2, emptied.pageCount)
    }

    @Test
    fun `shrinking the grid keeps what fits and relocates the rest without losing anything`() {
        val layout = layoutOf("a", "b", "c", "d", "e", "f")
        val narrow = GridSize(columns = 2, rows = 2)

        val fitted = layout.fitTo(narrow)

        assertEquals(6, fitted.items.size)
        assertEquals(CellPosition(0, 0, 0), fitted.positionOf("a"))
        assertEquals(CellPosition(0, 1, 0), fitted.positionOf("b"))
        assertEquals(CellPosition(0, 0, 1), fitted.positionOf("d"))
        assertEquals(CellPosition(0, 1, 1), fitted.positionOf("e"))
        assertEquals(CellPosition(1, 0, 0), fitted.positionOf("c"))
        assertEquals(CellPosition(1, 1, 0), fitted.positionOf("f"))
        assertTrue(fitted.items.all { fitted.isFree(it.position, it.span, narrow, ignoreId = it.id) })
    }

    @Test
    fun `fitting to the same grid changes nothing`() {
        val layout = layoutOf("a", "b", "c", "d")
        assertEquals(layout.items.toSet(), layout.fitTo(grid).items.toSet())
    }

    @Test
    fun `shortcuts to uninstalled apps disappear`() {
        val layout = layoutOf("a", "b", "c")
        val cleaned = layout.withoutMissingApps { it != "pkg.b/Main" }

        assertEquals(listOf(app("a"), app("c")), cleaned.items.map { it.content })
        assertSame(layout, layout.withoutMissingApps { true })
    }

    @Test
    fun `the same app is recognised as already placed`() {
        val layout = layoutOf("a")
        assertTrue(layout.contains(app("a")))
        assertEquals(false, layout.contains(app("b")))
    }

    @Test
    fun `adding from a later page never leaves an empty page behind`() {
        val layout = layoutOf("a").add(app("far"), grid, fromPage = 7)

        assertEquals(CellPosition(1, 0, 0), layout.positionOf("far"))
        assertEquals(CellPosition(0, 1, 0), layout.add(app("near"), grid).positionOf("near"))
    }

    @Test
    fun `removing by content takes the shortcut away`() {
        val layout = layoutOf("a", "b")

        assertEquals(listOf(app("b")), layout.without(app("a")).items.map { it.content })
        assertSame(layout, layout.without(app("zzz")))
    }

    @Test
    fun `what is shown leaves out missing apps and fits the grid`() {
        val layout = layoutOf("a", "b", "c", "d")
        val narrow = GridSize(columns = 2, rows = 2)

        val shown = layout.visibleOn(narrow) { it != "pkg.b/Main" }

        assertEquals(setOf(app("a"), app("c"), app("d")), shown.items.map { it.content }.toSet())
        assertTrue(shown.items.all { shown.isFree(it.position, it.span, narrow, ignoreId = it.id) })
        assertEquals(4, layout.visibleOn(narrow).items.size)
    }

    @Test
    fun `a layout survives being saved as text`() {
        val widget = HomeItem(40, CellPosition(1, 0, 0), app("widget"), CellSpan(columns = 2, rows = 2))
        val layout = layoutOf("a", "b").let { it.copy(items = it.items + widget) }

        assertEquals(layout, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout)))
        assertEquals(HomeLayout(), HomeLayoutCodec.decode(HomeLayoutCodec.encode(HomeLayout())))
    }

    @Test
    fun `text that is not a layout is refused and bad lines are skipped`() {
        assertNull(HomeLayoutCodec.decode(null))
        assertNull(HomeLayoutCodec.decode(""))
        assertNull(HomeLayoutCodec.decode("something else"))

        val good = "1\t0\t0\t0\t1\t1\tapp\tpkg.a/Main"
        val text = listOf(
            "kiri-home-1",
            good,
            "not a line",
            "2\t0\t-1\t0\t1\t1\tapp\tpkg.b/Main",
            "3\t0\t1\t0\t1\t1\tfuture\tsomething",
            "1\t0\t2\t0\t1\t1\tapp\tpkg.c/Main",
        ).joinToString("\n")

        assertEquals(listOf(HomeItem(1, CellPosition(0, 0, 0), app("a"))), HomeLayoutCodec.decode(text)?.items)
    }

    private val widget = ItemContent.Widget("kiri.market")

    @Test
    fun `a widget takes the first place where its whole size fits`() {
        val layout = layoutOf("a", "b").add(widget, grid, span = CellSpan(2, 1))

        // The first row only has one cell left, so the widget starts the second row.
        val placed = layout.items.single { it.content == widget }
        assertEquals(CellPosition(0, 0, 1), placed.position)
        assertEquals(CellSpan(2, 1), placed.span)
    }

    @Test
    fun `a widget that grows pushes the apps underneath to the nearest free places`() {
        val start = HomeLayout().add(widget, grid).let { it.add(app("a"), grid).add(app("b"), grid) }
        val id = start.items.single { it.content == widget }.id

        val grown = start.resize(id, CellSpan(2, 2), grid)

        val placed = grown.item(id)!!
        assertEquals(CellPosition(0, 0, 0), placed.position)
        assertEquals(CellSpan(2, 2), placed.span)
        // "a" was under the widget and takes the first free cell; "b" was not and stays.
        assertEquals(CellPosition(0, 2, 0), grown.positionOf("b"))
        assertEquals(CellPosition(0, 2, 1), grown.positionOf("a"))
        assertEquals(start.items.map { it.id }, grown.items.map { it.id })
    }

    @Test
    fun `a widget that grows on a full page sends what it covers to the next page`() {
        val start = layoutOf("a", "b", "c", "d", "e").add(widget, grid)
        val id = start.items.single { it.content == widget }.id
        assertEquals(CellPosition(0, 2, 1), start.item(id)!!.position)

        val grown = start.resize(id, CellSpan(2, 2), grid)

        // At the right edge it shifts back to stay on the page.
        assertEquals(CellPosition(0, 1, 0), grown.item(id)!!.position)
        assertEquals(CellPosition(0, 0, 0), grown.positionOf("a"))
        assertEquals(CellPosition(0, 0, 1), grown.positionOf("d"))
        assertEquals(listOf(1, 1, 1), listOf("b", "c", "e").map { grown.positionOf(it).page })
    }

    @Test
    fun `resizing never goes past the grid and the same size changes nothing`() {
        val start = HomeLayout().add(widget, grid, span = CellSpan(2, 1))
        val id = start.items.single().id

        assertEquals(CellSpan(3, 2), start.resize(id, CellSpan(9, 9), grid).item(id)!!.span)
        assertSame(start, start.resize(id, CellSpan(2, 1), grid))
        assertSame(start, start.resize(99, CellSpan(1, 1), grid))
        assertEquals(CellSpan(1, 1), start.resize(id, CellSpan(1, 1), grid).item(id)!!.span)
    }

    @Test
    fun `a widget survives being saved as text and is never treated as a missing app`() {
        val layout = layoutOf("a").add(widget, grid, span = CellSpan(2, 1))

        assertEquals(layout, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout)))
        assertTrue(layout.visibleOn(grid) { false }.contains(widget))
    }

    @Test
    fun `a widget dropped on apps lands there and the apps make room, but an app never does that`() {
        val start = HomeLayout().add(widget, grid, span = CellSpan(2, 1))
            .let { it.add(app("a"), grid).add(app("b"), grid).add(app("c"), grid) }
        val id = start.items.single { it.content == widget }.id
        assertEquals(CellPosition(0, 0, 1), start.positionOf("b"))

        val moved = start.move(id, CellPosition(0, 0, 1), grid)

        assertEquals(CellPosition(0, 0, 1), moved.item(id)!!.position)
        // "b" and "c" were underneath and take the cells the widget left; "a" stays.
        assertEquals(CellPosition(0, 2, 0), moved.positionOf("a"))
        assertEquals(CellPosition(0, 0, 0), moved.positionOf("b"))
        assertEquals(CellPosition(0, 1, 0), moved.positionOf("c"))

        // Off the page is refused, and a single app dropped on the widget stays where it was.
        assertSame(start, start.move(id, CellPosition(0, 2, 1), grid))
        assertSame(start, start.move(start.idOf("a"), CellPosition(0, 0, 0), grid))
    }

    @Test
    fun `an app dropped on a cell goes there, or to the nearest free place when it is taken`() {
        val start = layoutOf("a", "b")

        val free = start.place(app("c"), CellPosition(0, 2, 1), grid)
        assertEquals(CellPosition(0, 2, 1), free.positionOf("c"))

        val taken = start.place(app("c"), CellPosition(0, 0, 0), grid)
        assertEquals(CellPosition(0, 2, 0), taken.positionOf("c"))

        // A page far beyond the last one is the next new page, never a gap.
        val newPage = start.place(app("c"), CellPosition(7, 1, 1), grid)
        assertEquals(CellPosition(1, 1, 1), newPage.positionOf("c"))

        // Already on the home screen: it moves instead of appearing twice.
        val again = start.place(app("a"), CellPosition(0, 2, 1), grid)
        assertEquals(CellPosition(0, 2, 1), again.positionOf("a"))
        assertEquals(2, again.items.size)
    }

    @Test
    fun `an app widget survives being saved as text, with the number Android gave it`() {
        val clock = ItemContent.AppWidget(12, "com.clock/com.clock.widget.Digital")
        val other = ItemContent.AppWidget(13, "com.clock/com.clock.widget.Digital")
        val layout = layoutOf("a").add(clock, grid, span = CellSpan(2, 1)).add(other, grid, span = CellSpan(3, 1))

        val read = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals(layout, read)
        assertEquals(setOf(12, 13), read!!.appWidgetIds)
        assertEquals("com.clock", clock.packageName)
        // Two copies of the same widget are two things, and neither is a missing app.
        assertEquals(3, layout.visibleOn(grid) { it == "pkg.a/Main" }.items.size)
        assertEquals(setOf(13), layout.without(clock).appWidgetIds)
    }

    @Test
    fun `an app widget written badly is skipped`() {
        val text = listOf(
            "kiri-home-1",
            "1\t0\t0\t0\t2\t1\tappwidget\t7:com.clock/com.clock.Digital",
            "2\t0\t0\t1\t2\t1\tappwidget\tcom.clock/com.clock.Digital",
            "3\t0\t2\t1\t1\t1\tappwidget\t9:",
            "4\t0\t2\t0\t1\t1\tappwidget\tx:com.clock/com.clock.Digital",
        ).joinToString("\n")

        assertEquals(setOf(7), HomeLayoutCodec.decode(text)!!.appWidgetIds)
    }
}
