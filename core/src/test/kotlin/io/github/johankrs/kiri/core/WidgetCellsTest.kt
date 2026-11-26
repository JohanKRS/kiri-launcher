package io.github.johankrs.kiri.core

import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.GridSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetCellsTest {
    private val grid = GridSize(columns = 4, rows = 6)

    @Test
    fun `a size becomes the fewest cells that hold it, gaps included`() {
        // Cells of 80 with 8 between them: two cells span 168, three span 256.
        assertEquals(1, WidgetCells.holding(40f, 80f, 8f))
        assertEquals(1, WidgetCells.holding(80f, 80f, 8f))
        assertEquals(2, WidgetCells.holding(81f, 80f, 8f))
        assertEquals(2, WidgetCells.holding(168f, 80f, 8f))
        assertEquals(3, WidgetCells.holding(169f, 80f, 8f))
        assertEquals(1, WidgetCells.holding(500f, 0f, 0f))
    }

    @Test
    fun `a widget starts with the cells its app asks for, or with what holds its size, inside the grid`() {
        val asked = CellSpan(5, 2)
        assertEquals(
            CellSpan(4, 2),
            WidgetCells.startSpan(asked, 0f, 0f, 80f, 100f, 8f, grid),
        )
        assertEquals(
            CellSpan(3, 1),
            WidgetCells.startSpan(null, 250f, 90f, 80f, 100f, 8f, grid),
        )
    }

    @Test
    fun `a step of size stops at the smallest and the largest size`() {
        val min = CellSpan(2, 1)
        val max = CellSpan(4, 6)
        val from = CellSpan(2, 6)
        assertNull(WidgetCells.stepped(from, -1, 0, min, max))
        assertNull(WidgetCells.stepped(from, 0, 1, min, max))
        assertEquals(CellSpan(3, 6), WidgetCells.stepped(from, 1, 0, min, max))
        assertEquals(CellSpan(2, 5), WidgetCells.stepped(from, 0, -1, min, max))
    }
}
