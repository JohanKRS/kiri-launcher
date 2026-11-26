package io.github.johankrs.kiri.core

import io.github.johankrs.kiri.core.layout.GridSize
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.layout.containsApp
import io.github.johankrs.kiri.core.layout.folder
import io.github.johankrs.kiri.core.layout.mergeApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HiddenAppsTest {
    private fun app(label: String) = AppEntry("pkg.$label", "Main", label)

    @Test
    fun `hidden apps leave the list and the others keep their order`() {
        val apps = listOf(app("Agenda"), app("Banco"), app("Câmera"), app("Notas"))
        val shown = apps.shown(setOf(app("Banco").key, app("Notas").key))

        assertEquals(listOf("Agenda", "Câmera"), shown.map { it.label })
    }

    @Test
    fun `with nothing hidden the very same list is kept`() {
        val apps = listOf(app("Agenda"), app("Banco"))
        assertSame(apps, apps.shown(emptySet()))
    }

    @Test
    fun `hidden apps that are not installed change nothing`() {
        val apps = listOf(app("Agenda"), app("Banco"))
        assertEquals(apps, apps.shown(setOf("gone/Main")))
    }

    @Test
    fun `the list shows every app and apart the hidden ones that are still there`() {
        val apps = listOf(app("Agenda"), app("Banco"), app("Câmera"))
        val list = HiddenAppsList.of(apps, setOf(app("Câmera").key, "gone/Main", app("Agenda").key))

        assertEquals(listOf("Agenda", "Câmera"), list.hidden.map { it.label })
        assertEquals(apps, list.all)
    }

    @Test
    fun `the storage key leads back to the app`() {
        val key = HiddenApps.storageKey("pkg/Main")
        assertEquals("pkg/Main", HiddenApps.appKeyOf(key))
        assertNull(HiddenApps.appKeyOf("name.pkg/Main"))
        assertNull(HiddenApps.appKeyOf("hidden."))
    }

    @Test
    fun `a hidden app leaves the home screen and the folder it was in`() {
        val grid = GridSize(columns = 4, rows = 3)
        val keys = listOf("a", "b", "c").map { "pkg.$it/Main" }
        val layout = keys.fold(HomeLayout()) { home, key -> home.add(ItemContent.App(key), grid) }
        val folderAt = layout.items.single { it.content == ItemContent.App(keys[0]) }.id
        val withFolder = layout.mergeApp(keys[1], folderAt, "f1", "Pasta")

        val hidden = withFolder.without(ItemContent.App(keys[1]))

        assertFalse(hidden.containsApp(keys[1]))
        // A folder left with one app becomes that app, where the folder was.
        assertNull(hidden.folder("f1"))
        assertTrue(hidden.containsApp(keys[0]))
        assertTrue(hidden.containsApp(keys[2]))
    }
}
