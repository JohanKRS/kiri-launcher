package io.github.johankrs.kiri.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.backup.Backup
import io.github.johankrs.kiri.core.backup.BackupFile
import io.github.johankrs.kiri.core.backup.BackupInfo
import io.github.johankrs.kiri.core.backup.BackupPart
import io.github.johankrs.kiri.core.backup.BackupSummary
import io.github.johankrs.kiri.core.backup.StoredValue
import io.github.johankrs.kiri.core.look.WallpaperSetup
import io.github.johankrs.kiri.widgets.market.MarketStore
import io.github.johankrs.kiri.widgets.system.SystemWidgets
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The last backup saved to a file: its name, where it went (the provider's authority) and when. */
@Immutable
data class LastBackup(val name: String, val authority: String?, val at: Long)

/** A backup found in the device's Download/Kiri folder, where a TV without a file picker keeps them. */
@Immutable
data class FoundBackup(val uri: Uri, val name: String, val size: Long, val at: Long)

/**
 * A backup opened to be restored: what it holds, which of its parts can be restored, and what
 * it needs that this device does not have.
 */
@Stable
class OpenedBackup(
    val backup: Backup,
    val summary: BackupSummary,
    /** The parts it can restore, in the order they are shown. */
    val parts: List<BackupPart>,
    /** Apps on its home screens that are not installed here, by name. */
    val missingApps: List<String>,
    /** Icon packs its icons come from that are not installed here, by name. */
    val missingPacks: List<String>,
    /** Its size in bytes, when the place it came from tells. */
    val size: Long?,
)

/** What a restore did, for the screen to say once. */
@Immutable
data class RestoreReport(
    /** Whether it was the restore being undone. */
    val undone: Boolean,
    /** Widgets of other apps that Android no longer has: each asks for a tap to be placed again. */
    val widgetsToPlace: Int,
    /** False when the picture of the wallpaper could not be given back to the device. */
    val wallpaperOk: Boolean = true,
)

/**
 * Saves the launcher, whole, to a file and brings it back from one.
 *
 * The file is written and read wherever the person points Android's file picker, which offers
 * Google Drive and the device's own storage without Kiri asking for anything. Before a restore
 * a copy of how things are is kept in Kiri's own folder, so the restore can be undone.
 *
 * There is one for the whole app.
 */
@Stable
class Backups private constructor(private val appContext: Context) {
    private val settings = SettingsStore.of(appContext)
    private val market = MarketStore(appContext)
    private val studio = WallpaperStudio.of(appContext)
    private val appIcons = AppIcons(appContext)
    private val notes = appContext.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val undoFile = File(File(appContext.filesDir, "backup"), "before-restore.kiri")
    private val lock = Mutex()

    /** The last backup saved, or null before the first. */
    var last by mutableStateOf(readLast())
        private set

    /** When the copy kept to undo the last restore was made; null when there is nothing to undo. */
    var undoAt by mutableStateOf(notes.getLong(KEY_UNDO_AT, 0L).takeIf { it > 0L && undoFile.exists() })
        private set

    /** The backup opened to be restored, between choosing the file and restoring it. */
    var opened by mutableStateOf<OpenedBackup?>(null)

    /** What the last restore did, until the screen has said it. */
    var report by mutableStateOf<RestoreReport?>(null)

    /** The name a new backup is offered under: `Kiri 26-11-2026 11h20.kiri`. */
    fun suggestedName(now: Long = System.currentTimeMillis()): String =
        "Kiri " + SimpleDateFormat("dd-MM-yyyy HH'h'mm", Locale.ROOT).format(Date(now)) + "." + BackupFile.EXTENSION

    /**
     * What the launcher holds now, counted. [secrets] tells whether there are market keys to
     * carry. Null when the settings could not be read.
     */
    suspend fun current(formFactor: FormFactor): Current? = withContext(Dispatchers.IO) {
        val saved = settings.dumpAll() ?: return@withContext null
        val (plain, secrets) = market.dumpAll() ?: (emptyMap<String, StoredValue>() to emptyMap())
        Current(
            summary = BackupSummary.of(saved, plain, formFactor),
            hasMarket = plain.isNotEmpty(),
            hasKeys = secrets.isNotEmpty(),
            hasWallpaper = studio.version > 0,
        )
    }

    @Immutable
    data class Current(val summary: BackupSummary, val hasMarket: Boolean, val hasKeys: Boolean, val hasWallpaper: Boolean)

    /**
     * Writes a backup of everything to [uri]. The market keys go only with a [password], locked
     * with it. [appNames] are the names of the apps by `AppEntry.key`, kept so a restore can say
     * which are missing. False when it could not be written.
     */
    suspend fun save(uri: Uri, appNames: Map<String, String>, formFactor: FormFactor, password: CharArray?): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val backup = make(appNames, formFactor, password, forUndo = false) ?: return@withLock false
                val written = runCatching {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { BackupFile.write(backup, it) } != null
                }.getOrDefault(false)
                if (written) remember(LastBackup(displayName(uri) ?: suggestedName(), uri.authority, System.currentTimeMillis()))
                written
            }
        }

    /**
     * Writes a backup to the device's Download/Kiri folder: where a TV with no file picker keeps
     * them. False when it could not be written.
     */
    suspend fun saveToDownloads(appNames: Map<String, String>, formFactor: FormFactor, password: CharArray?): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val backup = make(appNames, formFactor, password, forUndo = false) ?: return@withLock false
                val name = suggestedName()
                runCatching {
                    val resolver = appContext.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, BackupFile.MIME)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS_FOLDER)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                    val written = resolver.openOutputStream(uri, "w")?.use { BackupFile.write(backup, it) } != null
                    if (!written) {
                        resolver.delete(uri, null, null)
                        return@runCatching false
                    }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    remember(LastBackup(name, DOWNLOADS_AUTHORITY, System.currentTimeMillis()))
                    true
                }.getOrDefault(false)
            }
        }

    /** The backups in the device's Download/Kiri folder, newest first. */
    suspend fun findInDownloads(): List<FoundBackup> = withContext(Dispatchers.IO) {
        runCatching {
            val found = ArrayList<FoundBackup>()
            val columns = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            appContext.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                columns,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf(DOWNLOADS_FOLDER, "%.${BackupFile.EXTENSION}"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    found += FoundBackup(
                        uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()),
                        name = cursor.getString(1).orEmpty(),
                        size = cursor.getLong(2),
                        at = cursor.getLong(3) * 1000L,
                    )
                }
            }
            found
        }.getOrDefault(emptyList())
    }

    /**
     * Reads the backup at [uri] and makes it the [opened] one. [isInstalled] tells which apps
     * this device has, by `AppEntry.key`. False when it is not a backup Kiri can read.
     */
    suspend fun open(uri: Uri, formFactor: FormFactor, isInstalled: (String) -> Boolean): Boolean = withContext(Dispatchers.IO) {
        val size = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        if (size != null && size > MAX_FILE) return@withContext false
        val backup = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { BackupFile.read(it.buffered()) }
        }.getOrNull() ?: return@withContext false
        val packs = IconPacks(appContext).installed().mapTo(HashSet()) { it.packageName }
        val parts = buildList {
            add(BackupPart.HOME)
            add(BackupPart.ICONS)
            add(BackupPart.HIDDEN)
            add(BackupPart.LOOK)
            if (backup.wallpaper != null) add(BackupPart.WALLPAPER)
            if (backup.market.isNotEmpty()) add(BackupPart.MARKET)
        }
        val opened = OpenedBackup(
            backup = backup,
            summary = BackupSummary.of(backup.settings, backup.market, formFactor),
            parts = parts,
            missingApps = BackupSummary.appsOnHome(backup.settings, formFactor)
                .filterNot(isInstalled)
                .map { key -> backup.names[key] ?: key.substringBefore('/') },
            missingPacks = BackupSummary.iconPacks(backup.settings)
                .filterNot { it in packs }
                .map { pack -> backup.names[pack] ?: pack },
            size = size,
        )
        withContext(Dispatchers.Main) { this@Backups.opened = opened }
        true
    }

    /**
     * Restores the [parts] of [opened]: first keeping how things are now, so it can be undone,
     * then putting the backup's in place. [secrets] are the market keys, opened with the
     * person's password; without them the keys of now stay. [screen] and [density] are for
     * making the wallpaper again. Null when nothing was restored.
     */
    suspend fun restore(
        opened: OpenedBackup,
        parts: Set<BackupPart>,
        secrets: Map<String, StoredValue>?,
        screen: IntSize,
        density: Float,
    ): RestoreReport? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (parts.isEmpty() && secrets == null) return@withLock null
            // How things are now, to go back to. Without it, nothing is changed.
            val undo = make(emptyMap(), opened.backup.info.formFactor, password = null, forUndo = true) ?: return@withLock null
            val kept = runCatching {
                undoFile.parentFile?.mkdirs()
                val partial = File(undoFile.path + ".part")
                partial.outputStream().use { BackupFile.write(undo, it) }
                partial.renameTo(undoFile)
            }.getOrDefault(false)
            if (!kept) return@withLock null
            val now = System.currentTimeMillis()
            notes.edit()
                .putLong(KEY_UNDO_AT, now)
                .putString(KEY_UNDO_WIDGETS, BackupSummary.appWidgets(undo.settings).joinToString(",") { it.appWidgetId.toString() })
                .apply()
            withContext(Dispatchers.Main) { undoAt = now }

            apply(opened.backup, parts, secrets, screen, density).also { report ->
                withContext(Dispatchers.Main) {
                    this@Backups.report = report
                    this@Backups.opened = null
                }
            }
        }
    }

    /** Goes back to how things were before the last restore. Null when there is nothing to undo. */
    suspend fun undo(screen: IntSize, density: Float): RestoreReport? = withContext(Dispatchers.IO) {
        lock.withLock {
            val backup = runCatching { undoFile.inputStream().use { BackupFile.read(it.buffered()) } }.getOrNull()
                ?: return@withLock null
            val parts = BackupPart.entries.filterTo(HashSet()) { it != BackupPart.WALLPAPER || backup.wallpaper != null }
            val report = apply(backup, parts, backup.secrets.orEmpty(), screen, density).copy(undone = true)
            undoFile.delete()
            notes.edit().remove(KEY_UNDO_AT).remove(KEY_UNDO_WIDGETS).apply()
            withContext(Dispatchers.Main) {
                undoAt = null
                this@Backups.report = report
            }
            report
        }
    }

    /**
     * Widgets kept for the restore that can be undone: Android must not forget them while the
     * home screens that show them can still come back.
     */
    fun widgetsKeptForUndo(): Set<Int> =
        if (undoAt == null) {
            emptySet()
        } else {
            notes.getString(KEY_UNDO_WIDGETS, null).orEmpty().split(',').mapNotNullTo(HashSet()) { it.toIntOrNull() }
        }

    private suspend fun apply(
        backup: Backup,
        parts: Set<BackupPart>,
        secrets: Map<String, StoredValue>?,
        screen: IntSize,
        density: Float,
    ): RestoreReport {
        // Kiri's own icon is kept by Android, not with the settings: it is put back apart.
        val icon = (backup.settings[APP_ICON] as? StoredValue.Text)?.value
        settings.restore(backup.settings - APP_ICON, parts - BackupPart.MARKET)
        if (BackupPart.LOOK in parts) AppIconColor.entries.firstOrNull { it.name == icon }?.let(appIcons::choose)
        if (BackupPart.MARKET in parts || secrets != null) {
            market.restore(if (BackupPart.MARKET in parts) backup.market else null, secrets)
        }
        var wallpaperOk = true
        val picture = backup.wallpaper
        if (BackupPart.WALLPAPER in parts && picture != null) {
            val setup = WallpaperSetup.fromStored((backup.settings[WallpaperSetup.KEY] as? StoredValue.Text)?.value)
            wallpaperOk = studio.restoreOriginal(picture) && studio.apply(setup, screen, density)
            if (wallpaperOk) WallpaperSource.of(appContext).changed()
        }
        val system = SystemWidgets.of(appContext)
        val widgetsToPlace = if (BackupPart.HOME in parts) {
            BackupSummary.appWidgets(backup.settings).count { widget ->
                system.info(widget.appWidgetId)?.provider?.flattenToString() != widget.provider
            }
        } else {
            0
        }
        return RestoreReport(undone = false, widgetsToPlace = widgetsToPlace, wallpaperOk = wallpaperOk)
    }

    /** A backup of everything as it is now; null when the settings could not be read. */
    private suspend fun make(appNames: Map<String, String>, formFactor: FormFactor, password: CharArray?, forUndo: Boolean): Backup? {
        val saved = settings.dumpAll() ?: return null
        val (plain, secrets) = market.dumpAll() ?: (emptyMap<String, StoredValue>() to emptyMap())
        val names = HashMap<String, String>()
        if (!forUndo) {
            // Only the apps and packs the settings speak of: the file says nothing else about the device.
            val wanted = (BackupSummary.appsOnHome(saved, formFactor) + saved.keys.mapNotNull { it.substringAfter('.', "").takeIf { key -> '/' in key } }).toSet()
            appNames.filterKeys { it in wanted }.let(names::putAll)
            val packs = BackupSummary.iconPacks(saved)
            if (packs.isNotEmpty()) IconPacks(appContext).installed().filter { it.packageName in packs }.forEach { names[it.packageName] = it.label }
        }
        return Backup(
            info = BackupInfo(
                createdAt = System.currentTimeMillis(),
                device = deviceName(),
                formFactor = formFactor,
                appVersion = appVersion(),
            ),
            settings = saved + (APP_ICON to StoredValue.Text(appIcons.current().name)),
            market = plain,
            secrets = if (forUndo) secrets else null,
            lockedSecrets = if (!forUndo && password != null && secrets.isNotEmpty()) Backup.lock(secrets, password) else null,
            names = names,
            wallpaper = if (studio.version > 0) studio.originalBytes(BackupFile.MAX_WALLPAPER) else null,
        )
    }

    private fun remember(backup: LastBackup) {
        notes.edit()
            .putString(KEY_LAST_NAME, backup.name)
            .putString(KEY_LAST_AUTHORITY, backup.authority)
            .putLong(KEY_LAST_AT, backup.at)
            .apply()
        last = backup
    }

    private fun readLast(): LastBackup? {
        val name = notes.getString(KEY_LAST_NAME, null) ?: return null
        return LastBackup(name, notes.getString(KEY_LAST_AUTHORITY, null), notes.getLong(KEY_LAST_AT, 0L))
    }

    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun deviceName(): String =
        runCatching { Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty()

    private fun appVersion(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull().orEmpty()

    companion object {
        /** Where a TV with no file picker keeps its backups, and how that place is named. */
        val DOWNLOADS_FOLDER = Environment.DIRECTORY_DOWNLOADS + "/Kiri/"
        const val DOWNLOADS_AUTHORITY = "kiri.downloads"

        /** The authority of Google Drive's files in Android's file picker. */
        const val DRIVE_AUTHORITY = "com.google.android.apps.docs.storage"

        /** The device's own storage and its Downloads, in the file picker. */
        val DEVICE_AUTHORITIES = setOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
            "media",
        )

        /** No backup file is larger than this. */
        private const val MAX_FILE = 96L * 1024 * 1024

        /** Where a backup keeps the color of Kiri's own icon, among the settings of the look. */
        private const val APP_ICON = "kiri.appIcon"

        private const val KEY_LAST_NAME = "last.name"
        private const val KEY_LAST_AUTHORITY = "last.authority"
        private const val KEY_LAST_AT = "last.at"
        private const val KEY_UNDO_AT = "undo.at"
        private const val KEY_UNDO_WIDGETS = "undo.widgets"

        @Volatile
        private var instance: Backups? = null

        fun of(context: Context): Backups = instance ?: synchronized(this) {
            instance ?: Backups(context.applicationContext).also { instance = it }
        }
    }
}
