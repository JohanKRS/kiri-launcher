package io.github.johankrs.kiri.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PagesTest {
    private val grid = GridSize(columns = 2, rows = 1)

    private fun app(name: String) = ItemContent.App("pkg.$name/Main")

    /** Two apps a page: "a b | c d | e". */
    private fun layoutOf(vararg names: String): HomeLayout =
        names.fold(HomeLayout()) { layout, name -> layout.add(app(name), grid) }

    private fun HomeLayout.pageOf(name: String): Int = items.single { it.content == app(name) }.position.page

    private fun HomeLayout.names(page: Int): List<String> =
        itemsOn(page).map { (it.content as ItemContent.App).appKey.removePrefix("pkg.").removeSuffix("/Main") }

    @Test
    fun `a new page is empty and goes where it is asked, the others moving along`() {
        val layout = layoutOf("a", "b", "c", "d", "e")

        val atEnd = layout.addPage()
        assertEquals(4, atEnd.pageCount)
        assertTrue(atEnd.isEmptyPage(3))

        val inFront = layout.addPage(at = 0)
        assertEquals(4, inFront.pageCount)
        assertTrue(inFront.isEmptyPage(0))
        assertEquals(listOf("a", "b"), inFront.names(1))
        assertEquals(listOf("e"), inFront.names(3))
    }

    @Test
    fun `an empty home screen has one page and gets a second`() {
        val added = HomeLayout().addPage()

        assertEquals(2, added.pageCount)
        assertEquals(added, HomeLayoutCodec.decode(HomeLayoutCodec.encode(added)))
    }

    @Test
    fun `no page is added past the most there can be`() {
        val full = (1 until MAX_PAGES).fold(HomeLayout()) { layout, _ -> layout.addPage() }

        assertEquals(MAX_PAGES, full.pageCount)
        assertFalse(full.canAddPage)
        assertSame(full, full.addPage())
    }

    @Test
    fun `taking a page away takes what is on it, and the later pages move back`() {
        val layout = layoutOf("a", "b", "c", "d", "e")
        val removed = layout.removePage(1)

        assertEquals(2, removed.pageCount)
        assertEquals(listOf("a", "b"), removed.names(0))
        assertEquals(listOf("e"), removed.names(1))
        assertFalse(removed.contains(app("c")))
    }

    @Test
    fun `the only page stays, and a page that is not there changes nothing`() {
        val layout = layoutOf("a")

        assertSame(layout, layout.removePage(0))
        val two = layoutOf("a", "b", "c")
        assertSame(two, two.removePage(-1))
        assertSame(two, two.removePage(2))
    }

    @Test
    fun `an empty page in the middle can be taken away`() {
        val layout = layoutOf("a", "b", "c").addPage(at = 1)
        assertTrue(layout.isEmptyPage(1))

        val removed = layout.removePage(1)
        assertEquals(2, removed.pageCount)
        assertEquals(1, removed.pageOf("c"))
    }

    @Test
    fun `a page moves with all it holds and the pages in between make room`() {
        val layout = layoutOf("a", "b", "c", "d", "e")

        val toEnd = layout.movePage(from = 0, to = 2)
        assertEquals(listOf("c", "d"), toEnd.names(0))
        assertEquals(listOf("e"), toEnd.names(1))
        assertEquals(listOf("a", "b"), toEnd.names(2))

        val toFront = layout.movePage(from = 2, to = 0)
        assertEquals(listOf("e"), toFront.names(0))
        assertEquals(listOf("a", "b"), toFront.names(1))
        assertEquals(listOf("c", "d"), toFront.names(2))

        assertSame(layout, layout.movePage(from = 1, to = 1))
        assertSame(layout, layout.movePage(from = 7, to = 0))
    }

    @Test
    fun `an empty page can be moved too`() {
        val layout = layoutOf("a", "b", "c").addPage()
        val moved = layout.movePage(from = 2, to = 0)

        assertTrue(moved.isEmptyPage(0))
        assertEquals(3, moved.pageCount)
        assertEquals(1, moved.pageOf("a"))
    }

    @Test
    fun `the main page is the same page wherever it goes`() {
        val layout = layoutOf("a", "b", "c", "d", "e").withMainPage(1)
        assertEquals(1, layout.startPage)

        assertEquals(0, layout.movePage(from = 1, to = 0).startPage)
        // The first page goes to the end: the main one moves to the front.
        assertEquals(0, layout.movePage(from = 0, to = 2).startPage)
        assertEquals(2, layout.movePage(from = 2, to = 0).startPage)
        assertEquals(2, layout.addPage(at = 0).startPage)
        assertEquals(1, layout.addPage().startPage)
        assertEquals(0, layout.removePage(0).startPage)
        assertEquals(1, layout.removePage(2).startPage)
    }

    @Test
    fun `when the main page is taken away the page that takes its place is the main one`() {
        val layout = layoutOf("a", "b", "c", "d", "e")

        assertEquals(1, layout.withMainPage(1).removePage(1).startPage)
        assertEquals(1, layout.withMainPage(2).removePage(2).startPage)
    }

    @Test
    fun `the main page outlives items coming and going`() {
        val layout = layoutOf("a", "b", "c", "d", "e").withMainPage(2)
        val id = layout.items.single { it.content == app("e") }.id

        val emptied = layout.remove(id)
        assertEquals(3, emptied.pageCount)
        assertEquals(2, emptied.startPage)
        assertEquals(2, emptied.fitTo(grid).startPage)
        assertEquals(2, emptied.visibleOn(grid) { true }.startPage)
    }

    @Test
    fun `pages and the main page survive being saved as text`() {
        val layout = layoutOf("a", "b", "c").addPage().addPage(at = 0).withMainPage(3)
        val read = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))!!

        assertEquals(layout, read)
        assertEquals(4, read.pageCount)
        assertEquals(3, read.startPage)
        assertTrue(read.isEmptyPage(0))
    }

    @Test
    fun `a layout saved before pages were written down has the pages its items use`() {
        val text = listOf(
            "kiri-home-1",
            "1\t0\t0\t0\t1\t1\tapp\tpkg.a/Main",
            "2\t1\t0\t0\t1\t1\tapp\tpkg.b/Main",
        ).joinToString("\n")
        val read = HomeLayoutCodec.decode(text)!!

        assertEquals(2, read.pageCount)
        assertEquals(0, read.startPage)
    }

    @Test
    fun `a main page past the end is read as the last page`() {
        val text = listOf("kiri-home-1", "pages\t2\t9", "1\t0\t0\t0\t1\t1\tapp\tpkg.a/Main").joinToString("\n")

        assertEquals(1, HomeLayoutCodec.decode(text)!!.startPage)
    }
}

