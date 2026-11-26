package io.github.johankrs.kiri.core.layout

import io.github.johankrs.kiri.core.look.FolderGround
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoldersTest {
    private val grid = GridSize(columns = 4, rows = 3)

    private fun key(name: String) = "pkg.$name/Main"
    private fun app(name: String) = ItemContent.App(key(name))

    private fun layoutOf(vararg names: String): HomeLayout =
        names.fold(HomeLayout()) { layout, name -> layout.add(app(name), grid) }

    private fun HomeLayout.idOf(name: String): Long = items.single { it.content == app(name) }.id

    private fun HomeLayout.withFolder(vararg names: String, id: String = "f1"): HomeLayout {
        val first = idOf(names[0])
        return names.drop(1).fold(this) { layout, name -> layout.mergeApp(key(name), first, id, "Pasta") }
    }

    @Test
    fun `an app dropped on another makes a folder in the cell of the one underneath`() {
        val layout = layoutOf("a", "b", "c")
        val target = layout.idOf("b")
        val merged = layout.mergeApp(key("c"), target, "f1", "Jogos")

        val folder = assertNotNull(merged.folder("f1"))
        assertEquals("Jogos", folder.name)
        assertEquals(listOf(key("b"), key("c")), folder.appKeys)
        assertEquals(CellPosition(0, 1, 0), merged.folderItem("f1")!!.position)
        assertEquals(2, merged.items.size)
        assertTrue(merged.containsApp(key("c")))
        assertTrue(merged.contains(app("c")))
    }

    @Test
    fun `an app dropped on a folder joins it at the end, and nothing is ever there twice`() {
        val layout = layoutOf("a", "b", "c", "d").withFolder("a", "b")
        val folderId = layout.folderItem("f1")!!.id
        val merged = layout.mergeApp(key("d"), folderId, "f2", "Pasta")

        assertEquals(listOf(key("a"), key("b"), key("d")), merged.folder("f1")!!.appKeys)
        assertNull(merged.items.firstOrNull { it.content == app("d") })
        // Dropping an app that is already inside changes nothing.
        assertEquals(merged, merged.mergeApp(key("a"), folderId, "f3", "Pasta"))
    }

    @Test
    fun `an app moves from one folder to another, and the folder left with one app becomes that app`() {
        val layout = layoutOf("a", "b", "c", "d").withFolder("a", "b").withFolder("c", "d", id = "f2")
        val second = layout.folderItem("f2")!!
        val moved = layout.mergeApp(key("a"), second.id, "f3", "Pasta")

        assertNull(moved.folder("f1"))
        assertEquals(app("b"), moved.items.single { it.position == CellPosition(0, 0, 0) }.content)
        assertEquals(listOf(key("c"), key("d"), key("a")), moved.folder("f2")!!.appKeys)
    }

    @Test
    fun `taking an app out leaves the folder, or turns a folder of two into the other app`() {
        val three = layoutOf("a", "b", "c").withFolder("a", "b", "c")
        val two = three.takeOutOfFolder("f1", key("c"))
        assertEquals(listOf(key("a"), key("b")), two.folder("f1")!!.appKeys)
        assertFalse(two.containsApp(key("c")))

        val one = two.takeOutOfFolder("f1", key("b"))
        assertNull(one.folder("f1"))
        assertEquals(listOf(app("a")), one.items.map { it.content })
    }

    @Test
    fun `removing an app from the home screen also takes it out of its folder`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b", "c")
        val without = layout.without(app("b"))
        assertEquals(listOf(key("a"), key("c")), without.folder("f1")!!.appKeys)
    }

    @Test
    fun `an app inside a folder placed on a free cell comes out of the folder`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b", "c")
        val placed = layout.place(app("c"), CellPosition(0, 3, 2), grid)
        assertEquals(listOf(key("a"), key("b")), placed.folder("f1")!!.appKeys)
        assertEquals(CellPosition(0, 3, 2), placed.items.single { it.content == app("c") }.position)
    }

    @Test
    fun `uninstalled apps leave folders, and a folder of one becomes the app`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b")
        val visible = layout.withoutMissingApps { it != key("b") }
        assertNull(visible.folder("f1"))
        assertEquals(setOf(app("a"), app("c")), visible.items.map { it.content }.toSet())
    }

    @Test
    fun `undoing a folder puts its first app in its cell and the others in free places`() {
        val layout = layoutOf("a", "b", "c", "x").withFolder("a", "b", "c")
        val undone = layout.dissolveFolder("f1", grid)
        assertNull(undone.folder("f1"))
        assertEquals(app("a"), undone.items.single { it.position == CellPosition(0, 0, 0) }.content)
        assertTrue(undone.containsApp(key("b")))
        assertTrue(undone.containsApp(key("c")))
        assertEquals(4, undone.items.size)
    }

    @Test
    fun `a folder taken off the home screen takes its apps along, in both orientations`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b")
        assertEquals(key("a"), layout.folderOf(key("a"))?.appKeys?.first())
        assertNull(layout.folderOf(key("c")))

        val without = layout.withoutFolder("f1")
        assertEquals(listOf(app("c")), without.items.map { it.content })
        assertFalse(without.containsApp(key("a")))

        val other = layoutOf("c", "b", "a").withFolder("b", "a")
        val mirrored = other.mirrorFolders(listOf("f1"), without, grid)
        assertEquals(listOf(app("c")), mirrored.items.map { it.content })
    }

    @Test
    fun `renaming keeps a clean name on one line and never a blank one`() {
        val layout = layoutOf("a", "b").withFolder("a", "b")
        assertEquals("Trabalho e estudo", layout.renameFolder("f1", "  Trabalho\n e\testudo ").folder("f1")!!.name)
        assertEquals("Pasta", layout.renameFolder("f1", "   ").folder("f1")!!.name)
    }

    @Test
    fun `a folder can have its own ground and its own order`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b", "c")
        assertEquals(FolderGround.NONE, layout.setFolderGround("f1", FolderGround.NONE).folder("f1")!!.ground)
        assertEquals(
            listOf(key("c"), key("a"), key("b")),
            layout.reorderFolder("f1", listOf(key("c"), key("a"))).folder("f1")!!.appKeys,
        )
    }

    @Test
    fun `only an app goes into an app or a folder`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b").add(ItemContent.Widget("w"), grid)
        val folderId = layout.folderItem("f1")!!.id
        val widgetId = layout.items.single { it.content is ItemContent.Widget }.id
        assertTrue(layout.canMerge(layout.idOf("c"), folderId))
        assertTrue(layout.canMerge(null, layout.idOf("c")))
        assertFalse(layout.canMerge(folderId, layout.idOf("c")))
        assertFalse(layout.canMerge(layout.idOf("c"), widgetId))
        assertFalse(layout.canMerge(layout.idOf("c"), layout.idOf("c")))
    }

    @Test
    fun `the other orientation follows a new folder, a changed one and an undone one`() {
        val portrait = layoutOf("a", "b", "c", "d")
        val landscape = layoutOf("d", "c", "b", "a")

        val made = portrait.withFolder("a", "b")
        val mirrored = landscape.mirrorFolders(listOf("f1"), made, grid)
        assertEquals(listOf(key("a"), key("b")), mirrored.folder("f1")!!.appKeys)
        assertFalse(mirrored.items.any { it.content == app("a") || it.content == app("b") })
        assertEquals(3, mirrored.items.size)

        // An app taken out of the folder and left on the home screen stays there in both.
        val grown = made.mergeApp(key("c"), made.folderItem("f1")!!.id, "x", "x")
        val out = grown.takeOutOfFolder("f1", key("c")).place(app("c"), CellPosition(0, 3, 2), grid)
        val mirroredOut = mirrored.mirrorFolders(listOf("f1"), grown, grid).mirrorFolders(listOf("f1"), out, grid)
        assertTrue(mirroredOut.items.any { it.content == app("c") })
        assertEquals(listOf(key("a"), key("b")), mirroredOut.folder("f1")!!.appKeys)

        val undone = out.dissolveFolder("f1", grid)
        val mirroredUndone = mirroredOut.mirrorFolders(listOf("f1"), undone, grid)
        assertNull(mirroredUndone.folder("f1"))
        assertTrue(mirroredUndone.containsApp(key("a")) && mirroredUndone.containsApp(key("b")))
    }

    @Test
    fun `folders survive being saved and read back`() {
        val layout = layoutOf("a", "b", "c").withFolder("a", "b").setFolderGround("f1", FolderGround.CRISTAL)
            .renameFolder("f1", "Café & chá")
        assertEquals(layout, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout)))
    }
}
