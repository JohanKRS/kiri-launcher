package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderLookTest {
    @Test
    fun `columns are the square root rounded up, never more than the home screen has`() {
        val expected = mapOf(2 to 2, 3 to 2, 4 to 2, 5 to 3, 6 to 3, 7 to 3, 9 to 3, 10 to 4, 16 to 4, 17 to 5, 30 to 6)
        for ((count, columns) in expected) assertEquals(columns, FolderGrid.columns(count), "$count apps")
        assertEquals(4, FolderGrid.columns(30, most = 4))
        assertEquals(2, FolderGrid.columns(9, chosen = 2))
        // Never more columns than apps.
        assertEquals(3, FolderGrid.columns(3, chosen = 4))
    }

    @Test
    fun `the last row is centred`() {
        // Five apps in three columns: the last two sit half a cell in.
        assertEquals(0.5f to 1, FolderGrid.cell(3, 5, 3))
        assertEquals(1.5f to 1, FolderGrid.cell(4, 5, 3))
        assertEquals(2f to 0, FolderGrid.cell(2, 5, 3))
    }

    @Test
    fun `a closed folder fills its inside, and stands the last place for the rest`() {
        val look = FolderLook(margin = 0, spacing = 0)
        val four = FolderGrid.closed(4, look)
        assertEquals(4, four.slots.size)
        assertEquals(0.5f, four.slots[0].size, 0.0001f)
        assertEquals(0, four.more)

        val two = FolderGrid.closed(2, look)
        // Side by side, in the middle from top to bottom.
        assertEquals(0.25f, two.slots[0].y, 0.0001f)
        assertEquals(0.5f, two.slots[1].x, 0.0001f)

        val many = FolderGrid.closed(14, FolderLook())
        assertEquals(9, many.slots.size)
        assertEquals(8, many.drawn)
        assertEquals(6, many.more)
        for (slot in many.slots) {
            assertTrue(slot.x >= 0f && slot.y >= 0f && slot.x + slot.size <= 1.0001f && slot.y + slot.size <= 1.0001f)
        }
    }

    @Test
    fun `the look is read back from what was saved, and nonsense falls back to the default`() {
        val saved = mapOf<String, Any>(
            FolderLook.KEY_GROUND to "none",
            FolderLook.KEY_GROUND_OPACITY to 500,
            FolderLook.KEY_SHAPE to "circle",
            FolderLook.KEY_MINIS to 7,
            FolderLook.KEY_PANEL_GLASS to "cristal",
            FolderLook.KEY_COLUMNS to 1,
            FolderLook.KEY_PANEL_SPACING to 9,
            FolderLook.KEY_PANEL_MARGIN to -3,
            FolderLook.KEY_PANEL_TITLE to false,
        )
        val look = FolderLook.fromStored(
            string = { saved[it] as? String },
            int = { saved[it] as? Int },
            boolean = { saved[it] as? Boolean },
        )
        assertEquals(FolderGround.NONE, look.ground)
        assertEquals(95, look.groundOpacity)
        assertEquals(IconShape.CIRCLE, look.shape)
        assertEquals(FolderLook.DEFAULT_MINIS, look.minis)
        assertEquals(GlassStyle.CRISTAL, look.panelGlass)
        assertEquals(FolderLook.BEST_FIT, look.columns)
        assertEquals(FolderLook.LEVELS.last, look.panelSpacing)
        assertEquals(FolderLook.LEVELS.first, look.panelMargin)
        assertEquals(false, look.panelTitle)
        assertEquals(FolderLook(), FolderLook.fromStored({ null }, { null }, { null }))
    }

    @Test
    fun `more room in an open folder is more room both across and down`() {
        val gaps = FolderLook.LEVELS.map { FolderLook(panelSpacing = it) }
        for ((less, more) in gaps.zipWithNext()) {
            assertTrue(more.panelGapDp > less.panelGapDp)
            assertTrue(more.panelRowGapDp > less.panelRowGapDp)
        }
        // Apps side by side never touch, even at the least room.
        assertTrue(gaps.first().panelGapDp >= 8f)
        // The margin of an open folder grows with its level, and the default is as it was.
        val margins = FolderLook.LEVELS.map { FolderLook(panelMargin = it).panelMarginDp }
        assertEquals(margins.sorted(), margins)
        assertEquals(18f, FolderLook().panelMarginDp)
    }

    // A phone in portrait: 384 wide less 12 on each side, icons of 56, names 21 tall.
    private fun panel(count: Int, room: Float = 360f, names: Float = 21f) = FolderPanelCells.of(
        count = count,
        columns = FolderGrid.columns(count, most = 5),
        icon = 56f,
        labelBlock = names,
        gap = 20f,
        rowGap = 14f,
        pad = 18f,
        roomWidth = room,
        roomHeight = 760f,
    )

    @Test
    fun `an open folder of 4, 9 or 16 apps is a square, its icons as far apart across as down`() {
        for (count in listOf(4, 9, 16)) {
            val cells = panel(count)
            assertEquals(cells.width, cells.height, 0.01f, "$count apps")
            // The same room on every side of the cells.
            assertEquals(cells.pad, (cells.width - cells.columns * cells.cell) / 2f, 0.01f)
        }
        // 3 and 7 fill the last row in part, and are squares too; 6 is wider than tall.
        assertEquals(panel(3).width, panel(3).height, 0.01f)
        assertEquals(panel(7).width, panel(7).height, 0.01f)
        assertTrue(panel(6).width > panel(6).height)
    }

    @Test
    fun `an icon and its name sit in the middle of their cell`() {
        val cells = panel(9)
        assertEquals(56f, cells.icon)
        // 56 + 21 + 14: the name and the room under it set the cell.
        assertEquals(91f, cells.cell, 0.01f)
        assertEquals(7f, cells.iconTop, 0.01f)
        assertEquals(17.5f, cells.iconLeft, 0.01f)
        // Without names the room between icons sets it, the same across and down.
        val bare = panel(9, names = 0f)
        assertEquals(76f, bare.cell, 0.01f)
        assertEquals(bare.iconLeft, bare.iconTop, 0.01f)
    }

    @Test
    fun `short of room, the rows come closer first, then the icons get smaller`() {
        // 16 apps in 360: cells of 81, so 4 between a name and the next icon would be too
        // little; the icons give a little instead.
        val sixteen = panel(16)
        assertEquals(81f, sixteen.cell, 0.01f)
        assertEquals(81f - 21f - FolderPanelCells.LEAST_ROW_GAP_DP, sixteen.icon, 0.01f)
        assertTrue(sixteen.icon >= 50f)
        // A narrower screen takes more from them, never down to nothing.
        val narrow = panel(16, room = 260f)
        assertTrue(narrow.icon < sixteen.icon && narrow.icon >= 1f)
        assertEquals(narrow.width, narrow.height, 0.01f)
    }
}
