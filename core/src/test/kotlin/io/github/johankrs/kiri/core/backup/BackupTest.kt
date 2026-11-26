package io.github.johankrs.kiri.core.backup

import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.HiddenApps
import io.github.johankrs.kiri.core.layout.CellPosition
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.HomeItem
import io.github.johankrs.kiri.core.layout.HomeLayout
import io.github.johankrs.kiri.core.layout.HomeLayoutCodec
import io.github.johankrs.kiri.core.layout.ItemContent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    private fun text(value: String) = StoredValue.Text(value)

    private fun app(name: String) = ItemContent.App("pkg.$name/Main")

    private fun layout(vararg items: Pair<Int, ItemContent>, pages: Int = 0): StoredValue = text(
        HomeLayoutCodec.encode(
            HomeLayout(
                items = items.mapIndexed { index, (page, content) ->
                    HomeItem(
                        id = index + 1L,
                        position = CellPosition(page, index % 4, index / 4),
                        content = content,
                        span = if (content is ItemContent.AppWidget) CellSpan(2, 2) else CellSpan.Single,
                    )
                },
                pages = pages,
            ),
        ),
    )

    private fun appsIn(value: StoredValue?): List<String> =
        HomeLayoutCodec.decode((value as StoredValue.Text).value)!!.items.mapNotNull { (it.content as? ItemContent.App)?.appKey }

    @Test
    fun `every kind of value is written and read back as it was`() {
        val values = mapOf(
            "a" to text("tab\tand\nline"),
            "b" to StoredValue.Whole(-3),
            "c" to StoredValue.Big(9_000_000_000L),
            "d" to StoredValue.Flag(true),
            "e" to StoredValue.Fraction(0.25f),
            "f" to StoredValue.Precise(1.5),
            "g" to StoredValue.Texts(setOf("x", "y")),
        )
        assertEquals(values, StoredValues.fromJson(StoredValues.toJson(values)))
    }

    @Test
    fun `values that cannot be read are left out, the rest is kept`() {
        val json = StoredValues.toJson(mapOf("good" to StoredValue.Whole(1)))
        json.put("bad", org.json.JSONObject().put("t", "i").put("v", "not a number"))
        json.put("odd", org.json.JSONObject().put("t", "?").put("v", 1))
        assertEquals(mapOf("good" to StoredValue.Whole(1)), StoredValues.fromJson(json))
    }

    @Test
    fun `each setting belongs to one part`() {
        assertEquals(BackupPart.HOME, BackupPart.of("home.phone.portrait.layout"))
        assertEquals(BackupPart.HOME, BackupPart.of("home.tablet.landscape.columns"))
        assertEquals(BackupPart.HOME, BackupPart.of("drawer.tv.landscape.iconSize"))
        assertEquals(BackupPart.HOME, BackupPart.of("appwidget.12.look"))
        assertEquals(BackupPart.ICONS, BackupPart.of("icon.pkg.a/Main"))
        assertEquals(BackupPart.ICONS, BackupPart.of("iconArt.pkg.a/Main"))
        assertEquals(BackupPart.ICONS, BackupPart.of("iconGround.pkg.a/Main"))
        assertEquals(BackupPart.ICONS, BackupPart.of("name.pkg.a/Main"))
        assertEquals(BackupPart.HIDDEN, BackupPart.of(HiddenApps.storageKey("pkg.a/Main")))
        assertEquals(BackupPart.HOME, BackupPart.of("tv.home.items"))
        assertEquals(BackupPart.HOME, BackupPart.of("tv.home.arrangement"))
        assertEquals(BackupPart.ICONS, BackupPart.of("tv.banner.app:pkg.a/Main"))
        assertEquals(BackupPart.ICONS, BackupPart.of("tv.name.com.tv/.HdmiInput/HW2"))
        assertEquals(BackupPart.WALLPAPER, BackupPart.of("wallpaper.setup"))
        // The drawer's own look is not a grid of a device.
        assertEquals(BackupPart.LOOK, BackupPart.of("drawer.glass"))
        assertEquals(BackupPart.LOOK, BackupPart.of("icons.shape"))
        assertEquals(BackupPart.LOOK, BackupPart.of("gesture.swipeUp"))
        // Something added later belongs somewhere too.
        assertEquals(BackupPart.LOOK, BackupPart.of("tv.banners.size"))
        assertEquals(FormFactor.TABLET, BackupPart.formFactorOf("home.tablet.portrait.rows"))
        assertNull(BackupPart.formFactorOf("drawer.opacity"))
    }

    @Test
    fun `a part restored is restored whole, the others stay as they are`() {
        val current = mapOf(
            "home.phone.portrait.layout" to layout(0 to app("now")),
            "home.phone.portrait.columns" to StoredValue.Whole(5),
            "icon.pkg.a/Main" to text("pack\tnow"),
            "name.pkg.b/Main" to text("Added after the backup"),
            "icons.shape" to text("circle"),
        )
        val backup = mapOf(
            "home.phone.portrait.layout" to layout(0 to app("then")),
            "icon.pkg.a/Main" to text("pack\tthen"),
            "icons.shape" to text("squircle"),
        )

        val restored = BackupPlan.restore(current, backup, setOf(BackupPart.HOME, BackupPart.ICONS))

        assertEquals(listOf("pkg.then/Main"), appsIn(restored["home.phone.portrait.layout"]))
        // The backup had no column count of its own: the phone goes back to the default.
        assertNull(restored["home.phone.portrait.columns"])
        assertEquals(text("pack\tthen"), restored["icon.pkg.a/Main"])
        assertNull(restored["name.pkg.b/Main"])
        // The look was not asked for.
        assertEquals(text("circle"), restored["icons.shape"])
    }

    @Test
    fun `the home screen of a device the backup knows nothing about is left alone`() {
        val current = mapOf(
            "home.tablet.portrait.layout" to layout(0 to app("tablet")),
            "home.phone.portrait.layout" to layout(0 to app("phoneNow")),
            "appwidget.7.look" to text("glass=fume"),
        )
        val backup = mapOf(
            "home.phone.portrait.layout" to layout(0 to app("phoneThen")),
            "appwidget.9.look" to text("glass=cristal"),
        )

        val restored = BackupPlan.restore(current, backup, setOf(BackupPart.HOME))

        assertEquals(listOf("pkg.tablet/Main"), appsIn(restored["home.tablet.portrait.layout"]))
        assertEquals(listOf("pkg.phoneThen/Main"), appsIn(restored["home.phone.portrait.layout"]))
        // Widget looks go when their widget goes, not here.
        assertEquals(text("glass=fume"), restored["appwidget.7.look"])
        assertEquals(text("glass=cristal"), restored["appwidget.9.look"])
    }

    @Test
    fun `apps hidden by the restore leave the home screens and folders`() {
        val folder = ItemContent.Folder(id = "f", name = "Work", appKeys = listOf("pkg.a/Main", "pkg.b/Main", "pkg.c/Main"))
        val current = mapOf("home.phone.portrait.layout" to layout(0 to app("a"), 0 to folder, 0 to app("d")))
        val backup = mapOf(HiddenApps.storageKey("pkg.a/Main") to StoredValue.Flag(true), HiddenApps.storageKey("pkg.b/Main") to StoredValue.Flag(true))

        val restored = BackupPlan.restore(current, backup, setOf(BackupPart.HIDDEN))

        val home = HomeLayoutCodec.decode((restored["home.phone.portrait.layout"] as StoredValue.Text).value)!!
        assertTrue(home.items.none { it.content == app("a") })
        assertTrue(home.items.any { it.content == app("d") })
        val apps = home.items.flatMap { item ->
            when (val content = item.content) {
                is ItemContent.Folder -> content.appKeys
                is ItemContent.App -> listOf(content.appKey)
                else -> emptyList()
            }
        }
        assertFalse("pkg.b/Main" in apps)
    }

    @Test
    fun `nothing asked, nothing changes`() {
        val current = mapOf("icons.shape" to text("circle"))
        assertEquals(current, BackupPlan.restore(current, mapOf("icons.shape" to text("square")), emptySet()))
    }

    @Test
    fun `the market keys of now stay unless the backup brings its own`() {
        val current = mapOf("watchlist" to text("FX:USD=1"), "key.brapi" to text("mine"))
        val backup = mapOf("watchlist" to text("B3:PETR4=1"), "refreshMinutes" to StoredValue.Whole(30))

        val kept = BackupPlan.restoreMarket(current, backup, secrets = null)
        assertEquals(text("B3:PETR4=1"), kept["watchlist"])
        assertEquals(text("mine"), kept["key.brapi"])

        val brought = BackupPlan.restoreMarket(current, backup, secrets = mapOf("key.finnhub" to text("theirs")))
        assertNull(brought["key.brapi"])
        assertEquals(text("theirs"), brought["key.finnhub"])
    }

    @Test
    fun `a widget placed again keeps its place under its new number`() {
        val widget = ItemContent.AppWidget(41, "pkg.w/Provider")
        val saved = HomeLayoutCodec.decode((layout(0 to app("a"), 1 to widget) as StoredValue.Text).value)!!

        val renumbered = saved.withAppWidgetRenumbered(old = 41, new = 77, provider = "pkg.w/Provider")

        val item = renumbered.items.single { it.content is ItemContent.AppWidget }
        assertEquals(ItemContent.AppWidget(77, "pkg.w/Provider"), item.content)
        assertEquals(saved.items.single { it.content == widget }.position, item.position)
        assertEquals(CellSpan(2, 2), item.span)
    }

    @Test
    fun `keys locked with a password open only with it`() {
        val locked = SecretLock.lock("{\"key.brapi\":1}", "segredo1".toCharArray(), rounds = 10_000)
        assertEquals("{\"key.brapi\":1}", SecretLock.open(locked, "segredo1".toCharArray()))
        assertNull(SecretLock.open(locked, "segredo2".toCharArray()))

        val again = Locked.fromJson(locked.toJson())!!
        assertEquals("{\"key.brapi\":1}", SecretLock.open(again, "segredo1".toCharArray()))
        // Changed on the way: it opens to nothing.
        again.data[0] = (again.data[0] + 1).toByte()
        assertNull(SecretLock.open(again, "segredo1".toCharArray()))
    }

    @Test
    fun `a lock asking for absurd work is not even tried`() {
        val json = SecretLock.lock("x", "segredo1".toCharArray(), rounds = 10_000).toJson().put("rounds", 900_000_000)
        assertNull(Locked.fromJson(json))
    }

    @Test
    fun `a backup is written to a file and read back`() {
        val secrets = mapOf("key.brapi" to text("abc"))
        val backup = Backup(
            info = BackupInfo(createdAt = 1_764_166_500_000L, device = "Galaxy S24 Ultra", formFactor = FormFactor.PHONE, appVersion = "0.1.0"),
            settings = mapOf("home.phone.portrait.layout" to layout(0 to app("a")), "icons.shape" to text("circle")),
            market = mapOf("watchlist" to text("FX:USD=1,B3:PETR4=0")),
            lockedSecrets = SecretLock.lock(StoredValues.toJson(secrets).toString(), "segredo1".toCharArray(), rounds = 10_000),
            names = mapOf("pkg.a/Main" to "Banco"),
            wallpaper = byteArrayOf(1, 2, 3, 4),
        )
        val out = ByteArrayOutputStream()
        BackupFile.write(backup, out)

        val read = assertNotNull(BackupFile.read(ByteArrayInputStream(out.toByteArray())))

        assertEquals(backup.info, read.info)
        assertEquals(backup.settings, read.settings)
        assertEquals(backup.market, read.market)
        assertEquals(backup.names, read.names)
        assertContentEquals(backup.wallpaper, read.wallpaper)
        assertNull(read.secrets)
        assertEquals(secrets, read.openSecrets("segredo1".toCharArray()))
        assertNull(read.openSecrets("errada".toCharArray()))
    }

    @Test
    fun `a file that is not a backup is turned down`() {
        assertNull(BackupFile.read(ByteArrayInputStream("hello".toByteArray())))

        val otherZip = ByteArrayOutputStream()
        ZipOutputStream(otherZip).use { zip ->
            zip.putNextEntry(ZipEntry("kiri-backup.json"))
            zip.write("{\"something\":\"else\"}".toByteArray())
            zip.closeEntry()
        }
        assertNull(BackupFile.read(ByteArrayInputStream(otherZip.toByteArray())))
    }

    @Test
    fun `what a backup holds is counted for the person`() {
        val folder = ItemContent.Folder(id = "f", name = "Work", appKeys = listOf("pkg.b/Main", "pkg.c/Main"))
        val settings = mapOf(
            "home.phone.portrait.layout" to layout(0 to app("a"), 0 to folder, 1 to ItemContent.AppWidget(5, "pkg.w/P"), 2 to ItemContent.Widget("market"), pages = 4),
            "home.phone.landscape.layout" to layout(0 to app("z")),
            "icon.pkg.a/Main" to text("pack.arctic\tbank"),
            "iconArt.pkg.a/Main" to StoredValue.Whole(120),
            "iconGround.pkg.b/Main" to text("glass"),
            "name.pkg.a/Main" to text("Meu banco"),
            HiddenApps.storageKey("pkg.x/Main") to StoredValue.Flag(true),
        )
        val market = mapOf("watchlist" to text("FX:USD=1,B3:PETR4=1,US:AAPL=0"))

        val summary = BackupSummary.of(settings, market, FormFactor.PHONE)

        assertEquals(HomeCount(pages = 4, apps = 3, folders = 1, widgets = 2), summary.home)
        assertEquals(2, summary.icons)
        assertEquals(1, summary.names)
        assertEquals(1, summary.hidden)
        assertEquals(3, summary.watching)
        assertEquals(setOf("pack.arctic"), BackupSummary.iconPacks(settings))
        assertEquals(listOf("pkg.z/Main", "pkg.a/Main", "pkg.b/Main", "pkg.c/Main"), BackupSummary.appsOnHome(settings, FormFactor.PHONE))
        assertEquals(listOf(ItemContent.AppWidget(5, "pkg.w/P")), BackupSummary.appWidgets(settings))
        // A TV has no home screen of its own yet: the phone's is counted.
        assertNotNull(BackupSummary.of(settings, market, FormFactor.TV).home)
        assertNull(BackupSummary.of(emptyMap(), emptyMap(), FormFactor.PHONE).watching)
    }
}
