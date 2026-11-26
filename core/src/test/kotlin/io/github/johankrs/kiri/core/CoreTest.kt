package io.github.johankrs.kiri.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTest {
    private fun app(label: String, pkg: String = "pkg.$label") = AppEntry(pkg, "Main", label)

    @Test
    fun `labels sort ignoring case and accents`() {
        val sorted = listOf(app("Zoom"), app("câmera"), app("Ágora"), app("agenda"), app("Calculadora"))
            .sortedByLabel(Locale.forLanguageTag("pt-BR"))
            .map { it.label }

        assertEquals(listOf("agenda", "Ágora", "Calculadora", "câmera", "Zoom"), sorted)
    }

    @Test
    fun `apps with the same label keep a stable order`() {
        val sorted = listOf(app("Notas", "b.notes"), app("Notas", "a.notes"))
            .sortedByLabel(Locale.ROOT)
            .map { it.packageName }

        assertEquals(listOf("a.notes", "b.notes"), sorted)
    }

    @Test
    fun `form factor follows television first, then width`() {
        assertEquals(FormFactor.TV, FormFactor.of(isTelevision = true, smallestWidthDp = 960))
        assertEquals(FormFactor.TABLET, FormFactor.of(isTelevision = false, smallestWidthDp = 600))
        assertEquals(FormFactor.PHONE, FormFactor.of(isTelevision = false, smallestWidthDp = 411))
    }

    @Test
    fun `grid values are pulled back into range`() {
        val wild = GridSpec(
            columns = 99, rows = 0, marginHorizontalDp = -4, marginTopDp = 500,
            marginBottomDp = 12, gapDp = 999, iconSizeDp = 1, showLabels = false,
        ).coerced()

        assertEquals(GridSpec.COLUMNS.last, wild.columns)
        assertEquals(GridSpec.ROWS.first, wild.rows)
        assertEquals(0, wild.marginHorizontalDp)
        assertEquals(GridSpec.MARGIN_DP.last, wild.marginTopDp)
        assertEquals(12, wild.marginBottomDp)
        assertEquals(GridSpec.GAP_DP.last, wild.gapDp)
        assertEquals(GridSpec.ICON_SIZE_DP.first, wild.iconSizeDp)
    }

    @Test
    fun `every default grid is already inside its range`() {
        for (formFactor in FormFactor.entries) {
            for (landscape in listOf(false, true)) {
                val spec = GridSpec.default(formFactor, landscape)
                assertEquals(spec, spec.coerced(), "$formFactor landscape=$landscape")
            }
        }
    }

    @Test
    fun `each profile stores its settings under its own prefix`() {
        val phone = GridProfile(Surface.DRAWER, FormFactor.PHONE, landscape = false)
        val tv = GridProfile(Surface.HOME, FormFactor.TV, landscape = true)

        assertEquals("drawer.phone.portrait.columns", GridField.COLUMNS.storageKey(phone))
        assertEquals("home.tv.landscape.showLabels", GridField.showLabelsStorageKey(tv))
    }

    @Test
    fun `a field change stays inside its range and leaves the rest alone`() {
        val base = GridSpec.default(FormFactor.PHONE, landscape = false)

        val wider = GridField.COLUMNS.set(base, base.columns + 1)
        assertEquals(base.columns + 1, wider.columns)
        assertEquals(base.copy(columns = base.columns + 1), wider)

        assertEquals(GridSpec.ICON_SIZE_DP.last, GridField.ICON_SIZE.set(base, 9_999).iconSizeDp)
        for (field in GridField.entries) {
            assertEquals(field.get(base), field.get(field.set(base, field.get(base))), field.name)
        }
    }

    @Test
    fun `the drawer has no row count`() {
        assertEquals(false, GridField.ROWS in GridField.forSurface(Surface.DRAWER))
        assertEquals(true, GridField.ROWS in GridField.forSurface(Surface.HOME))
    }

    @Test
    fun `stored values override defaults and bad values are corrected`() {
        val profile = GridProfile(Surface.DRAWER, FormFactor.PHONE, landscape = false)
        val default = GridSpec.default(FormFactor.PHONE, landscape = false)
        val ints = mapOf(
            GridField.COLUMNS.storageKey(profile) to 4,
            GridField.GAP.storageKey(profile) to -50,
            "home.phone.portrait.columns" to 9,
        )
        val booleans = mapOf(
            GridField.showLabelsStorageKey(profile) to false,
            GridField.pageDotsStorageKey(profile) to false,
        )

        val spec = GridSpec.fromStored(profile, ints::get, booleans::get)

        assertEquals(
            default.copy(columns = 4, gapDp = GridSpec.GAP_DP.first, showLabels = false, showPageDots = false),
            spec,
        )
        assertEquals(default, GridSpec.fromStored(profile, { null }, { null }))
    }
}
