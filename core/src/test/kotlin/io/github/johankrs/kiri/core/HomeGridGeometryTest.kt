package io.github.johankrs.kiri.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where the cells of the home screen go: icons from margin to margin, rows as asked. */
class HomeGridGeometryTest {

    // A phone in portrait, as the S24 Ultra measures it without its bars.
    private val phone = HomeRoom(widthDp = 384f, heightDp = 800f, labelBlockDp = 38f)
    private val spec = GridSpec(5, 6, 16, 24, 12, 18, 56, true)

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun `the icons go from one margin to the other`() {
        val grid = HomeGridGeometry.of(spec, phone)
        // 384 less two margins of 16 is 352; five icons of 56 leave 72 for four gaps.
        near(56f, grid.iconDp)
        near(18f, grid.gapXDp)
        near(352f, 5 * grid.iconDp + 4 * grid.gapXDp)
    }

    @Test
    fun `the same room between rows as between columns`() {
        val grid = HomeGridGeometry.of(spec, phone)
        near(grid.shownGapXDp, grid.shownGapYDp)
        near(56f + 38f + 2 * HomeGridGeometry.CELL_PAD_DP, grid.cellHeightDp)
    }

    @Test
    fun `a room of its own between rows`() {
        val grid = HomeGridGeometry.of(spec.copy(evenGaps = false, rowGapDp = 30), phone)
        near(30f, grid.shownGapYDp)
        near(18f, grid.shownGapXDp)
    }

    @Test
    fun `what the rows leave free goes above, below or on both sides`() {
        val top = HomeGridGeometry.of(spec, phone)
        val middle = HomeGridGeometry.of(spec.copy(place = GridPlace.MIDDLE), phone)
        val bottom = HomeGridGeometry.of(spec.copy(place = GridPlace.BOTTOM), phone)
        near(0f, top.topDp)
        assertTrue(bottom.topDp > 0f)
        near(bottom.topDp / 2f, middle.topDp)
        // At the bottom, the last row's cell ends where the margin starts.
        val inside = phone.heightDp - spec.marginTopDp - spec.marginBottomDp
        near(inside, bottom.topDp + 6 * bottom.cellHeightDp + 5 * bottom.gapYDp)
    }

    @Test
    fun `rows that do not fit with the room asked come closer, then share the height`() {
        val seven = spec.copy(rows = 7)
        assertFalse(HomeGridGeometry.fits(seven, phone))
        val grid = HomeGridGeometry.of(seven, phone)
        val inside = phone.heightDp - spec.marginTopDp - spec.marginBottomDp
        near(inside, 7 * grid.cellHeightDp + 6 * grid.gapYDp)
        assertTrue(grid.shownGapYDp < grid.shownGapXDp)

        val far = HomeGridGeometry.of(spec.copy(rows = 14), phone)
        near(inside / 14, far.cellHeightDp)
        near(0f, far.gapYDp)
    }

    @Test
    fun `fits says how many rows and columns there is room for`() {
        assertTrue(HomeGridGeometry.fits(spec, phone))
        assertFalse(HomeGridGeometry.fits(spec.copy(rows = 7), phone))
        // Smaller margins above and below make room for one more.
        assertTrue(HomeGridGeometry.fits(spec.copy(rows = 7, marginTopDp = 8, marginBottomDp = 8), phone))
        // Six icons of 56 do not fit in 352 side by side.
        assertTrue(HomeGridGeometry.fits(spec.copy(columns = 6), phone))
        assertFalse(HomeGridGeometry.fits(spec.copy(columns = 7), phone))
        // Without names, more rows fit.
        assertTrue(HomeGridGeometry.fits(spec.copy(rows = 9), phone.copy(labelBlockDp = 0f)))
    }

    @Test
    fun `icons too large for the width get smaller and touch`() {
        val grid = HomeGridGeometry.of(spec.copy(columns = 8), phone)
        near(352f / 8, grid.iconDp)
        near(0f, grid.gapXDp)
    }

    @Test
    fun `a name has its cell and the room beside it`() {
        val grid = HomeGridGeometry.of(spec, phone)
        near(56f + 18f - HomeGridGeometry.LABEL_INSET_DP, grid.labelWidthDp)
        val touching = HomeGridGeometry.of(spec.copy(columns = 8), phone)
        near(touching.cellWidthDp, touching.labelWidthDp)
    }
}
