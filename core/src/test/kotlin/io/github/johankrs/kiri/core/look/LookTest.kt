package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LookTest {
    @Test
    fun `nothing saved gives the defaults and unknown values are ignored`() {
        assertEquals(MotionSettings(), MotionSettings.fromStored({ null }, { null }))
        assertEquals(DrawerLook(), DrawerLook.fromStored({ null }, { null }))

        val odd = MotionSettings.fromStored({ "sideways" }, { 99 })
        assertEquals(MotionSettings().appOpen, odd.appOpen)
        assertEquals(5, odd.speed)
    }

    @Test
    fun `saved choices are read back`() {
        val strings = mapOf(
            MotionSettings.KEY_APP_OPEN to AppOpenStyle.FADE.key,
            MotionSettings.KEY_HOME_RETURN to HomeReturnStyle.NONE.key,
            MotionSettings.KEY_DRAWER to DrawerStyle.ZOOM.key,
            DrawerLook.KEY_GLASS to GlassStyle.NEVOA.key,
        )
        val ints = mapOf(MotionSettings.KEY_SPEED to 4, DrawerLook.KEY_OPACITY to 30, DrawerLook.KEY_BLUR to 12)

        assertEquals(
            MotionSettings(AppOpenStyle.FADE, HomeReturnStyle.NONE, DrawerStyle.ZOOM, speed = 4),
            MotionSettings.fromStored(strings::get, ints::get),
        )
        assertEquals(DrawerLook(GlassStyle.NEVOA, 30, 12), DrawerLook.fromStored(strings::get, ints::get))
    }

    @Test
    fun `a faster speed means stiffer springs and shorter durations`() {
        val speeds = MotionSettings.SPEED.map { MotionSettings(speed = it) }
        assertEquals(speeds.map { it.stiffnessScale }.sorted(), speeds.map { it.stiffnessScale })
        assertEquals(1f, MotionSettings().stiffnessScale)
        assertTrue(MotionSettings(speed = 5).durationScale < 1f)
        assertTrue(MotionSettings(speed = 1).durationScale > 1f)
    }

    @Test
    fun `the look of the drawer stays inside its limits`() {
        assertEquals(95, DrawerLook().withOpacity(200).opacityPercent)
        assertEquals(0, DrawerLook().withBlur(-8).blurDp)
    }

    @Test
    fun `an icon choice survives being saved and belongs to its app`() {
        val choice = IconChoice(pack = "com.pack.icons", drawable = "camera_alt")
        assertEquals(choice, IconChoice.fromStored(choice.stored))
        assertNull(IconChoice.fromStored("just one part"))
        assertNull(IconChoice.fromStored(null))

        val key = IconChoice.storageKey("pkg.a/pkg.a.Main")
        assertEquals("pkg.a/pkg.a.Main", IconChoice.appKeyOf(key))
        assertNull(IconChoice.appKeyOf("motion.speed"))
    }
}
