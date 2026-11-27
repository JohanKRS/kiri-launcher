package io.github.johankrs.kiri.core.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvHomeButtonTest {
    private val kiri = "io.github.johankrs.kiri"
    private val kiriHome = HomeActivity(kiri, "$kiri.MainActivity")
    private val launcherx = "com.google.android.apps.tv.launcherx"

    // What a Google TV (a Hisense on Android 11) lists for the Home button.
    private val googleTv = listOf(
        HomeActivity(launcherx, "$launcherx.home.HomeActivity", priority = 2),
        HomeActivity("com.google.android.tungsten.setupwraith", "com.google.android.tungsten.setupwraith.RecoveryActivity", priority = 1),
        HomeActivity("com.google.android.apps.tv.launcherx.fallbackhome", "x.FallbackHome", priority = 0),
        kiriHome,
        HomeActivity("com.android.tv.settings", "com.android.tv.settings.FallbackHome", priority = -1000),
    )

    @Test
    fun `a home screen with a higher priority can only be taken over`() {
        assertEquals(HomeRoute.TAKE_OVER, TvHomeButton.route(googleTv, launcherx, kiri))
        val other = TvHomeButton.otherHome(googleTv, launcherx, kiri)
        assertEquals(OtherHome(launcherx, setOf("$launcherx.home.HomeActivity")), other)
    }

    @Test
    fun `where all are equal Android is asked`() {
        val equal = listOf(HomeActivity("tv.vendor", "tv.vendor.Home"), kiriHome)
        assertEquals(HomeRoute.ASK_ANDROID, TvHomeButton.route(equal, "tv.vendor", kiri))
        assertEquals(HomeRoute.ASK_ANDROID, TvHomeButton.route(equal, TvHomeButton.CHOOSER, kiri))
        assertEquals(HomeRoute.ASK_ANDROID, TvHomeButton.route(equal, null, kiri))
        // Lower ones do not stand in the way.
        val lower = equal + HomeActivity("com.android.tv.settings", "Fallback", priority = -1000)
        assertEquals(HomeRoute.ASK_ANDROID, TvHomeButton.route(lower, "tv.vendor", kiri))
    }

    @Test
    fun `kiri on the Home button needs nothing`() {
        assertEquals(HomeRoute.KIRI, TvHomeButton.route(googleTv, kiri, kiri))
        assertNull(TvHomeButton.otherHome(googleTv, kiri, kiri))
    }

    @Test
    fun `nothing is watched while Android would ask`() {
        assertNull(TvHomeButton.otherHome(googleTv, TvHomeButton.CHOOSER, kiri))
        assertNull(TvHomeButton.otherHome(googleTv, null, kiri))
        assertNull(TvHomeButton.otherHome(googleTv, "gone.pkg", kiri))
    }

    @Test
    fun `only the home activities of the other app count, aliases by their target too`() {
        val aliased = listOf(HomeActivity("tv.vendor", "tv.vendor.HomeAlias", targetName = "tv.vendor.Launcher"), kiriHome)
        val other = TvHomeButton.otherHome(aliased, "tv.vendor", kiri)!!
        assertTrue(other.isShowing("tv.vendor", "tv.vendor.Launcher"))
        assertTrue(other.isShowing("tv.vendor", "tv.vendor.HomeAlias"))
        assertFalse(other.isShowing("tv.vendor", "tv.vendor.SettingsPanel"))
        assertFalse(other.isShowing("tv.vendor", "android.widget.FrameLayout"))
        assertFalse(other.isShowing("other.app", "tv.vendor.Launcher"))
        assertFalse(other.isShowing("tv.vendor", null))
    }

    @Test
    fun `kiri comes back once for a home screen that shows twice`() {
        assertTrue(TvHomeButton.comesBack(now = 1_000, last = null))
        assertFalse(TvHomeButton.comesBack(now = 1_100, last = 1_000))
        assertTrue(TvHomeButton.comesBack(now = 1_000 + TvHomeButton.GAP_MS, last = 1_000))
        // A clock that went back (a restart) never blocks.
        assertTrue(TvHomeButton.comesBack(now = 50, last = 1_000))
    }

    @Test
    fun `one tuner per service and per name`() {
        val hisense = listOf(
            TunerSeen("com.hisense.tv.hitvinput/.tuner.TunerTvInputService/HW0", "com.hisense.tv.hitvinput/.tuner.TunerTvInputService", "Tuner"),
            TunerSeen("com.hisense.tv.hitvinput/.tuner.TunerTvInputService/HW6", "com.hisense.tv.hitvinput/.tuner.TunerTvInputService", "Tuner"),
        )
        assertEquals(setOf("com.hisense.tv.hitvinput/.tuner.TunerTvInputService/HW0"), TvTuners.kept(hisense))
        val twoKinds = listOf(
            TunerSeen("a/HW0", "a", "Antenna"),
            TunerSeen("b/HW1", "b", "Cable"),
            TunerSeen("c/HW2", "c", "antenna "),
        )
        assertEquals(setOf("a/HW0", "b/HW1"), TvTuners.kept(twoKinds))
        assertEquals(emptySet(), TvTuners.kept(emptyList()))
    }
}
