package io.github.johankrs.kiri.market

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketPlanTest {
    private fun plan(width: Int, height: Int, items: Int, chart: Boolean = true, extended: Boolean = false) =
        MarketPlanner.plan(width.toFloat(), height.toFloat(), items, chart, extended)

    @Test
    fun `a small square shows one item, large`() {
        val plan = plan(190, 190, items = 5)
        assertEquals(MarketLayout.SINGLE, plan.layout)
        assertEquals(1, plan.shown)
    }

    @Test
    fun `a short strip puts items side by side`() {
        val plan = plan(390, 92, items = 5)
        assertEquals(MarketLayout.TILES, plan.layout)
        assertEquals(3, plan.columns)
        assertEquals(3, plan.shown)
        assertTrue(!plan.dense)
    }

    @Test
    fun `one item alone in a strip gets a full line, with name and chart`() {
        val plan = plan(390, 100, items = 1)
        assertEquals(MarketLayout.LIST, plan.layout)
        assertTrue(plan.showChart && !plan.dense && !plan.showHeader)
        // Two items would need the tight list, so they stay side by side.
        assertEquals(MarketLayout.TILES, plan(390, 100, items = 2).layout)
    }

    @Test
    fun `a very short strip drops a line from each tile instead of shrinking the text`() {
        val plan = plan(390, 60, items = 5)
        assertEquals(MarketLayout.TILES, plan.layout)
        assertTrue(plan.dense)
        assertEquals(2, plan.columns)
        assertEquals(100, plan.scalePercent)
    }

    @Test
    fun `a narrow tall space stacks tiles`() {
        val plan = plan(190, 400, items = 5)
        assertEquals(MarketLayout.TILES, plan.layout)
        assertEquals(1, plan.columns)
        assertEquals(5, plan.shown)
        // One item alone has nothing to stack.
        assertEquals(MarketLayout.SINGLE, plan(190, 400, items = 1).layout)
    }

    @Test
    fun `the list keeps names and title while every item fits`() {
        val plan = plan(390, 290, items = 4)
        assertEquals(MarketLayout.LIST, plan.layout)
        assertEquals(1, plan.columns)
        assertEquals(4, plan.shown)
        assertTrue(plan.showHeader && plan.showChart && !plan.dense)
    }

    @Test
    fun `items come before decoration`() {
        // Four items do not fit with names in this height: the list tightens rather than hide one.
        val tight = plan(390, 190, items = 4)
        assertEquals(MarketLayout.LIST, tight.layout)
        assertTrue(tight.dense && !tight.showHeader)
        assertEquals(4, tight.shown)
        // Three do fit, so they keep their names.
        val roomy = plan(390, 190, items = 3)
        assertTrue(!roomy.dense)
        assertEquals(3, roomy.shown)
    }

    @Test
    fun `the chart goes when the line is too narrow for it, or when it is switched off`() {
        assertTrue(plan(390, 290, items = 3).showChart)
        assertTrue(!plan(250, 290, items = 3).showChart)
        assertTrue(!plan(390, 290, items = 3, chart = false).showChart)
    }

    @Test
    fun `a wide space gets more columns only when one is not enough`() {
        val many = plan(800, 290, items = 8)
        assertEquals(MarketLayout.LIST, many.layout)
        assertEquals(2, many.columns)
        assertEquals(8, many.shown)
        assertEquals(1, plan(800, 290, items = 4).columns)
    }

    @Test
    fun `spare space makes everything larger, never at the cost of an item`() {
        // One item in a strip made for three.
        assertEquals(MarketPlanner.MAX_SCALE_PERCENT, plan(390, 100, items = 1).scalePercent)
        // Three items with room to spare, but not for the largest size.
        assertEquals(115, plan(390, 190, items = 3).scalePercent)
        // A full list stays at the base size.
        assertEquals(100, plan(390, 290, items = 5).scalePercent)
    }

    @Test
    fun `names that wrap count as taller lines`() {
        val oneLine = plan(390, 290, items = 5)
        val wrapped = MarketPlanner.plan(390f, 290f, 5, true, rowHeight = { 66f })
        assertTrue(!oneLine.dense)
        // Five lines of 66 do not fit, so the list tightens to keep all five.
        assertTrue(wrapped.dense)
        assertEquals(5, wrapped.shown)
    }

    @Test
    fun `nothing to show is still a valid plan`() {
        val plan = plan(390, 290, items = 0)
        assertEquals(0, plan.shown)
        assertEquals(100, plan.scalePercent)
    }

    @Test
    fun `a larger widget never shows fewer items and text never shrinks`() {
        for (items in 1..12) {
            for (width in 100..1000 step 10) {
                var before = 0
                for (height in 48..800 step 4) {
                    val plan = plan(width, height, items)
                    assertTrue(plan.shown >= before, "taller hid an item at $width x $height, $items items")
                    assertTrue(plan.shown in 1..items)
                    assertTrue(plan.scalePercent in 100..MarketPlanner.MAX_SCALE_PERCENT)
                    before = plan.shown
                }
            }
            for (height in 48..800 step 8) {
                var before = 0
                for (width in 100..1000 step 4) {
                    val shown = plan(width, height, items).shown
                    assertTrue(shown >= before, "wider hid an item at $width x $height, $items items")
                    before = shown
                }
            }
        }
    }

    @Test
    fun `a price outside regular hours makes every item one line taller`() {
        // Four lines and the title fit here without it; with it the title gives way.
        assertTrue(plan(390, 290, items = 4).showHeader)
        val list = plan(390, 290, items = 4, extended = true)
        assertEquals(MarketLayout.LIST, list.layout)
        assertTrue(list.showExtended && !list.dense && !list.showHeader)
        assertEquals(4, list.shown)
        // With room for the taller lines the title stays.
        val roomy = plan(390, 340, items = 4, extended = true)
        assertTrue(roomy.showExtended && !roomy.dense && roomy.showHeader)

        val strip = plan(390, 100, items = 5, extended = true)
        assertEquals(MarketLayout.TILES, strip.layout)
        assertTrue(strip.showExtended && !strip.dense)
        assertEquals(3, strip.shown)

        val square = plan(190, 190, items = 5, extended = true)
        assertEquals(MarketLayout.SINGLE, square.layout)
        assertTrue(square.showExtended)
    }

    @Test
    fun `it is part of the item, so it may cost an item but is never cut`() {
        assertEquals(5, plan(390, 190, items = 5).shown)
        val taller = plan(390, 190, items = 5, extended = true)
        assertTrue(taller.showExtended)
        assertEquals(3, taller.shown)
        // Where not even one item fits whole with it, it gives way.
        val tiny = plan(190, 60, items = 3, extended = true)
        assertTrue(!tiny.showExtended)
        assertEquals(plan(190, 60, items = 3), tiny)
        // No item has one: nothing changes.
        assertEquals(plan(390, 290, items = 4), plan(390, 290, items = 4, extended = false))
    }

    @Test
    fun `with the extra line a larger widget still never shows fewer items`() {
        for (items in 1..12) {
            for (width in 100..1000 step 10) {
                var before = 0
                for (height in 48..800 step 4) {
                    val plan = plan(width, height, items, extended = true)
                    // Only once the line appears can the count start again.
                    if (plan.showExtended) {
                        assertTrue(plan.shown >= before, "taller hid an item at $width x $height, $items items")
                        before = plan.shown
                    } else {
                        assertEquals(0, before, "the line went away again at $width x $height, $items items")
                    }
                    assertTrue(plan.shown in 1..items)
                    assertTrue(plan.scalePercent in 100..MarketPlanner.MAX_SCALE_PERCENT)
                }
            }
            for (height in 48..800 step 8) {
                var before = 0
                for (width in 100..1000 step 4) {
                    val plan = plan(width, height, items, extended = true)
                    if (plan.showExtended) {
                        assertTrue(plan.shown >= before, "wider hid an item at $width x $height, $items items")
                        before = plan.shown
                    } else {
                        assertEquals(0, before, "the line went away again at $width x $height, $items items")
                    }
                }
            }
        }
    }
}