class PageTilesTest {
    private fun arrange(count: Int) = PageTiles.arrange(
        count = count,
        left = 16f,
        top = 118f,
        areaWidth = 328f,
        areaHeight = 612f,
        aspect = 760f / 360f,
        gapX = 12f,
        gapY = 12f,
        below = 34f,
        maxWidth = 360f * 0.46f,
    )

    @Test
    fun `few pages are large and side by side`() {
        val tiles = arrange(2)

        assertEquals(2, tiles.size)
        assertEquals(tiles[0].y, tiles[1].y)
        assertTrue(tiles[0].width > 150f)
    }

    @Test
    fun `more pages take more columns so that all fit without scrolling`() {
        for (count in 1..13) {
            val tiles = arrange(count)
            assertEquals(count, tiles.size)
            for (tile in tiles) {
                assertTrue(tile.x >= 16f - 0.01f && tile.x + tile.width <= 344f + 0.01f, "count $count: $tile")
                assertTrue(tile.y >= 118f - 0.01f && tile.y + tile.height + 34f <= 730f + 0.01f, "count $count: $tile")
            }
            // Never two on top of each other.
            for (a in tiles.indices) for (b in a + 1 until tiles.size) {
                val one = tiles[a]
                val other = tiles[b]
                val apart = one.x + one.width <= other.x + 0.01f || other.x + other.width <= one.x + 0.01f ||
                    one.y + one.height <= other.y + 0.01f || other.y + other.height <= one.y + 0.01f
                assertTrue(apart, "count $count: $one and $other")
            }
        }
    }

    @Test
    fun `the last row is centred`() {
        val tiles = arrange(3)
        val last = tiles.last()
        val firstRow = tiles.filter { it.y == tiles[0].y }

        assertTrue(last.y > tiles[0].y)
        val rowMiddle = (firstRow.first().x + firstRow.last().x + firstRow.last().width) / 2f
        assertEquals(rowMiddle, last.x + last.width / 2f, 0.01f)
    }

    @Test
    fun `nothing to place gives nothing`() {
        assertEquals(emptyList(), arrange(0))
    }
}
