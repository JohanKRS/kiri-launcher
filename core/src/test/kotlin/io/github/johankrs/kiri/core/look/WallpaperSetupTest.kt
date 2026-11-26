package io.github.johankrs.kiri.core.look

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WallpaperSetupTest {
    @Test
    fun aSetupSurvivesBeingSaved() {
        val setup = WallpaperSetup(
            zoomPercent = 135,
            panX = -400,
            panY = 250,
            dimPercent = 20,
            blurDp = 8,
            scrollPercent = 60,
            target = WallpaperTarget.HOME,
        )
        assertEquals(setup, WallpaperSetup.fromStored(setup.stored))
    }

    @Test
    fun brokenOrMissingTextGivesTheDefault() {
        assertEquals(WallpaperSetup(), WallpaperSetup.fromStored(null))
        assertEquals(WallpaperSetup(), WallpaperSetup.fromStored("1,2,3"))
        assertEquals(WallpaperSetup(), WallpaperSetup.fromStored("a,0,0,0,0,0,both"))
    }

    @Test
    fun savedValuesAreKeptInsideTheirRanges() {
        val setup = WallpaperSetup.fromStored("9000,-5000,5000,900,900,900,lock")
        assertEquals(WallpaperSetup.ZOOM_PERCENT.last, setup.zoomPercent)
        assertEquals(-1000, setup.panX)
        assertEquals(1000, setup.panY)
        assertEquals(WallpaperSetup.DIM_PERCENT.last, setup.dimPercent)
        assertEquals(WallpaperSetup.BLUR_DP.last, setup.blurDp)
        assertEquals(WallpaperSetup.SCROLL_PERCENT.last, setup.scrollPercent)
        assertEquals(WallpaperTarget.LOCK, setup.target)
    }

    @Test
    fun aPictureOfTheSameProportionsIsShownWhole() {
        val frame = WallpaperFraming.frame(2160, 4680, 1080, 2340, zoomPercent = 100, panX = 0, panY = 0)
        assertEquals(PictureFrame(0f, 0f, 2160f, 4680f), frame)
    }

    @Test
    fun aWidePictureIsCutAtTheSidesAndPanSlidesOverTheRest() {
        // 4000 x 2000 on a tall screen of 1000 x 2000: a strip 1000 wide is seen.
        val centred = WallpaperFraming.frame(4000, 2000, 1000, 2000, 100, 0, 0)
        assertEquals(1500f, centred.left)
        assertEquals(2500f, centred.right)
        assertEquals(0f, centred.top)
        assertEquals(2000f, centred.bottom)
        assertEquals(0f, WallpaperFraming.frame(4000, 2000, 1000, 2000, 100, -1000, 0).left)
        assertEquals(4000f, WallpaperFraming.frame(4000, 2000, 1000, 2000, 100, 1000, 0).right)
    }

    @Test
    fun zoomComesCloserAndTheFrameNeverLeavesThePicture() {
        for (zoom in listOf(100, 150, 300)) {
            for (pan in listOf(-1000, -300, 0, 700, 1000)) {
                val frame = WallpaperFraming.frame(3000, 2000, 1080, 2340, zoom, pan, -pan)
                assertTrue(frame.left >= -0.01f && frame.top >= -0.01f, "zoom $zoom pan $pan: $frame")
                assertTrue(frame.right <= 3000.01f && frame.bottom <= 2000.01f, "zoom $zoom pan $pan: $frame")
                // Always the proportions of the wallpaper.
                assertEquals(1080f / 2340f, frame.width / frame.height, 0.0005f)
            }
        }
        val near = WallpaperFraming.frame(3000, 2000, 1080, 2340, 200, 0, 0)
        val far = WallpaperFraming.frame(3000, 2000, 1080, 2340, 100, 0, 0)
        assertEquals(far.width / 2f, near.width, 0.01f)
    }

    @Test
    fun aWallpaperThatTravelsIsWiderThanTheScreen() {
        assertEquals(1080, WallpaperFraming.wallpaperWidth(1080, 0))
        assertEquals(1620, WallpaperFraming.wallpaperWidth(1080, 100))
        assertEquals(1350, WallpaperFraming.wallpaperWidth(1080, 50))
    }

    @Test
    fun draggingThePictureMovesTheFrameTheOtherWay() {
        // The frame covers half the picture: dragging the picture right by half a frame moves
        // the frame left by a quarter of the picture, which is all the free room on that side.
        assertEquals(-1000, WallpaperFraming.panAfterDrag(0, dragged = 0.5f, frameShare = 0.5f))
        assertEquals(500, WallpaperFraming.panAfterDrag(0, dragged = -0.25f, frameShare = 0.5f))
        // Nothing to slide over: the pan stays in the middle.
        assertEquals(0, WallpaperFraming.panAfterDrag(300, dragged = 0.4f, frameShare = 1f))
    }
}

class LabelLookTest {
    @Test
    fun whatWasSavedIsReadBackAndKeptInRange() {
        val strings = mapOf(LabelLook.KEY_WEIGHT to "light")
        val look = LabelLook.fromStored(strings::get) { key -> if (key == LabelLook.KEY_SIZE) 40 else null }
        assertEquals(LabelLook(LabelLook.SIZE_SP.last, LabelWeight.LIGHT), look)
    }

    @Test
    fun nothingSavedGivesTheDefault() {
        assertEquals(LabelLook(), LabelLook.fromStored({ null }, { null }))
        assertEquals(LabelLook(), LabelLook.fromStored({ "unknown" }, { null }))
    }
}
