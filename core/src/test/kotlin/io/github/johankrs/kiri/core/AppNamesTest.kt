package io.github.johankrs.kiri.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppNamesTest {
    private fun app(label: String, pkg: String = "pkg.$label") = AppEntry(pkg, "Main", label)

    @Test
    fun `a renamed app shows its new name and keeps its own`() {
        val files = app("Gerenciador de arquivos", "files")
        val named = listOf(files).named(mapOf(files.key to "Arquivos")).single()

        assertEquals("Arquivos", named.label)
        assertEquals("Gerenciador de arquivos", named.ownLabel)
        assertEquals(files.key, named.key)
        assertTrue(named.renamed)
        assertFalse(files.renamed)
    }

    @Test
    fun `renamed apps sort by their new name`() {
        val apps = listOf(app("Agenda"), app("Zeta"), app("Música")).sortedByLabel(Locale.ROOT)
        val named = apps.named(mapOf(app("Zeta").key to "Banco"), Locale.ROOT).map { it.label }

        assertEquals(listOf("Agenda", "Banco", "Música"), named)
    }

    @Test
    fun `names of apps that are not there change nothing`() {
        val apps = listOf(app("Agenda"), app("Notas"))
        assertEquals(apps, apps.named(mapOf("gone/Main" to "Outro"), Locale.ROOT))
        assertEquals(apps, apps.named(emptyMap(), Locale.ROOT))
    }

    @Test
    fun `a name is tidied before it is saved`() {
        assertEquals("Meu banco", AppNames.clean("  Meu   banco \n", own = "Banco XYZ"))
        assertEquals("x".repeat(AppNames.MAX_LENGTH), AppNames.clean("x".repeat(80), own = "Banco"))
    }

    @Test
    fun `an empty name or the app's own name saves nothing`() {
        assertNull(AppNames.clean("", own = "Banco"))
        assertNull(AppNames.clean("   ", own = "Banco"))
        assertNull(AppNames.clean(" Banco ", own = "Banco"))
    }

    @Test
    fun `the storage key leads back to the app`() {
        val key = AppNames.storageKey("pkg/Main")
        assertEquals("pkg/Main", AppNames.appKeyOf(key))
        assertNull(AppNames.appKeyOf("icon.pkg/Main"))
        assertNull(AppNames.appKeyOf("name."))
    }
}
