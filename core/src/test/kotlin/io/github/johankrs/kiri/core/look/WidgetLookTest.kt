package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetLookTest {
    @Test
    fun `a widget nobody touched is shown as its app made it`() {
        val look = WidgetLook.AsMade

        assertNull(look.glass)
        assertFalse(look.hidesBackground)
        assertFalse(look.recolours)
        assertFalse(look.changesFont)
        assertEquals(0, look.marginDp)
    }

    @Test
    fun `one step of glass dresses the whole widget`() {
        val look = WidgetLook.AsMade.withGlassStepped(1)!!

        assertEquals(GlassStyle.FUME, look.glass)
        assertTrue(look.hidesBackground)
        assertTrue(look.recolours)
        assertTrue(look.changesFont)
    }

    @Test
    fun `the glass steps from none through every style and stops at both ends`() {
        var look = WidgetLook.AsMade
        val seen = mutableListOf(look.glass)
        while (true) {
            look = look.withGlassStepped(1) ?: break
            seen += look.glass
        }

        assertEquals(listOf(null) + GlassStyle.entries, seen)
        assertNull(WidgetLook.AsMade.withGlassStepped(-1))
        assertNull(look.withGlassStepped(1))
    }

    @Test
    fun `taking the glass away keeps the other choices for when it comes back`() {
        val chosen = WidgetLook(glass = GlassStyle.NEVOA, ownBackground = true, kiriFont = false, marginLevel = 3)
        val bare = chosen.copy(glass = null)

        assertFalse(bare.recolours)
        assertEquals(0, bare.marginDp)
        assertEquals(chosen, bare.copy(glass = GlassStyle.NEVOA))
        assertEquals(12, chosen.marginDp)
    }

    @Test
    fun `a widget that keeps its own background keeps its own colours`() {
        val look = WidgetLook(glass = GlassStyle.FUME, ownBackground = true, kiriColors = true)

        assertFalse(look.hidesBackground)
        assertFalse(look.recolours)
        assertTrue(look.changesFont)
        assertTrue(look.copy(ownBackground = false).recolours)
    }

    @Test
    fun `the margin stays within its levels`() {
        assertNull(WidgetLook.AsMade.withMarginStepped(-1))
        assertEquals(1, WidgetLook.AsMade.withMarginStepped(1)?.marginLevel)
        assertNull(WidgetLook(marginLevel = WidgetLook.MARGIN_LEVELS.last).withMarginStepped(1))
    }

    @Test
    fun `a look survives being saved`() {
        val looks = listOf(
            WidgetLook.AsMade,
            WidgetLook(glass = GlassStyle.FUME),
            WidgetLook(glass = GlassStyle.CRISTAL, ownBackground = true, kiriColors = false, kiriFont = false, marginLevel = 4),
            WidgetLook(glass = null, ownBackground = true, marginLevel = 2),
        )

        for (look in looks) assertEquals(look, WidgetLook.decode(look.encode()))
    }

    @Test
    fun `what cannot be read falls back to a widget as its app made it`() {
        assertEquals(WidgetLook.AsMade, WidgetLook.decode(null))
        assertEquals(WidgetLook.AsMade, WidgetLook.decode(""))
        assertEquals(WidgetLook.AsMade, WidgetLook.decode("nonsense"))
        assertEquals(WidgetLook(glass = GlassStyle.FUME), WidgetLook.decode("glass=fume;from=the future;margin=many"))
        assertEquals(WidgetLook.AsMade, WidgetLook.decode("glass=unheard of"))
    }

    @Test
    fun `the look of each copy has a place of its own`() {
        assertEquals(42, WidgetLook.appWidgetIdOf(WidgetLook.storageKey(42)))
        assertNull(WidgetLook.appWidgetIdOf("gesture.swipeUp"))
        assertNull(WidgetLook.appWidgetIdOf("appwidget.x.look"))
        assertNull(WidgetLook.appWidgetIdOf("phone.portrait.layout"))
    }

    @Test
    fun `tones run from white to black and swap ends when inverted`() {
        val natural = WidgetTones.SHADES.map { WidgetTones.lightness(it, inverted = false) }
        val inverted = WidgetTones.SHADES.map { WidgetTones.lightness(it, inverted = true) }

        assertEquals(100, natural.first())
        assertEquals(0, natural.last())
        assertEquals(natural.sortedDescending(), natural)
        assertEquals(natural.map { 100 - it }, inverted)
        assertEquals(natural.size, natural.distinct().size)
    }

    @Test
    fun `tones are swapped only when glass and theme disagree`() {
        assertFalse(WidgetTones.inverted(darkGlass = true, darkTheme = true))
        assertFalse(WidgetTones.inverted(darkGlass = false, darkTheme = false))
        assertTrue(WidgetTones.inverted(darkGlass = true, darkTheme = false))
        assertTrue(WidgetTones.inverted(darkGlass = false, darkTheme = true))
    }
}
