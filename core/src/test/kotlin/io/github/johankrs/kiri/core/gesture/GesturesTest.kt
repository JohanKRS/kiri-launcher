package io.github.johankrs.kiri.core.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GesturesTest {
    @Test
    fun `untouched gestures do what a new user expects`() {
        val map = GestureMap()

        assertEquals(LauncherAction.OpenDrawer, map.actionFor(Gesture.SWIPE_UP))
        assertEquals(LauncherAction.OpenNotifications, map.actionFor(Gesture.SWIPE_DOWN))
        assertEquals(LauncherAction.None, map.actionFor(Gesture.DOUBLE_TAP))
        assertEquals(LauncherAction.OpenHomeMenu, map.actionFor(Gesture.LONG_PRESS))
        assertTrue(map.settingsReachable)
    }

    @Test
    fun `changing one gesture leaves the others alone`() {
        val map = GestureMap().with(Gesture.DOUBLE_TAP, LauncherAction.OpenDrawer)

        assertEquals(LauncherAction.OpenDrawer, map.actionFor(Gesture.DOUBLE_TAP))
        assertEquals(LauncherAction.OpenDrawer, map.actionFor(Gesture.SWIPE_UP))
        assertEquals(LauncherAction.OpenHomeMenu, map.actionFor(Gesture.LONG_PRESS))
    }

    @Test
    fun `the settings can never become unreachable`() {
        val locked = GestureMap().with(Gesture.LONG_PRESS, LauncherAction.None)

        assertEquals(false, locked.settingsReachable)
        assertEquals(LauncherAction.None, locked.chosenFor(Gesture.LONG_PRESS))
        assertEquals(LauncherAction.OpenSettings, locked.actionFor(Gesture.LONG_PRESS))

        val moved = locked.with(Gesture.DOUBLE_TAP, LauncherAction.OpenSettings)
        assertTrue(moved.settingsReachable)
        assertEquals(LauncherAction.None, moved.actionFor(Gesture.LONG_PRESS))
        assertEquals(LauncherAction.OpenSettings, moved.actionFor(Gesture.DOUBLE_TAP))
    }

    @Test
    fun `actions survive being saved by id`() {
        val all = LauncherAction.simple + LauncherAction.LaunchApp("pkg.a/Main")

        for (action in all) assertEquals(action, LauncherAction.fromId(action.id))
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertNull(LauncherAction.fromId(null))
        assertNull(LauncherAction.fromId("from the future"))
        assertNull(LauncherAction.fromId("app:"))
    }

    @Test
    fun `a double tap can lock the screen`() {
        val stored = mapOf("gesture.doubleTap" to "lock", "gesture.pinch" to "pages")

        val map = GestureMap.fromStored(stored::get)

        assertEquals(LauncherAction.LockScreen, map.actionFor(Gesture.DOUBLE_TAP))
        assertTrue(LauncherAction.LockScreen in LauncherAction.simple)
        // A gesture this version no longer has is left alone.
        assertEquals(GestureMap().with(Gesture.DOUBLE_TAP, LauncherAction.LockScreen), map)
    }

    @Test
    fun `stored gestures are read back and unknown ones fall back to the default`() {
        val stored = mapOf(
            "gesture.swipeDown" to "drawer",
            "gesture.doubleTap" to "app:pkg.a/Main",
            "gesture.swipeUp" to "from the future",
        )

        val map = GestureMap.fromStored(stored::get)

        assertEquals(LauncherAction.OpenDrawer, map.chosenFor(Gesture.SWIPE_DOWN))
        assertEquals(LauncherAction.LaunchApp("pkg.a/Main"), map.chosenFor(Gesture.DOUBLE_TAP))
        assertEquals(LauncherAction.OpenDrawer, map.chosenFor(Gesture.SWIPE_UP))
        assertEquals(LauncherAction.OpenHomeMenu, map.chosenFor(Gesture.LONG_PRESS))
        assertEquals(Gesture.entries.size, Gesture.entries.map { GestureMap.storageKey(it) }.distinct().size)
    }

    @Test
    fun `the menu of the home screen counts as a way into the settings`() {
        val map = GestureMap()
            .with(Gesture.LONG_PRESS, LauncherAction.OpenWidgets)
            .with(Gesture.DOUBLE_TAP, LauncherAction.OpenHomeMenu)

        assertTrue(map.settingsReachable)
        assertEquals(LauncherAction.OpenWidgets, map.actionFor(Gesture.LONG_PRESS))

        // Widgets alone are not: holding then opens the settings again.
        val locked = GestureMap().with(Gesture.LONG_PRESS, LauncherAction.OpenWidgets)
        assertEquals(false, locked.settingsReachable)
        assertEquals(LauncherAction.OpenSettings, locked.actionFor(Gesture.LONG_PRESS))
    }
}
