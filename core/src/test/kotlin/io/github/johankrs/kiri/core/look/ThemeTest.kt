package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.backup.BackupPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeTest {
    @Test
    fun `a saved theme is read back and an unknown one is not`() {
        Theme.entries.forEach { assertEquals(it, Theme.fromKey(it.key)) }
        assertNull(Theme.fromKey("sepia"))
        assertNull(Theme.fromKey(null))
    }

    @Test
    fun `with nothing chosen a phone follows the device and a TV is dark`() {
        assertEquals(Theme.SYSTEM, Theme.default(tv = false))
        assertEquals(Theme.DARK, Theme.default(tv = true))
    }

    @Test
    fun `light and dark ignore the device, and system follows it`() {
        assertTrue(Theme.SYSTEM.isDark(systemDark = true))
        assertFalse(Theme.SYSTEM.isDark(systemDark = false))
        assertFalse(Theme.LIGHT.isDark(systemDark = true))
        assertTrue(Theme.DARK.isDark(systemDark = false))
    }

    @Test
    fun `the theme is saved with the rest of the look`() {
        assertEquals(BackupPart.LOOK, BackupPart.of(Theme.KEY))
    }
}
