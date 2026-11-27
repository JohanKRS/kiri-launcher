package io.github.johankrs.kiri.core

import io.github.johankrs.kiri.core.backup.StoredValue
import io.github.johankrs.kiri.core.gesture.Gesture
import io.github.johankrs.kiri.core.gesture.GestureMap
import io.github.johankrs.kiri.core.gesture.LauncherAction
import io.github.johankrs.kiri.core.layout.CellPosition
import io.github.johankrs.kiri.core.layout.GridSize
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.HomeLayoutCodec
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.folder
import io.github.johankrs.kiri.core.layout.mergeApp
import io.github.johankrs.kiri.core.layout.withAppsMoved
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.core.tv.TvBanner
import io.github.johankrs.kiri.core.tv.TvHomeItems
import io.github.johankrs.kiri.core.tv.TvItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppMovesTest {
    private val grid = GridSize(columns = 4, rows = 3)

    // An app with three icons to choose from, each an activity-alias of its own.
    private val blue = "com.bank/com.bank.IconBlue"
    private val gold = "com.bank/com.bank.IconGold"
    private val dark = "com.bank/com.bank.IconDark"
    private val notes = "com.notes/com.notes.Main"
    private val camera = "com.camera/com.camera.Main"

    private fun layoutOf(vararg keys: String): HomeLayout =
        keys.fold(HomeLayout()) { layout, key -> layout.add(ItemContent.App(key), grid) }

    private fun HomeLayout.idOf(key: String): Long = items.single { it.content == ItemContent.App(key) }.id

    private fun text(value: String) = StoredValue.Text(value)

    private fun layoutIn(settings: Map<String, StoredValue>, name: String = "home.phone.portrait.layout"): HomeLayout =
        HomeLayoutCodec.decode((settings[name] as StoredValue.Text).value)!!

    @Test
    fun `an app that turned to another of its icons is found under its new key`() {
        val moves = AppMoves.find(saved = listOf(notes, blue), installed = setOf(notes, gold, camera))
        assertEquals(mapOf(blue to gold), moves)
    }

    @Test
    fun `an app that is gone, or in the middle of an update, moves nowhere`() {
        // Nothing of com.bank is listed: uninstalled, or both icons off for a moment.
        assertEquals(emptyMap(), AppMoves.find(saved = listOf(notes, blue), installed = setOf(notes, camera)))
        // Everything is where it was.
        assertEquals(emptyMap(), AppMoves.find(saved = listOf(notes, blue), installed = setOf(notes, blue)))
    }

    @Test
    fun `with several entries, the one nothing mentions yet is the new one`() {
        val settings = "com.bank/com.bank.Pay"
        val moves = AppMoves.find(saved = listOf(blue, settings), installed = setOf(gold, settings))
        assertEquals(mapOf(blue to gold), moves)
    }

    @Test
    fun `with several new entries, the one Android opens for the app wins, and else nothing moves`() {
        val pay = "com.bank/com.bank.Pay"
        val installed = setOf(gold, pay)
        assertEquals(mapOf(blue to gold), AppMoves.find(listOf(blue), installed) { if (it == "com.bank") gold else null })
        assertEquals(emptyMap(), AppMoves.find(listOf(blue), installed) { null })
        // A main entry Android names but that is not a listed one is not taken.
        assertEquals(emptyMap(), AppMoves.find(listOf(blue), installed) { "com.bank/com.bank.Splash" })
    }

    @Test
    fun `the moved app keeps its cell, and the rest stays as it was`() {
        val layout = layoutOf(notes, blue, camera)
        val moved = layout.withAppsMoved(mapOf(blue to gold))

        assertEquals(listOf(notes, gold, camera), moved.items.map { (it.content as ItemContent.App).appKey })
        assertEquals(layout.items.map { it.position }, moved.items.map { it.position })
        assertEquals(layout.items.map { it.id }, moved.items.map { it.id })
        // Nothing of it mentioned: the very same layout.
        assertSame(layout, layout.withAppsMoved(mapOf("com.other/A" to "com.other/B")))
    }

    @Test
    fun `the moved app keeps its place in its folder`() {
        val start = layoutOf(notes, blue, camera)
        val layout = start.mergeApp(blue, start.idOf(notes), "f1", "Banco")
        val moved = layout.withAppsMoved(mapOf(blue to gold))

        assertEquals(listOf(notes, gold), moved.folder("f1")!!.appKeys)
        assertEquals("Banco", moved.folder("f1")!!.name)
    }

    @Test
    fun `an app put back by hand under its new key keeps that place, and the old shortcut goes`() {
        val layout = layoutOf(notes, blue, camera, gold)
        val moved = layout.withAppsMoved(mapOf(blue to gold))

        assertEquals(listOf(notes, camera, gold), moved.items.map { (it.content as ItemContent.App).appKey })
        assertEquals(CellPosition(0, 3, 0), moved.items.last().position)
        // Its page stays, even when the old shortcut was all there was on it.
        assertEquals(layout.pageCount, moved.pageCount)
    }

    @Test
    fun `a folder that loses the old shortcut to the app standing elsewhere becomes its other app`() {
        val start = layoutOf(notes, blue, gold)
        val layout = start.mergeApp(blue, start.idOf(notes), "f1", "Pasta")
        val moved = layout.withAppsMoved(mapOf(blue to gold))

        assertNull(moved.folder("f1"))
        assertEquals(setOf(notes, gold), moved.items.mapTo(HashSet()) { (it.content as ItemContent.App).appKey })
    }

    @Test
    fun `the app keeps everything saved for it, on every home screen, the TV and the gestures`() {
        val portrait = layoutOf(notes, blue)
        val landscape = layoutOf(blue, notes)
        val settings = mapOf(
            "home.phone.portrait.layout" to text(HomeLayoutCodec.encode(portrait)),
            "home.phone.landscape.layout" to text(HomeLayoutCodec.encode(landscape)),
            IconChoice.storageKey(blue) to text(IconChoice("pack.icons", "bank_gold").stored),
            IconLook.appArtKey(blue) to StoredValue.Whole(120),
            IconLook.appGroundKey(blue) to text("glass"),
            AppNames.storageKey(blue) to text("Meu banco"),
            AppNames.storageKey(notes) to text("Notas"),
            HiddenApps.storageKey(camera) to StoredValue.Flag(true),
            GestureMap.storageKey(Gesture.DOUBLE_TAP) to text(LauncherAction.LaunchApp(blue).id),
            GestureMap.storageKey(Gesture.SWIPE_UP) to text(LauncherAction.OpenDrawer.id),
            TvHomeItems.KEY to text(TvHomeItems.encode(TvHomeItems(listOf(TvItem.Input("hdmi1"), TvItem.App(blue))))),
            TvBanner.storageKey(TvItem.App(blue).key) to text("drawn:play:blue"),
            "icons.shape" to text("squircle"),
        )
        val saved = AppMoves.appKeysIn(settings)
        assertEquals(setOf(notes, blue, camera), saved)
        val moves = AppMoves.find(saved, installed = setOf(notes, gold, camera))
        val moved = AppMoves.applied(settings, moves)

        assertEquals(listOf(notes, gold), layoutIn(moved).items.map { (it.content as ItemContent.App).appKey })
        assertEquals(
            listOf(gold, notes),
            layoutIn(moved, "home.phone.landscape.layout").items.map { (it.content as ItemContent.App).appKey },
        )
        assertEquals(text(IconChoice("pack.icons", "bank_gold").stored), moved[IconChoice.storageKey(gold)])
        assertEquals(StoredValue.Whole(120), moved[IconLook.appArtKey(gold)])
        assertEquals(text("glass"), moved[IconLook.appGroundKey(gold)])
        assertEquals(text("Meu banco"), moved[AppNames.storageKey(gold)])
        assertEquals(text(LauncherAction.LaunchApp(gold).id), moved[GestureMap.storageKey(Gesture.DOUBLE_TAP)])
        assertEquals(
            listOf(TvItem.Input("hdmi1"), TvItem.App(gold)),
            TvHomeItems.decode((moved[TvHomeItems.KEY] as StoredValue.Text).value)!!.items,
        )
        assertEquals(text("drawn:play:blue"), moved[TvBanner.storageKey(TvItem.App(gold).key)])
        // Nothing is left under the old key, and nothing else changed.
        assertTrue(moved.keys.none { blue in it })
        assertEquals(text("Notas"), moved[AppNames.storageKey(notes)])
        assertEquals(StoredValue.Flag(true), moved[HiddenApps.storageKey(camera)])
        assertEquals(text(LauncherAction.OpenDrawer.id), moved[GestureMap.storageKey(Gesture.SWIPE_UP)])
        assertEquals(text("squircle"), moved["icons.shape"])
        assertEquals(settings.size, moved.size)
    }

    @Test
    fun `a hidden app stays hidden under its new key`() {
        val settings = mapOf(HiddenApps.storageKey(blue) to StoredValue.Flag(true))
        val moved = AppMoves.applied(settings, AppMoves.find(AppMoves.appKeysIn(settings), setOf(gold)))
        assertEquals(mapOf(HiddenApps.storageKey(gold) to StoredValue.Flag(true)), moved)
    }

    @Test
    fun `what the new key already has wins over the old one's`() {
        val settings = mapOf(
            AppNames.storageKey(blue) to text("Antigo"),
            AppNames.storageKey(gold) to text("Novo"),
        )
        val moved = AppMoves.applied(settings, mapOf(blue to gold))
        assertEquals(mapOf(AppNames.storageKey(gold) to text("Novo")), moved)
    }

    @Test
    fun `switching back and forth always ends where the app is`() {
        val start = mapOf("home.phone.portrait.layout" to text(HomeLayoutCodec.encode(layoutOf(notes, blue))))
        var settings: Map<String, StoredValue> = start
        for (now in listOf(gold, dark, blue)) {
            val installed = setOf(notes, now)
            settings = AppMoves.applied(settings, AppMoves.find(AppMoves.appKeysIn(settings), installed))
            assertEquals(listOf(notes, now), layoutIn(settings).items.map { (it.content as ItemContent.App).appKey })
        }
        assertEquals(layoutIn(start), layoutIn(settings))
    }
}
