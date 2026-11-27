package io.github.johankrs.kiri.core.tv

import io.github.johankrs.kiri.core.look.GlassLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvTest {
    private val hdmi = TvItem.Input("com.tv/.Hdmi/HW2")
    private val app = TvItem.App("pkg.a/Main")
    private val other = TvItem.App("pkg.b/Main")

    @Test
    fun `items keep their kind and identity when saved`() {
        val home = TvHomeItems(listOf(hdmi, app, other))
        assertEquals(home, TvHomeItems.decode(TvHomeItems.encode(home)))
        assertNull(TvHomeItems.decode(null))
        assertEquals(TvHomeItems.EMPTY, TvHomeItems.decode(""))
        // Something unknown or twice is dropped.
        assertEquals(TvHomeItems(listOf(app)), TvHomeItems.decode("app:pkg.a/Main\nwidget:3\napp:pkg.a/Main\napp:"))
    }

    @Test
    fun `adding, taking out and moving`() {
        val home = TvHomeItems(listOf(hdmi, app))
        assertEquals(listOf(hdmi, app, other), home.with(other).items)
        assertEquals(home, home.with(app))
        assertEquals(listOf(hdmi), home.without(app).items)
        val three = TvHomeItems(listOf(hdmi, app, other))
        assertEquals(listOf(app, other, hdmi), three.moved(hdmi, 2).items)
        assertEquals(listOf(other, hdmi, app), three.moved(other, -4).items)
        assertEquals(three, three.moved(TvItem.App("gone"), 0))
    }

    @Test
    fun `what is gone goes, and nothing goes while nothing is known`() {
        val home = TvHomeItems(listOf(hdmi, app, other))
        assertEquals(home, home.present(null, null))
        val kept = home.present(isApp = { it == "pkg.a/Main" }, isInput = { false })
        assertEquals(listOf(app), kept.items)
    }

    @Test
    fun `the first home screen has the inputs, then at most twelve apps`() {
        val first = TvHomeItems.firstRun(listOf("in1"), List(20) { "pkg.$it/Main" })
        assertEquals(1 + TvHomeItems.FIRST_APPS, first.items.size)
        assertEquals(TvItem.Input("in1"), first.items.first())
    }

    @Test
    fun `banners are saved and read back`() {
        val banners = listOf(
            TvBanner.Own,
            TvBanner.Kiri,
            TvBanner.Drawn(TvGlyph.CONTROLLER, TvColor.ROXO),
            TvBanner.Console("ps5", ConsoleStyle.RETRO),
        )
        for (banner in banners) assertEquals(banner, TvBanner.fromStored(banner.stored))
        assertNull(TvBanner.fromStored("drawn:nothing:azul"))
        assertNull(TvBanner.fromStored("console:nothing:noite"))
        assertEquals(TvBanner.Console("sw2", ConsoleStyle.NOITE), TvBanner.fromStored("console:sw2:later"))
        assertEquals("app:pkg.a/Main", TvBanner.itemKeyOf(TvBanner.storageKey(app.key)))
        assertNull(TvBanner.itemKeyOf("tv.banner."))
    }

    @Test
    fun `a typed name is cleaned, and the input's own name is not kept`() {
        assertEquals("PlayStation 5", TvInputNames.clean("  PlayStation   5 ", own = "HDMI 2"))
        assertNull(TvInputNames.clean("HDMI 2", own = "HDMI 2"))
        assertNull(TvInputNames.clean("   ", own = "HDMI 2"))
        assertEquals(TvInputNames.MAX_LENGTH, TvInputNames.clean("x".repeat(40), own = "HDMI 2")!!.length)
    }

    @Test
    fun `the look is read with defaults and kept within its levels`() {
        val stored = mapOf(TvLook.KEY_ARRANGEMENT to "carousel", TvLook.KEY_ALIGN to "spread", TvLook.KEY_NAMES to "?")
        val look = TvLook.fromStored({ stored[it] }, { if (it == TvLook.KEY_SIZE) 9 else null })
        assertEquals(TvArrangement.CAROUSEL, look.arrangement)
        assertEquals(TvAlign.SPREAD, look.align)
        assertEquals(TvNameShow.FOCUS, look.names)
        assertEquals(TvLook.SIZES.last, look.bannerSize)
        assertEquals(226f, look.bannerWidthDp)
    }

    @Test
    fun `the room between banners and their shadow are read and kept within their levels`() {
        val default = TvLook.fromStored({ null }, { null })
        assertEquals(TvLook.DEFAULT_GAP, default.gap)
        assertEquals(1f, default.gapScale)
        assertEquals(GlassLevel.MEDIUM, default.shadow)
        assertEquals(1f, default.shadowAmount)
        val stored = mapOf(TvLook.KEY_SHADOW to "off")
        val look = TvLook.fromStored({ stored[it] }, { if (it == TvLook.KEY_GAP) 40 else null })
        assertEquals(TvLook.GAPS.last, look.gap)
        assertEquals(GlassLevel.OFF, look.shadow)
        assertEquals(0f, look.shadowAmount)
        assertEquals(TvLook.GAPS.first, TvLook().withGap(-3).gap)
        assertTrue(TvLook(shadow = GlassLevel.LIGHT).shadowAmount < 1f)
        assertTrue(TvLook(shadow = GlassLevel.STRONG).shadowAmount > 1f)
    }

    @Test
    fun `more room between banners moves them apart, in the grid and in the carousel`() {
        val usual = TvArrange.place(11, TvLook(), TvArrange.HEIGHT, 0, 0f).boxes
        val close = TvArrange.place(11, TvLook(gap = 1), TvArrange.HEIGHT, 0, 0f).boxes
        val far = TvArrange.place(11, TvLook(gap = 7), TvArrange.HEIGHT, 0, 0f).boxes
        val step: (List<TvBox>) -> Float = { boxes -> boxes[1].left - boxes[0].left }
        assertTrue(step(close) < step(usual))
        assertTrue(step(far) > step(usual))
        assertTrue(far.all { it.right <= TvArrange.WIDTH })
        // The rows come apart too, as long as there is a second one.
        val rowGap: (List<TvBox>) -> Float = { boxes -> boxes.first { it.top > boxes[0].top }.top - boxes[0].bottom }
        assertTrue(rowGap(close) < rowGap(usual))
        val carousel = TvLook(arrangement = TvArrangement.CAROUSEL)
        val c1 = TvArrange.place(8, carousel, TvArrange.HEIGHT, 0, 0f).boxes
        val c7 = TvArrange.place(8, carousel.withGap(7), TvArrange.HEIGHT, 0, 0f).boxes
        assertTrue(step(c7) > step(c1))
    }

    @Test
    fun `every console has a known id, and stepping goes round`() {
        assertEquals(TvConsoles.ALL.size, TvConsoles.ALL.map { it.id }.toSet().size)
        val ps5 = assertNotNull(TvConsoles.byId("ps5"))
        val playstation = TvConsoles.of(ConsoleFamily.PLAYSTATION)
        assertEquals(playstation.last(), TvConsoles.stepModel(playstation.first(), -1))
        assertEquals(ps5, TvConsoles.stepModel(TvConsoles.stepModel(ps5, 1), -1))
        assertNull(TvConsoles.stepFamily(TvConsoles.of(ConsoleFamily.PC).first(), 1))
        assertEquals(TvConsoles.of(ConsoleFamily.NINTENDO).first(), TvConsoles.stepFamily(null, 1))
    }

    @Test
    fun `a long name is smaller, and never wider than its room`() {
        val short = ConsoleStyle.NOITE.nameSize("5")
        val long = ConsoleStyle.NOITE.nameSize("Master System")
        assertTrue(long < short)
        for (style in ConsoleStyle.entries) {
            for (console in TvConsoles.ALL) {
                val size = style.nameSize(console.nameLine)
                assertTrue(size > 0f && size <= 28f, "${style.key} ${console.id}: $size")
            }
        }
        assertTrue(ConsoleStyle.NOITE.nameSize("Switch 2", BesideDrawing.WIDE) < ConsoleStyle.NOITE.nameSize("Switch 2"))
    }

    @Test
    fun `the grid fills rows from the left and stays within the screen`() {
        val look = TvLook()
        val placed = TvArrange.place(count = 11, look = look, height = TvArrange.HEIGHT, focus = 0, scroll = 0f)
        val boxes = placed.boxes
        assertEquals(11, boxes.size)
        assertEquals(56f, boxes[0].left)
        assertEquals(boxes[0].top, boxes[1].top)
        assertTrue(boxes.all { it.left >= 0f && it.right <= TvArrange.WIDTH })
        // Five of 148 wide fit in a row at the default size.
        assertTrue(boxes[5].top > boxes[4].top)
        assertEquals(boxes[0].left, boxes[5].left)
    }

    @Test
    fun `spread reaches from one side to the other, and center is even on both`() {
        val spread = TvArrange.place(10, TvLook(align = TvAlign.SPREAD), TvArrange.HEIGHT, 0, 0f).boxes
        assertEquals(30f, spread[0].left)
        assertEquals(TvArrange.WIDTH - 30f, spread[4].right, 0.01f)
        val centered = TvArrange.place(3, TvLook(align = TvAlign.CENTER), TvArrange.HEIGHT, 0, 0f).boxes
        assertEquals(centered[0].left, TvArrange.WIDTH - centered[2].right, 0.01f)
    }

    @Test
    fun `a grid taller than the screen scrolls to keep the focus in sight`() {
        val look = TvLook(bannerSize = 5)
        val first = TvArrange.place(30, look, TvArrange.HEIGHT, focus = 0, scroll = 0f)
        assertEquals(0f, first.scroll)
        val last = TvArrange.place(30, look, TvArrange.HEIGHT, focus = 29, scroll = first.scroll)
        assertTrue(last.scroll > 0f)
        assertTrue(last.boxes[29].bottom <= TvArrange.HEIGHT)
        // Going back up a row moves only as far as needed.
        val back = TvArrange.place(30, look, TvArrange.HEIGHT, focus = 25, scroll = last.scroll)
        assertTrue(back.scroll <= last.scroll)
    }

    @Test
    fun `the carousel sits low and slides along with the focus`() {
        val look = TvLook(arrangement = TvArrangement.CAROUSEL)
        val start = TvArrange.place(20, look, TvArrange.HEIGHT, 0, 0f)
        assertTrue(start.boxes[0].top > TvArrange.HEIGHT / 2f)
        val far = TvArrange.place(20, look, TvArrange.HEIGHT, 19, start.scroll)
        assertTrue(far.boxes[19].right <= TvArrange.WIDTH)
        // Half a banner of the next one stays in sight while there is more.
        val third = TvArrange.place(20, look, TvArrange.HEIGHT, 5, start.scroll)
        assertTrue(third.boxes[6].left < TvArrange.WIDTH - third.bannerWidth * 0.4f)
        assertEquals(0f, TvArrange.place(20, look, TvArrange.HEIGHT, 0, third.scroll).scroll, 0.01f)
        val centered = TvArrange.place(20, look.copy(align = TvAlign.CENTER), TvArrange.HEIGHT, 7, 0f)
        assertEquals(TvArrange.WIDTH / 2f, centered.boxes[7].centerX, 0.01f)
    }

    @Test
    fun `the focus goes to the nearest box ahead`() {
        val boxes = TvArrange.place(11, TvLook(), TvArrange.HEIGHT, 0, 0f).boxes
        assertEquals(1, TvFocus.nearest(boxes[0], boxes, TvDirection.RIGHT))
        assertEquals(-1, TvFocus.nearest(boxes[0], boxes, TvDirection.LEFT))
        assertEquals(5, TvFocus.nearest(boxes[0], boxes, TvDirection.DOWN))
        assertEquals(-1, TvFocus.nearest(boxes[0], boxes, TvDirection.UP))
        // From the end of a short last row, down goes nowhere and up goes to the one above.
        assertEquals(5, TvFocus.nearest(boxes[10], boxes, TvDirection.UP))
        assertEquals(-1, TvFocus.nearest(boxes[10], boxes, TvDirection.DOWN))
        // At the end of a row, right goes up to the row above unless it has to stay in its row.
        assertEquals(-1, TvFocus.nearest(boxes[10], boxes, TvDirection.RIGHT, sameRow = true))
        assertEquals(4, TvFocus.nearest(boxes[3], boxes, TvDirection.RIGHT, sameRow = true))
        assertEquals(-1, TvFocus.nearest(boxes[4], boxes, TvDirection.RIGHT, sameRow = true))
    }

    @Test
    fun `all apps puts the inputs first, each part under its name`() {
        val all = TvAllPlacement.place(inputs = 3, apps = 8)
        assertEquals(11, all.tiles.size)
        assertEquals(0f, all.inputsTitleTop)
        assertTrue(all.appsTitleTop > all.tiles[2].bottom)
        assertTrue(all.tiles[3].top > all.appsTitleTop)
        assertEquals(all.tiles[3].left, all.tiles[9].left)
        assertNull(TvAllPlacement.place(inputs = 0, apps = 2).inputsTitleTop)
        assertEquals(0f, all.scrollFor(0, room = 436f))
    }

    @Test
    fun `widgets are saved with their sizes, and the clock and stocks come first`() {
        assertEquals(TvWidgets.DEFAULT, TvWidgets.decode(null))
        assertEquals(TvWidgets.NONE, TvWidgets.decode(""))
        val widgets = TvWidgets.NONE.with(TvWidgetKind.MARKET, 2).with(TvWidgetKind.CLOCK, 9)
        assertEquals("market:2,clock:2", TvWidgets.encode(widgets))
        assertEquals(widgets, TvWidgets.decode(TvWidgets.encode(widgets)))
        // Unknown kinds and a second copy of a kind are left out.
        assertEquals(listOf(TvWidgetKind.CLOCK), TvWidgets.decode("clock:1,weather:0,clock:2").list.map { it.kind })
        assertEquals(1, TvWidgets.decode("clock:1").of(TvWidgetKind.CLOCK)?.size)
    }

    @Test
    fun `widgets grow round, move along and come off`() {
        val widgets = TvWidgets.DEFAULT
        assertEquals(1, widgets.grown(TvWidgetKind.CLOCK).of(TvWidgetKind.CLOCK)?.size)
        assertEquals(0, widgets.grown(TvWidgetKind.CLOCK).grown(TvWidgetKind.CLOCK).grown(TvWidgetKind.CLOCK).of(TvWidgetKind.CLOCK)?.size)
        assertEquals(listOf(TvWidgetKind.MARKET, TvWidgetKind.CLOCK), widgets.moved(TvWidgetKind.CLOCK, 1).list.map { it.kind })
        assertEquals(widgets, widgets.moved(TvWidgetKind.CLOCK, -1))
        assertEquals(listOf(TvWidgetKind.CLOCK), widgets.without(TvWidgetKind.CLOCK).missing)
        assertTrue(widgets.missing.isEmpty())
        assertEquals(TvWidgetKind.MARKET, widgets.byKey(TvWidget(TvWidgetKind.MARKET).key)?.kind)
    }

    @Test
    fun `the widgets sit in one row at the top, aligned like the banners`() {
        val start = TvArrange.widgets(listOf(210f, 250f), TvAlign.START, TvArrangement.GRID)
        assertEquals(56f, start[0].left)
        assertEquals(92f, start[0].top)
        assertEquals(start[0].right + 18f, start[1].left)
        // The grid starts under them.
        val grid = TvArrange.place(4, TvLook(), 540f, 0, 0f, withWidgets = true)
        assertTrue(grid.boxes[0].top > start[0].bottom)

        val center = TvArrange.widgets(listOf(210f, 250f), TvAlign.CENTER, TvArrangement.CAROUSEL)
        assertEquals(TvArrange.WIDTH - center[1].right, center[0].left, 0.01f)
        assertEquals(116f, center[0].top)

        val spread = TvArrange.widgets(listOf(210f, 250f), TvAlign.SPREAD, TvArrangement.GRID)
        assertEquals(30f, spread[0].left)
        assertEquals(TvArrange.WIDTH - 30f, spread[1].right, 0.01f)

        // Too wide for the screen: they share it, and still leave the margins.
        val wide = TvArrange.widgets(listOf(340f, 340f, 340f), TvAlign.START, TvArrangement.GRID)
        assertTrue(wide.last().right <= TvArrange.WIDTH - 56f)
        assertTrue(TvArrange.widgets(emptyList(), TvAlign.START, TvArrangement.GRID).isEmpty())
    }
}
