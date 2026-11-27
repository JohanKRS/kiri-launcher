package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.backup.BackupPart
import io.github.johankrs.kiri.core.tv.TvLook
import io.github.johankrs.kiri.core.tv.TvWallpaper
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveWallpaperTest {
    @Test
    fun `a live look is kept and read back, and what is unknown falls back`() {
        for (scene in LiveScene.entries) {
            for (color in LiveColor.entries) {
                for (motion in LiveMotion.entries) {
                    val look = LiveLook(scene, color, motion)
                    assertEquals(look, LiveLook.fromStored(look.stored))
                }
            }
        }
        assertEquals(LiveLook(), LiveLook.fromStored(null))
        assertEquals(LiveLook(), LiveLook.fromStored(""))
        assertEquals(LiveLook(LiveScene.SERRA), LiveLook.fromStored("serra,?"))
        assertEquals(LiveLook(color = LiveColor.ROSE, motion = LiveMotion.STILL), LiveLook.fromStored("gone,rose,still"))
    }

    @Test
    fun `keys are unique and every scene is drawn smaller than the screen`() {
        assertEquals(LiveScene.entries.size, LiveScene.entries.map { it.key }.toSet().size)
        assertEquals(LiveColor.entries.size, LiveColor.entries.map { it.key }.toSet().size)
        for (scene in LiveScene.entries) assertTrue(scene.share > 0f && scene.share <= 0.5f, scene.key)
        // Only the pale one asks for dark writing.
        assertEquals(listOf(LiveColor.MIST), LiveColor.entries.filter { it.light })
        assertEquals(0f, LiveMotion.STILL.speed)
    }

    @Test
    fun `the TV starts on the living aurora, and a still wallpaper has no live look`() {
        val fresh = TvLook.fromStored({ null }, { null })
        assertEquals(TvWallpaper.LIVE_AURORA, fresh.wallpaper)
        assertEquals(LiveLook(LiveScene.AURORA, LiveColor.LILAC, LiveMotion.NORMAL), fresh.live)
        assertEquals(TvWallpaper.LIVE_AURORA, TvLook().wallpaper)

        val stored = mapOf(
            TvLook.KEY_WALLPAPER to "live_flores",
            TvLook.KEY_LIVE_COLOR to "blue",
            TvLook.KEY_LIVE_MOTION to "slow",
        )
        val flowers = TvLook.fromStored({ stored[it] }, { null })
        assertEquals(LiveLook(LiveScene.FLORES, LiveColor.BLUE, LiveMotion.SLOW), flowers.live)
        assertNull(TvLook(wallpaper = TvWallpaper.OCEANO).live)
        // A person who chose a still one before keeps it; the old default, the still aurora, becomes the new one.
        fun wallpaperOf(key: String) = TvLook.fromStored({ if (it == TvLook.KEY_WALLPAPER) key else null }, { null }).wallpaper
        assertEquals(TvWallpaper.GRAFITE, wallpaperOf("grafite"))
        assertEquals(TvWallpaper.LIVE_AURORA, wallpaperOf("aurora"))
        assertEquals(TvWallpaper.AURORA, wallpaperOf(TvWallpaper.AURORA.key))
        assertEquals(TvWallpaper.entries.size, TvWallpaper.entries.map { it.key }.toSet().size)
        assertTrue(TvWallpaper.entries.all { TvWallpaper.fromKey(it.key) == it })
        // Every live scene has its wallpaper on the TV.
        assertEquals(LiveScene.entries.toSet(), TvWallpaper.entries.mapNotNull { it.live }.toSet())
    }

    @Test
    fun `the live wallpaper goes with the wallpaper in a backup, the TV's with its home`() {
        assertEquals(BackupPart.WALLPAPER, BackupPart.of(LiveLook.KEY))
        assertEquals(BackupPart.HOME, BackupPart.of(TvLook.KEY_LIVE_COLOR))
        assertEquals(BackupPart.HOME, BackupPart.of(TvLook.KEY_LIVE_MOTION))
    }

    @Test
    fun `noise fills 0 to 1, is the same each time and has no seam`() {
        val size = 64
        val noise = LiveShapes.noise(size, seed = 7)
        assertEquals(size * size, noise.size)
        assertEquals(0f, noise.min())
        assertEquals(1f, noise.max())
        assertContentEquals(noise, LiveShapes.noise(size, seed = 7))
        assertTrue(!noise.contentEquals(LiveShapes.noise(size, seed = 8)))
        // The last column runs into the first as any column into the next, and so do the rows.
        var step = 0f
        var across = 0f
        var down = 0f
        for (i in 0 until size) {
            for (j in 0 until size - 1) {
                step = maxOf(step, abs(noise[i * size + j + 1] - noise[i * size + j]), abs(noise[(j + 1) * size + i] - noise[j * size + i]))
            }
            across = maxOf(across, abs(noise[i * size + size - 1] - noise[i * size]))
            down = maxOf(down, abs(noise[(size - 1) * size + i] - noise[i]))
        }
        assertTrue(across <= step * 1.01f, "seam across: $across, steps up to $step")
        assertTrue(down <= step * 1.01f, "seam down: $down, steps up to $step")
    }

    @Test
    fun `a band of mist fades at its edges`() {
        val noise = LiveShapes.noise(32, seed = 3)
        val band = LiveShapes.fogBand(noise, 32, 16, 0f, 1f) { along -> 1f - along }
        assertEquals(32 * 16, band.size)
        assertTrue(band.all { it in 0..255 })
        // Its last row is faded out entirely.
        assertTrue((0 until 32).all { band[15 * 32 + it] == 0 })
    }

    @Test
    fun `threads fill the screen in rows, and stay within one period`() {
        val rows = LiveShapes.threadRows(1920f, 1080f)
        assertTrue(rows.size in 30..40, "rows: ${rows.size}")
        assertTrue(rows.zipWithNext().all { (a, b) -> b.y > a.y })
        for (row in rows) {
            assertTrue(row.depth in 0..2)
            assertEquals(0, row.threads.size % 2)
            for (i in row.threads.indices step 2) assertTrue(row.threads[i] + row.threads[i + 1] <= row.period)
        }
        // The same screen, the same threads; a phone gets the same spacing for its shorter side.
        assertEquals(rows.size, LiveShapes.threadRows(1920f, 1080f).size)
        val phone = LiveShapes.threadRows(1080f, 2340f)
        assertTrue(phone.size > rows.size)
        assertTrue(LiveShapes.threadRows(0f, 0f).isEmpty())
    }

    @Test
    fun `the cluster is narrow on top, widest low down and closes at the stem`() {
        val top = LiveShapes.clusterWidth(0f)
        val widest = LiveShapes.clusterWidth(0.72f)
        val stem = LiveShapes.clusterWidth(1f)
        assertTrue(top < widest && stem < widest)
        assertEquals(1f, widest, 0.001f)
        assertTrue(stem >= 0.05f)
        // On a TV it leaves the left to the widgets; on a phone it is centred.
        assertTrue(LiveShapes.cluster(1920f, 1080f).centerX > 1920f / 2f)
        assertEquals(1080f / 2f, LiveShapes.cluster(1080f, 2340f).centerX)
        val outline = LiveShapes.cluster(1920f, 1080f).outline(steps = 10)
        assertEquals(44, outline.size)
    }

    @Test
    fun `hills and flowers are the same every time`() {
        val ridge = LiveShapes.ridge(100, seed = 2)
        assertEquals(100, ridge.size)
        assertEquals(0f, ridge.min())
        assertEquals(1f, ridge.max())
        assertContentEquals(ridge, LiveShapes.ridge(100, seed = 2))
        val petals = LiveShapes.petals()
        assertEquals(18, petals.size)
        assertTrue(petals.zipWithNext().all { (a, b) -> a.near <= b.near })
        assertTrue(petals.all { it.x in 0f..1f && it.y in 0f..1f && (it.turn == 1 || it.turn == -1) })
    }
}
