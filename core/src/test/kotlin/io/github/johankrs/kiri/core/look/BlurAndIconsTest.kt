package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurAndIconsTest {
    private fun solid(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    @Test
    fun aPlainPictureStaysTheSameWhenBlurred() {
        val pixels = IntArray(7 * 5) { solid(40, 120, 200) }
        BoxBlur.blur(pixels, width = 7, height = 5, radius = 3)
        assertTrue(pixels.all { it == solid(40, 120, 200) })
    }

    @Test
    fun noRadiusLeavesThePictureAlone() {
        val pixels = IntArray(9) { index -> solid(index * 20, 0, 0) }
        val before = pixels.copyOf()
        BoxBlur.blur(pixels, width = 3, height = 3, radius = 0)
        assertTrue(pixels.contentEquals(before))
    }

    @Test
    fun aBrightDotSpreadsEvenlyAroundItself() {
        val side = 21
        val pixels = IntArray(side * side) { solid(0, 0, 0) }
        pixels[10 * side + 10] = solid(255, 255, 255)
        BoxBlur.blur(pixels, width = side, height = side, radius = 2)

        fun red(x: Int, y: Int) = pixels[y * side + x] shr 16 and 0xFF
        assertTrue(red(10, 10) in 1..254, "the dot is no longer pure white")
        assertTrue(red(10, 10) >= red(12, 10), "it is brightest in the middle")
        assertEquals(red(8, 10), red(12, 10), "same to the left and to the right")
        assertEquals(red(10, 8), red(10, 12), "same above and below")
        // Whole numbers are rounded down at every pass, so across and down may differ by one.
        assertTrue(abs(red(8, 10) - red(10, 8)) <= 1, "same across and down")
        assertEquals(0, red(0, 0), "far away nothing changed")
    }

    @Test
    fun aHardEdgeBecomesAGradualOne() {
        val width = 20
        val pixels = IntArray(width * 3) { index -> if (index % width < 10) solid(0, 0, 0) else solid(200, 200, 200) }
        BoxBlur.blur(pixels, width = width, height = 3, radius = 2)
        val row = (0 until width).map { pixels[width + it] and 0xFF }
        assertEquals(row.sorted(), row, "it only ever gets lighter from left to right")
        assertEquals(0, row.first())
        assertEquals(200, row.last())
        assertTrue(abs(row[9] - row[10]) < 100, "no jump is left in the middle")
    }

    @Test
    fun aRadiusLargerThanThePictureIsFine() {
        val pixels = intArrayOf(solid(0, 0, 0), solid(90, 90, 90), solid(180, 180, 180))
        BoxBlur.blur(pixels, width = 3, height = 1, radius = 50)
        assertTrue(pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun aVeryLargeRadiusStillPreservesAPlainPicture() {
        val color = solid(72, 144, 216)
        val pixels = IntArray(4 * 4) { color }
        BoxBlur.blur(pixels, width = 4, height = 4, radius = 3000)
        assertTrue(pixels.all { it == color })
    }

    /**
     * Each pass is a plain average, rounded down: the same as dividing pixel by pixel, both for
     * the windows divided by a reciprocal (up to 255 pixels wide here) and for wider ones.
     */
    @Test
    fun everyPassIsExactlyThePlainAverage() {
        val side = 260
        val random = Random(7)
        val picture = IntArray(side * side) { solid(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        for (radius in listOf(1, 5, 60, 127, 128, 200)) {
            val blurred = picture.copyOf()
            BoxBlur.blur(blurred, width = side, height = side, radius = radius, passes = 1)
            val expected = plainAverage(plainAverage(picture, side, radius, across = true), side, radius, across = false)
            assertTrue(blurred.contentEquals(expected), "radius $radius")
        }
    }

    /** One pass of a box blur over a square picture, the slow and obvious way. */
    private fun plainAverage(pixels: IntArray, side: Int, radius: Int, across: Boolean): IntArray {
        val reach = minOf(radius, side)
        val window = 2 * reach + 1
        return IntArray(side * side) { index ->
            val x = index % side
            val y = index / side
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -reach..reach) {
                val pixel = if (across) {
                    pixels[y * side + (x + offset).coerceIn(0, side - 1)]
                } else {
                    pixels[(y + offset).coerceIn(0, side - 1) * side + x]
                }
                red += pixel shr 16 and 0xFF
                green += pixel shr 8 and 0xFF
                blue += pixel and 0xFF
            }
            solid(red / window, green / window, blue / window)
        }
    }

    @Test
    fun theSquircleTouchesTheMiddleOfEverySideAndStaysInsideTheSquare() {
        val points = IconShape.squirclePoints(steps = 8)
        assertEquals(16, points.size)
        assertEquals(1f, points[0], 0.0001f)
        assertEquals(0.5f, points[1], 0.0001f)
        assertTrue(points.all { it in 0f..1f })
        // A quarter of the way round it is at the bottom, in the middle.
        assertEquals(0.5f, points[4], 0.0001f)
        assertEquals(1f, points[5], 0.0001f)
    }

    @Test
    fun theIconLookIsReadBackAsItWasSaved() {
        val saved = mapOf(
            IconLook.KEY_SHAPE to "circle",
            IconLook.KEY_GLASS_USE to "all",
            IconLook.KEY_GLASS to GlassStyle.NEVOA.key,
        )
        val look = IconLook.fromStored(
            string = { saved[it] },
            int = { if (it == IconLook.KEY_ART) 999 else null },
            boolean = { false },
        )
        assertEquals(IconShape.CIRCLE, look.shape)
        assertEquals(IconGlassUse.ALL, look.glassUse)
        assertEquals(GlassStyle.NEVOA, look.glass)
        // Turned off when it could only be on or off, it stays off.
        assertEquals(GlassLevel.OFF, look.relief)
        assertEquals(IconLook.ART_PERCENT.last, look.artPercent)
        assertEquals(IconLook(), IconLook.fromStored({ null }, { null }, { null }))
    }

    @Test
    fun theSizeOfOneAppsDrawingHasItsOwnKey() {
        val key = IconLook.appArtKey("pkg/Activity")
        assertEquals("pkg/Activity", IconLook.appKeyOfArt(key))
        assertNull(IconLook.appKeyOfArt(IconLook.KEY_ART))
        assertNull(IconLook.appKeyOfArt(IconChoice.storageKey("pkg/Activity")))
        // And the other way round: a look setting is never taken for the icon picked for an app.
        assertNull(IconChoice.appKeyOf(IconLook.KEY_SHAPE))
        assertNull(IconChoice.appKeyOf(key))
    }

    @Test
    fun whatOneAppsIconSitsOnHasItsOwnKey() {
        val key = IconLook.appGroundKey("pkg/Activity")
        assertEquals("pkg/Activity", IconLook.appKeyOfGround(key))
        assertNull(IconLook.appKeyOfGround(IconLook.appArtKey("pkg/Activity")))
        assertNull(IconLook.appKeyOfArt(key))
        assertNull(IconChoice.appKeyOf(key))
        assertEquals(IconGround.GLASS, IconGround.fromKey("glass"))
        assertNull(IconGround.fromKey("nothing"))
    }

    @Test
    fun whatWasChosenForAnAppWinsOverTheSharedLook() {
        // Nothing chosen: the shared look decides.
        assertEquals(false, IconSettings.wantsGlass(IconGlassUse.OFF, null, ownBackground = false))
        assertEquals(true, IconSettings.wantsGlass(IconGlassUse.TRANSPARENT_ONLY, null, ownBackground = false))
        assertEquals(false, IconSettings.wantsGlass(IconGlassUse.TRANSPARENT_ONLY, null, ownBackground = true))
        assertEquals(true, IconSettings.wantsGlass(IconGlassUse.ALL, null, ownBackground = true))
        // Chosen for the app: it wins both ways.
        assertEquals(true, IconSettings.wantsGlass(IconGlassUse.OFF, IconGround.GLASS, ownBackground = true))
        assertEquals(false, IconSettings.wantsGlass(IconGlassUse.ALL, IconGround.OWN, ownBackground = false))
        val settings = IconSettings(grounds = mapOf("a" to IconGround.GLASS))
        assertEquals(true, settings.onGlass("a", ownBackground = true))
        assertEquals(false, settings.onGlass("b", ownBackground = true))
    }

    /** A picture of [side] by [side] where [drawn] says which pixels are solid. */
    private fun picture(side: Int, drawn: (x: Int, y: Int) -> Boolean): IntArray =
        IntArray(side * side) { index -> if (drawn(index % side, index / side)) solid(10, 10, 10) else 0 }

    @Test
    fun theBoxAroundADrawingIgnoresWhatIsEmpty() {
        val pixels = picture(10) { x, y -> x in 2..5 && y in 3..8 }
        assertEquals(PixelBox(2, 3, 6, 9), IconPixels.artBounds(pixels, 10, 10))
        assertNull(IconPixels.artBounds(IntArray(100), 10, 10))
    }

    @Test
    fun aFilledDiscHasItsOwnBackgroundAndARingDoesNot() {
        val side = 48
        fun distanceSquared(x: Int, y: Int): Float {
            val across = x + 0.5f - side / 2f
            val down = y + 0.5f - side / 2f
            return across * across + down * down
        }
        val disc = picture(side) { x, y -> distanceSquared(x, y) <= 18f * 18f }
        val discBox = assertNotNull(IconPixels.artBounds(disc, side, side))
        assertTrue(IconPixels.isSolidBody(disc, side, discBox))
        assertFalse(IconPixels.fillsPicture(discBox, side, side))

        val ring = picture(side) { x, y -> distanceSquared(x, y) in (14f * 14f)..(18f * 18f) }
        val ringBox = assertNotNull(IconPixels.artBounds(ring, side, side))
        assertFalse(IconPixels.isSolidBody(ring, side, ringBox))
    }

    @Test
    fun aBadgeIsAPictureAndAFewThinLinesAreNot() {
        val side = 48
        fun distanceSquared(x: Int, y: Int): Float {
            val across = x + 0.5f - side / 2f
            val down = y + 0.5f - side / 2f
            return across * across + down * down
        }
        // A disc with a hole in it: not a solid body, but still a picture with substance.
        val badge = picture(side) { x, y -> distanceSquared(x, y) in (6f * 6f)..(18f * 18f) }
        val badgeBox = assertNotNull(IconPixels.artBounds(badge, side, side))
        assertFalse(IconPixels.isSolidBody(badge, side, badgeBox))
        assertTrue(IconPixels.isPicture(badge, side, badgeBox))

        val outline = picture(side) { x, y -> distanceSquared(x, y) in (17f * 17f)..(18f * 18f) }
        val outlineBox = assertNotNull(IconPixels.artBounds(outline, side, side))
        assertFalse(IconPixels.isPicture(outline, side, outlineBox))

        val cross = picture(side) { x, y -> (x in 23..24 && y in 6..41) || (y in 23..24 && x in 6..41) }
        val crossBox = assertNotNull(IconPixels.artBounds(cross, side, side))
        assertFalse(IconPixels.isPicture(cross, side, crossBox))
    }

    @Test
    fun aWhiteIconGetsAnEdgeInTheColourOfItsDrawing() {
        val side = 48
        val white = (0xFF shl 24) or 0xFFFFFF
        val red = (0xFF shl 24) or (0xE0 shl 16) or (0x20 shl 8) or 0x30
        // A white tile with a red square in the middle.
        val tile = IntArray(side * side) { index ->
            if (index % side in 18..29 && index / side in 18..29) red else white
        }
        assertEquals(red, IconPixels.lightRimTint(tile, side, side))
        // All white: nothing to take a colour from, so a quiet grey.
        val blank = IntArray(side * side) { white }
        val grey = assertNotNull(IconPixels.lightRimTint(blank, side, side))
        assertEquals(grey shr 16 and 0xFF, 0x6B)
        // A dark tile keeps the bright line it already shows.
        val dark = IntArray(side * side) { (0xFF shl 24) or 0x101820 }
        assertNull(IconPixels.lightRimTint(dark, side, side))
    }

    @Test
    fun aPictureDrawnToEveryEdgeFillsIt() {
        val pixels = picture(20) { _, _ -> true }
        val box = assertNotNull(IconPixels.artBounds(pixels, 20, 20))
        assertTrue(IconPixels.fillsPicture(box, 20, 20))
        assertEquals(1f, IconPixels.coverage(pixels, 20, 20))
        assertEquals(0f, IconPixels.coverage(IntArray(400), 20, 20))
    }

    @Test
    fun theBodyOfAnIconLeavesItsShadowOut() {
        val side = 20
        // A solid tile with a faint shadow one pixel wide around it.
        val pixels = IntArray(side * side) { index ->
            val x = index % side
            val y = index / side
            when {
                x in 4..15 && y in 4..15 -> solid(30, 60, 90)
                x in 3..16 && y in 3..16 -> (60 shl 24)
                else -> 0
            }
        }
        assertEquals(PixelBox(3, 3, 17, 17), IconPixels.artBounds(pixels, side, side))
        assertEquals(PixelBox(4, 4, 16, 16), IconPixels.bodyBounds(pixels, side, side))
        assertNull(IconPixels.bodyBounds(IntArray(side * side), side, side))
    }

    @Test
    fun thePlateTakesTheColourOfTheRimNotOfTheDrawing() {
        val side = 48
        // A blue disc with a white bar across it that reaches the rim on both sides.
        val pixels = IntArray(side * side) { index ->
            val x = index % side
            val y = index / side
            val across = x + 0.5f - side / 2f
            val down = y + 0.5f - side / 2f
            when {
                across * across + down * down > 20f * 20f -> 0
                y in 22..25 -> solid(255, 255, 255)
                else -> solid(20, 40, 200)
            }
        }
        val body = assertNotNull(IconPixels.bodyBounds(pixels, side, side))
        assertEquals(solid(20, 40, 200), IconPixels.rimColor(pixels, side, body))

        // A thin ring has too little on the rim that is looked at to give a colour.
        val ring = IntArray(side * side) { index ->
            val across = index % side + 0.5f - side / 2f
            val down = index / side + 0.5f - side / 2f
            if (across * across + down * down in (19f * 19f)..(20f * 20f)) solid(9, 9, 9) else 0
        }
        val ringBody = assertNotNull(IconPixels.bodyBounds(ring, side, side))
        assertNull(IconPixels.rimColor(ring, side, ringBody))
    }
}
