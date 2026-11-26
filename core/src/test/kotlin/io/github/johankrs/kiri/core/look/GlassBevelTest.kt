package io.github.johankrs.kiri.core.look

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassBevelTest {
    private val size = 120
    private val light = GlassBevel.Light(rimTop = 0.4f, rimBottom = 0.2f)
    private val grey = 0xFF808080.toInt()

    /** A plain grey tile cut to [shape], its edge smooth as Android cuts it. */
    private fun tile(shape: IconShape): IntArray = IntArray(size * size) { index ->
        val cover = (shape.depth(index % size + 0.5f, index / size + 0.5f, size.toFloat()) + 0.5f).coerceIn(0f, 1f)
        ((cover * 255).toInt() shl 24) or (grey and 0xFFFFFF)
    }

    private fun brightness(pixel: Int): Int = ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3

    private fun at(pixels: IntArray, x: Int, y: Int): Int = pixels[y * size + x]

    @Test
    fun theDepthOfEveryShapeIsZeroOnItsEdgeAndHalfTheTileInItsMiddle() {
        for (shape in IconShape.entries) {
            val middle = shape.depth(60f, 60f, 120f)
            assertTrue(abs(middle - 60f) < 1f, "$shape: $middle in the middle")
            assertTrue(abs(shape.depth(60f, 0f, 120f)) < 0.5f, "$shape: top edge")
            assertTrue(shape.depth(60f, -10f, 120f) < 0f, "$shape: above it")
        }
    }

    @Test
    fun theTopOfTheBevelCatchesTheLightAndTheBottomIsInShade() {
        for (shape in IconShape.entries) {
            val dressed = GlassBevel.dress(tile(shape), size, shape, light, bend = false, under = false)
            val top = brightness(at(dressed, 60, 4))
            val bottom = brightness(at(dressed, 60, size - 5))
            assertTrue(top > brightness(grey), "$shape: top $top")
            assertTrue(bottom < brightness(grey), "$shape: bottom $bottom")
            assertTrue(top > bottom + 15, "$shape: top $top, bottom $bottom")
        }
    }

    @Test
    fun theMiddleOfTheTileIsLeftAlone() {
        val before = tile(IconShape.SQUIRCLE)
        val dressed = GlassBevel.dress(before, size, IconShape.SQUIRCLE, light, bend = true, under = false)
        for (y in 30..90) for (x in 30..90) assertEquals(at(before, x, y), at(dressed, x, y))
    }

    @Test
    fun theUpperLeftCornerShinesMoreThanTheUpperRightOne() {
        val shape = IconShape.ROUNDED
        val dressed = GlassBevel.dress(tile(shape), size, shape, light, bend = false, under = false)
        // Points on the diagonals, just inside the rounded corners.
        val left = brightness(at(dressed, 12, 12))
        val right = brightness(at(dressed, size - 13, 12))
        assertTrue(left > right, "upper left $left, upper right $right")
    }

    @Test
    fun aDrawingOnAPlateStaysOverTheGlass() {
        val shape = IconShape.CIRCLE
        // A solid red stroke across the top edge, the rest see-through.
        val drawing = IntArray(size * size) { index -> if (index / size in 2..5) 0xFFFF0000.toInt() else 0 }
        val dressed = GlassBevel.dress(drawing, size, shape, light, bend = false, under = true)
        assertEquals(0xFFFF0000.toInt(), at(dressed, 60, 3))
        // Where nothing is drawn, the light of the glass shows.
        assertTrue(at(dressed, 60, 8) ushr 24 > 0)
        assertEquals(0, at(dressed, 60, 60))
    }

    @Test
    fun theShadowFallsBelowTheTileAndNotUnderIt() {
        val pad = 12
        val shape = IconShape.SQUIRCLE
        val shadow = GlassBevel.shadow(size, shape, pad)
        val whole = size + 2 * pad
        fun alpha(x: Int, y: Int) = shadow[(y + pad) * whole + x + pad] ushr 24
        assertEquals(0, alpha(60, 60))
        assertTrue(alpha(60, size + 3) > alpha(60, -4), "below ${alpha(60, size + 3)}, above ${alpha(60, -4)}")
    }

    @Test
    fun aDeeperReliefShowsMoreAndNoneShowsNothing() {
        val shape = IconShape.SQUIRCLE
        val before = tile(shape)
        val off = GlassBevel.Tile.of(IconLook(relief = GlassLevel.OFF))
        assertTrue(GlassBevel.dress(before, size, shape, light, bend = true, under = false, tile = off).contentEquals(before))
        fun bottom(level: GlassLevel) = brightness(
            at(GlassBevel.dress(before, size, shape, light, bend = false, under = false, tile = GlassBevel.Tile.of(IconLook(relief = level))), 60, size - 5),
        )
        assertTrue(bottom(GlassLevel.LIGHT) > bottom(GlassLevel.MEDIUM))
        assertTrue(bottom(GlassLevel.MEDIUM) > bottom(GlassLevel.STRONG))
    }

    @Test
    fun theLightOnTheEdgeAndItsWidthHaveTheirOwnLevels() {
        val shape = IconShape.SQUIRCLE
        val before = tile(shape)
        fun at(look: IconLook, y: Int) = brightness(
            at(GlassBevel.dress(before, size, shape, light, bend = false, under = false, tile = GlassBevel.Tile.of(look)), 60, y),
        )
        assertTrue(at(IconLook(edgeLight = GlassLevel.LIGHT), 4) < at(IconLook(edgeLight = GlassLevel.STRONG), 4))
        // A wider edge is lit further in.
        assertTrue(at(IconLook(edgeWidth = 1), 13) < at(IconLook(edgeWidth = 5), 13))
    }

    @Test
    fun theShadowHasItsOwnLevels() {
        val pad = 12
        val whole = size + 2 * pad
        fun below(level: GlassLevel): Int {
            val shadow = GlassBevel.shadow(size, IconShape.CIRCLE, pad, GlassBevel.Tile.of(IconLook(shadow = level)).shadow)
            return shadow[(size + pad + 3) * whole + whole / 2] ushr 24
        }
        assertEquals(0, below(GlassLevel.OFF))
        assertTrue(below(GlassLevel.LIGHT) < below(GlassLevel.STRONG))
    }

    @Test
    fun theLevelsOfTheTileAreKept() {
        val saved = mapOf(
            IconLook.KEY_RELIEF to "strong",
            IconLook.KEY_EDGE_LIGHT to "light",
            IconLook.KEY_SHADOW to "off",
        )
        val look = IconLook.fromStored(
            string = { saved[it] },
            int = { if (it == IconLook.KEY_EDGE_WIDTH) 99 else null },
            boolean = { null },
        )
        assertEquals(GlassLevel.STRONG, look.relief)
        assertEquals(GlassLevel.LIGHT, look.edgeLight)
        assertEquals(IconLook.EDGE_WIDTH.last, look.edgeWidth)
        assertEquals(GlassLevel.OFF, look.shadow)
        assertEquals(GlassLevel.MEDIUM, IconLook().relief)
        assertEquals(GlassLevel.LIGHT, IconLook().shadow)
    }

    @Test
    fun aDarkPictureGivesBackLessLightThanALightOne() {
        val shape = IconShape.ROUNDED
        fun lifted(colour: Int): Int {
            val plain = IntArray(size * size) { index ->
                val cover = (shape.depth(index % size + 0.5f, index / size + 0.5f, size.toFloat()) + 0.5f).coerceIn(0f, 1f)
                ((cover * 255).toInt() shl 24) or (colour and 0xFFFFFF)
            }
            val dressed = GlassBevel.dress(plain, size, shape, light, bend = false, under = false)
            return brightness(at(dressed, 12, 12)) - brightness(colour)
        }
        assertTrue(lifted(0xFF101418.toInt()) < lifted(0xFF808080.toInt()))
    }

    @Test
    fun aPaneOfAnySizeIsLitLikeAnIcon() {
        val pane = GlassBevel.pane(corner = 30f, bevel = 9f, light = GlassBevel.lightOf(io.github.johankrs.kiri.core.GlassStyle.FUME))
        val middle = pane.middle
        fun alpha(x: Int, y: Int) = pane.pixels[y * pane.size + x] ushr 24
        fun pixel(x: Int, y: Int) = pane.pixels[y * pane.size + x]
        // The middle is clear, and so is everything outside the corners.
        assertEquals(0, alpha(middle, middle))
        assertEquals(0, alpha(0, 0))
        // The top edge is lit with white, the bottom one shaded with a dark colour.
        assertTrue(brightness(pixel(middle, 3)) > 200 && alpha(middle, 3) > 0, "top ${Integer.toHexString(pixel(middle, 3))}")
        assertTrue(brightness(pixel(middle, pane.size - 4)) < 100 && alpha(middle, pane.size - 4) > 0)
        // Along the top the edge is the same, so it can be stretched.
        assertEquals(pixel(middle, 3), pixel(middle + 1, 3))
    }

    @Test
    fun theShadowOfAPaneFallsBelowItOnly() {
        val shadow = GlassBevel.paneShadow(corner = 30f, scale = 150f, amount = 1f)
        val whole = shadow.size
        val pad = shadow.pad
        fun alpha(x: Int, y: Int) = shadow.pixels[y * whole + x] ushr 24
        val middle = whole / 2
        assertEquals(0, alpha(middle, middle))
        assertTrue(alpha(middle, whole - pad + 2) > alpha(middle, pad - 3))
        assertEquals(0, GlassBevel.paneShadow(30f, 150f, 0f).pixels.maxOrNull())
    }

    @Test
    fun theGlassUnderAnIconIsKeptWithinItsRange() {
        val look = IconLook.fromStored(
            string = { null },
            int = { if (it == IconLook.KEY_GLASS_OPACITY) 3 else null },
            boolean = { null },
        )
        assertEquals(IconLook.GLASS_OPACITY.first, look.glassOpacity)
        assertEquals(IconLook.DEFAULT_GLASS_OPACITY, IconLook().glassOpacity)
    }

    @Test
    fun dressingIntoAScratchBufferMatchesFreshOutputAndSurvivesClearingCaches() {
        val scratch = IntArray(size * size)
        for (shape in IconShape.entries) {
            val source = tile(shape)
            val fresh = GlassBevel.dress(source, size, shape, light, bend = true, under = false)
            val reused = GlassBevel.dress(source, size, shape, light, bend = true, under = false, into = scratch)
            assertTrue(reused === scratch)
            assertTrue(fresh.contentEquals(reused), "$shape: scratch output differed")

            val plateFresh = GlassBevel.dress(source, size, shape, light, bend = false, under = true)
            GlassBevel.clearCaches()
            val plateRebuilt = GlassBevel.dress(source, size, shape, light, bend = false, under = true, into = scratch)
            assertTrue(plateFresh.contentEquals(plateRebuilt), "$shape: plate differed after cache clear")
        }
    }
}
