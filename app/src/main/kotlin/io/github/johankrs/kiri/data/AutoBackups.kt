package io.github.johankrs.kiri.data

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.johankrs.kiri.AutoBackupJob
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.backup.AutoBackupFiles
import io.github.johankrs.kiri.core.backup.BackupFile
import io.github.johankrs.kiri.core.backup.BackupRepeat
import io.github.johankrs.kiri.core.backup.BackupSchedule
import io.github.johankrs.kiri.core.backup.BackupTarget
import io.github.johankrs.kiri.core.backup.PreparedKey
import io.github.johankrs.kiri.core.backup.SecretLock
import io.github.johankrs.kiri.isTelevision
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import java.time.DayOfWeek
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What kind of place a folder or file chosen for the automatic backup is in. */
enum class PlaceKind { DEVICE, CARD, DRIVE, DOWNLOADS, OTHER }

/**
 * A folder or a file chosen for the automatic backup, as Android's picker gave it: Kiri may
 * keep writing there until the person chooses another.
 */
@Immutable
data class AutoPlace(
    val uri: Uri,
    /** As a person would name it: `Download/Kiri`, or the file's name. */
    val name: String,
    val kind: PlaceKind,
    /** For [PlaceKind.OTHER], the app the place belongs to, such as Nextcloud. */
    val app: String? = null,
)

/** How an automatic backup went. */
enum class AutoOutcome {
    SAVED,

    /** Nothing had changed since the last one, so the last one stayed. */
    UNCHANGED,

    /** "One file" was chosen but no file yet. */
    NO_PLACE,

    /** The folder or file is gone, or Kiri may no longer write there. */
    NO_ACCESS,
    NO_SPACE,

    /** Kiri's own settings could not be read. */
    NO_SETTINGS,
    FAILED,
}

/** The last automatic backup: when, how it went, and the file it wrote or kept. */
@Immutable
data class AutoRun(
    val at: Long,
    val outcome: AutoOutcome,
    val name: String? = null,
    val kind: PlaceKind? = null,
    /** True when there are market keys that could not go in it, locked, because the key for them is gone. */
    val keysLeftOut: Boolean = false,
)

/**
 * Kiri's automatic backup: when it is made ([schedule]), where it goes ([folder] or [file]), and
 * how the last one went ([lastRun]). Android's job scheduler wakes Kiri around the time of each
 * (see [AutoBackupJob]); a backup missed while the device was off is made once it can be.
 *
 * In a folder each backup is a new file, and only the newest [BackupSchedule.keep] of the
 * automatic ones stay: Kiri deletes only files it wrote itself, never one saved by hand. Over
 * one file, the same file is written each time (Google Drive keeps the versions before).
 *
 * The market keys go only when the person turned them on and typed a password: then Kiri keeps
 * the key drawn from that password (not the password), locked by the device's own key store,
 * so each backup locks them as the password would and opens with it. All of this belongs to the
 * device: none of it goes into a backup.
 *
 * There is one for the whole app.
 */
@Stable
class AutoBackups private constructor(private val appContext: Context) {
    private val prefs: SharedPreferences = appContext.getSharedPreferences("auto-backup", Context.MODE_PRIVATE)
    private val backups = Backups.of(appContext)
    private val running = Mutex()

    var schedule by mutableStateOf(readSchedule())
        private set

    /** The folder chosen; null for Download/Kiri on the device. */
    var folder by mutableStateOf(readPlace(FOLDER))
        private set

    /** The file chosen to be written over; null before one is. */
    var file by mutableStateOf(readPlace(FILE))
        private set

    var lastRun by mutableStateOf(readRun())
        private set

    /** Whether the market keys go, locked with the person's password. */
    var keysOn by mutableStateOf(prefs.getBoolean(KEY_KEYS_ON, false) && prefs.contains(KEY_KEY_DATA))
        private set

    /** Whether a backup is being made right now. */
    var busy by mutableStateOf(false)
        private set

    /** When the next one is due; null while the automatic backup is off. */
    fun nextDue(): Long? {
        val schedule = schedule
        if (!schedule.on) return null
        val since = prefs.getLong(KEY_SINCE, System.currentTimeMillis())
        // One made before the schedule was last turned on does not count: turning it on starts afresh.
        val last = lastRun?.at?.takeIf { it >= since }
        return schedule.nextAfter(last, since, ZoneId.systemDefault())
    }

    /** Takes [next] as the schedule, and asks Android to wake Kiri for it. */
    fun update(next: BackupSchedule) {
        val normalized = next.normalized()
        if (normalized == schedule) return
        val editor = prefs.edit()
        if (normalized.on && !schedule.on) editor.putLong(KEY_SINCE, System.currentTimeMillis())
        // Somewhere else, the next one is written whether or not anything changed.
        if (normalized.target != schedule.target) forgetSaved(editor)
        editor
            .putBoolean(KEY_ON, normalized.on)
            .putString(KEY_REPEAT, normalized.repeat.key)
            .putInt(KEY_EVERY, normalized.everyDays)
            .putInt(KEY_WEEKDAY, normalized.weekday.value)
            .putInt(KEY_MONTH_DAY, normalized.monthDay)
            .putInt(KEY_HOUR, normalized.hour)
            .putString(KEY_TARGET, normalized.target.key)
            .putInt(KEY_KEEP, normalized.keep)
            .putBoolean(KEY_ONLY_CHANGED, normalized.onlyWhenChanged)
            .apply()
        schedule = normalized
        reschedule()
    }

    /**
     * Takes the folder [tree] the picker gave as the place of the backups from now on. The
     * backups made in the folder before stay there, and are no longer counted. False when
     * Android does not let Kiri keep writing there.
     */
    fun chooseFolder(tree: Uri): Boolean {
        if (!keepAccess(tree)) return false
        val place = describeTree(tree)
        val moved = folder?.uri != tree
        folder?.uri?.takeIf { moved && it != file?.uri }?.let(::releaseAccess)
        writePlace(FOLDER, place)
        folder = place
        if (moved) prefs.edit().remove(KEY_MADE).also(::forgetSaved).apply()
        return true
    }

    /** Takes the file [document] the picker made as the one written over each time. False as for [chooseFolder]. */
    fun chooseFile(document: Uri): Boolean {
        if (!keepAccess(document)) return false
        val place = describeDocument(document)
        file?.uri?.takeIf { it != document && it != folder?.uri }?.let(::releaseAccess)
        writePlace(FILE, place)
        file = place
        prefs.edit().also(::forgetSaved).apply()
        return true
    }

    /**
     * Turns the market keys on with [password], or off when it is null. Only the key drawn
     * from the password is kept, locked by the device. False when the device would not lock it.
     */
    suspend fun setKeys(password: CharArray?): Boolean = withContext(Dispatchers.Default) {
        if (password == null) {
            prefs.edit().putBoolean(KEY_KEYS_ON, false).remove(KEY_KEY_SALT).remove(KEY_KEY_ROUNDS).remove(KEY_KEY_IV).remove(KEY_KEY_DATA).apply()
            runCatching { keyStore().deleteEntry(VAULT_ALIAS) }
            withContext(Dispatchers.Main) { keysOn = false }
            return@withContext true
        }
        val prepared = SecretLock.prepare(password)
        password.fill(' ')
        val sealed = runCatching { seal(prepared.key) }.getOrNull() ?: return@withContext false
        prepared.key.fill(0)
        prefs.edit()
            .putBoolean(KEY_KEYS_ON, true)
            .putString(KEY_KEY_SALT, prepared.salt.encode())
            .putInt(KEY_KEY_ROUNDS, prepared.rounds)
            .putString(KEY_KEY_IV, sealed.first.encode())
            .putString(KEY_KEY_DATA, sealed.second.encode())
            .apply()
        withContext(Dispatchers.Main) { keysOn = true }
        true
    }

    /** Makes a backup now, as the automatic one would, even if nothing changed since the last. */
    suspend fun runNow(): AutoRun = run(force = true)

    /** Makes the backup that is due, if one is. For [AutoBackupJob]. */
    suspend fun runIfDue() {
        val due = nextDue() ?: return
        if (due > System.currentTimeMillis() + EARLY_MS) return
        run(force = false)
    }

    /** Asks Android to wake Kiri when the next backup is due, or to stop when it is off. */
    fun reschedule() {
        val jobs = appContext.getSystemService(JobScheduler::class.java) ?: return
        val due = nextDue()
        if (due == null) {
            jobs.cancel(JOB_ID)
            return
        }
        val job = JobInfo.Builder(JOB_ID, ComponentName(appContext, AutoBackupJob::class.java))
            .setMinimumLatency((due - System.currentTimeMillis()).coerceAtLeast(0L))
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setPersisted(true)
            .build()
        runCatching { jobs.schedule(job) }
    }

    /** Makes sure Android will wake Kiri for the next backup, as after Kiri was stopped by hand. */
    fun ensureScheduled() {
        if (!schedule.on) return
        val jobs = appContext.getSystemService(JobScheduler::class.java) ?: return
        if (jobs.getPendingJob(JOB_ID) == null) reschedule()
    }

    private suspend fun run(force: Boolean): AutoRun = running.withLock {
        withContext(Dispatchers.Main) { busy = true }
        try {
            val outcome = withContext(Dispatchers.IO) { make(force) }
            prefs.edit()
                .putLong(KEY_LAST_AT, outcome.at)
                .putString(KEY_LAST_OUTCOME, outcome.outcome.name)
                .putString(KEY_LAST_NAME, outcome.name)
                .putString(KEY_LAST_KIND, outcome.kind?.name)
                .putBoolean(KEY_LAST_KEYS_LEFT, outcome.keysLeftOut)
                .apply()
            withContext(Dispatchers.Main) { lastRun = outcome }
            outcome
        } finally {
            withContext(Dispatchers.Main) { busy = false }
        }
    }

    private suspend fun make(force: Boolean): AutoRun {
        val now = System.currentTimeMillis()
        val formFactor = FormFactor.of(
            isTelevision = appContext.isTelevision(),
            smallestWidthDp = appContext.resources.configuration.smallestScreenWidthDp,
        )
        val names = runCatching {
            AppRepository.of(appContext).loadApps(formFactor).associate { it.key to it.label }
        }.getOrDefault(emptyMap())
        val prepared = if (keysOn) unsealKey() else null
        val snapshot = backups.snapshot(names, formFactor, prepared) ?: return AutoRun(now, AutoOutcome.NO_SETTINGS)
        val keysLeftOut = keysOn && snapshot.keysLeftOut
        val target = if (formFactor == FormFactor.TV) BackupTarget.FOLDER else schedule.target

        // Nothing changed: the last one stays, and the older ones are not pushed out by copies of it.
        if (!force && schedule.onlyWhenChanged && snapshot.fingerprint == prefs.getString(KEY_SAVED_PRINT, null)) {
            val saved = prefs.getString(KEY_SAVED_URI, null)?.let(Uri::parse)
            if (saved != null && exists(saved)) {
                return AutoRun(now, AutoOutcome.UNCHANGED, prefs.getString(KEY_SAVED_NAME, null), lastRun?.kind, keysLeftOut)
            }
        }

        val place = if (target == BackupTarget.FILE) file else folder
        if (target == BackupTarget.FILE && place == null) return AutoRun(now, AutoOutcome.NO_PLACE)
        val written = try {
            when {
                target == BackupTarget.FILE -> writeOver(place!!.uri, snapshot.bytes)
                place != null -> writeInto(place.uri, AutoBackupFiles.name(now, ZoneId.systemDefault()), snapshot.bytes)
                else -> backups.writeToDownloads(AutoBackupFiles.name(now, ZoneId.systemDefault())) { it.write(snapshot.bytes) }
                    ?: throw IOException("Download/Kiri")
            }
        } catch (problem: Exception) {
            return AutoRun(now, outcomeOf(problem), place?.name, place?.kind ?: PlaceKind.DOWNLOADS)
        }
        val name = displayName(written) ?: place?.name ?: AutoBackupFiles.name(now, ZoneId.systemDefault())
        val kind = place?.kind ?: PlaceKind.DOWNLOADS
        prefs.edit()
            .putString(KEY_SAVED_PRINT, snapshot.fingerprint)
            .putString(KEY_SAVED_URI, written.toString())
            .putString(KEY_SAVED_NAME, name)
            .apply()
        if (target == BackupTarget.FOLDER) forgetOld(written)
        val authority = when (kind) {
            PlaceKind.DOWNLOADS -> Backups.DOWNLOADS_AUTHORITY
            PlaceKind.DRIVE -> Backups.DRIVE_AUTHORITY
            else -> written.authority
        }
        withContext(Dispatchers.Main) { backups.noteSaved(LastBackup(name, authority, now)) }
        return AutoRun(now, AutoOutcome.SAVED, name, kind, keysLeftOut)
    }

    /** A new file named [name] in the folder [tree], holding [bytes]. */
    private fun writeInto(tree: Uri, name: String, bytes: ByteArray): Uri {
        val resolver = appContext.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val document = DocumentsContract.createDocument(resolver, parent, BackupFile.MIME, name)
            ?: throw FileNotFoundException(name)
        try {
            (resolver.openOutputStream(document, "w") ?: throw FileNotFoundException(name)).use { it.write(bytes) }
        } catch (problem: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw problem
        }
        return document
    }

    /** [bytes] in place of what the file [document] held. */
    private fun writeOver(document: Uri, bytes: ByteArray): Uri {
        (appContext.contentResolver.openOutputStream(document, "wt") ?: throw FileNotFoundException(document.toString()))
            .use { it.write(bytes) }
        return document
    }

    /** Adds [written] to the backups made in the folder, and deletes the oldest past how many stay. */
    private fun forgetOld(written: Uri) {
        val made = prefs.getString(KEY_MADE, null).orEmpty().split('\n').filter { it.isNotBlank() } + written.toString()
        val old = AutoBackupFiles.toForget(made, schedule.keep)
        for (gone in old) {
            val uri = Uri.parse(gone)
            runCatching {
                if (DocumentsContract.isDocumentUri(appContext, uri)) {
                    DocumentsContract.deleteDocument(appContext.contentResolver, uri)
                } else {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
        // A file that could not be deleted (already gone, or no longer Kiri's) is not tried again.
        prefs.edit().putString(KEY_MADE, (made - old.toSet()).joinToString("\n")).apply()
    }

    /** Forgets which backup was the last one written, so the next is written even if nothing changed. */
    private fun forgetSaved(editor: SharedPreferences.Editor) {
        editor.remove(KEY_SAVED_PRINT).remove(KEY_SAVED_URI).remove(KEY_SAVED_NAME)
    }

    private fun outcomeOf(problem: Exception): AutoOutcome = when {
        problem is SecurityException || problem is FileNotFoundException || problem is IllegalArgumentException -> AutoOutcome.NO_ACCESS
        problem.message.orEmpty().let { "ENOSPC" in it || "No space" in it } -> AutoOutcome.NO_SPACE
        else -> AutoOutcome.FAILED
    }

    private fun exists(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun keepAccess(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }.isSuccess

    private fun releaseAccess(uri: Uri) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** `Download/Kiri` on the device, `Kiri` on a card, or the folder's own name elsewhere. */
    private fun describeTree(tree: Uri): AutoPlace {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        return when (tree.authority) {
            EXTERNAL_STORAGE -> {
                val volume = id.substringBefore(':')
                val path = id.substringAfter(':', "").trim('/')
                AutoPlace(
                    uri = tree,
                    name = path.ifEmpty { volume },
                    kind = if (volume == "primary") PlaceKind.DEVICE else PlaceKind.CARD,
                )
            }
            else -> {
                val document = runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, id) }.getOrNull()
                AutoPlace(
                    uri = tree,
                    name = document?.let(::displayName) ?: id,
                    kind = kindOf(tree.authority),
                    app = appOf(tree.authority),
                )
            }
        }
    }

    private fun describeDocument(document: Uri): AutoPlace = AutoPlace(
        uri = document,
        name = displayName(document) ?: AutoBackupFiles.SINGLE_NAME,
        kind = kindOf(document.authority),
        app = appOf(document.authority),
    )

    private fun kindOf(authority: String?): PlaceKind = when (authority) {
        Backups.DRIVE_AUTHORITY -> PlaceKind.DRIVE
        in Backups.DEVICE_AUTHORITIES -> PlaceKind.DEVICE
        else -> PlaceKind.OTHER
    }

    /** The name of the app a place belongs to, by its provider's [authority]. */
    private fun appOf(authority: String?): String? = runCatching {
        val provider = appContext.packageManager.resolveContentProvider(authority ?: return null, 0) ?: return null
        provider.applicationInfo.loadLabel(appContext.packageManager).toString()
    }.getOrNull()

    private fun readSchedule(): BackupSchedule = BackupSchedule(
        on = prefs.getBoolean(KEY_ON, false),
        repeat = BackupRepeat.of(prefs.getString(KEY_REPEAT, null)),
        everyDays = prefs.getInt(KEY_EVERY, 3),
        weekday = runCatching { DayOfWeek.of(prefs.getInt(KEY_WEEKDAY, DayOfWeek.SUNDAY.value)) }.getOrDefault(DayOfWeek.SUNDAY),
        monthDay = prefs.getInt(KEY_MONTH_DAY, 1),
        hour = prefs.getInt(KEY_HOUR, 3),
        target = BackupTarget.of(prefs.getString(KEY_TARGET, null)),
        keep = prefs.getInt(KEY_KEEP, 7),
        onlyWhenChanged = prefs.getBoolean(KEY_ONLY_CHANGED, true),
    ).normalized()

    private fun readPlace(which: String): AutoPlace? {
        val uri = prefs.getString("$which.uri", null)?.let(Uri::parse) ?: return null
        return AutoPlace(
            uri = uri,
            name = prefs.getString("$which.name", null).orEmpty(),
            kind = runCatching { PlaceKind.valueOf(prefs.getString("$which.kind", null).orEmpty()) }.getOrDefault(PlaceKind.OTHER),
            app = prefs.getString("$which.app", null),
        )
    }

    private fun writePlace(which: String, place: AutoPlace) {
        prefs.edit()
            .putString("$which.uri", place.uri.toString())
            .putString("$which.name", place.name)
            .putString("$which.kind", place.kind.name)
            .putString("$which.app", place.app)
            .apply()
    }

    private fun readRun(): AutoRun? {
        val at = prefs.getLong(KEY_LAST_AT, 0L).takeIf { it > 0L } ?: return null
        return AutoRun(
            at = at,
            outcome = runCatching { AutoOutcome.valueOf(prefs.getString(KEY_LAST_OUTCOME, null).orEmpty()) }.getOrDefault(AutoOutcome.FAILED),
            name = prefs.getString(KEY_LAST_NAME, null),
            kind = prefs.getString(KEY_LAST_KIND, null)?.let { runCatching { PlaceKind.valueOf(it) }.getOrNull() },
            keysLeftOut = prefs.getBoolean(KEY_LAST_KEYS_LEFT, false),
        )
    }

    /** The key drawn from the person's password, out of the device's key store; null when it is gone. */
    private fun unsealKey(): PreparedKey? = runCatching {
        val salt = prefs.getString(KEY_KEY_SALT, null)?.decode() ?: return null
        val iv = prefs.getString(KEY_KEY_IV, null)?.decode() ?: return null
        val data = prefs.getString(KEY_KEY_DATA, null)?.decode() ?: return null
        val rounds = prefs.getInt(KEY_KEY_ROUNDS, SecretLock.ROUNDS)
        val vault = keyStore().getKey(VAULT_ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(VAULT_CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, vault, GCMParameterSpec(128, iv))
        PreparedKey(salt = salt, rounds = rounds, key = cipher.doFinal(data))
    }.getOrNull()

    /** [secret] locked by a key that never leaves the device's key store: its iv and what it became. */
    private fun seal(secret: ByteArray): Pair<ByteArray, ByteArray> {
        val store = keyStore()
        val vault = store.getKey(VAULT_ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(VAULT_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
        val cipher = Cipher.getInstance(VAULT_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, vault)
        return cipher.iv to cipher.doFinal(secret)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        /** The job that wakes Kiri for the automatic backup. */
        const val JOB_ID = 4_210

        /** A job woken this much before the time is taken as on time. */
        private const val EARLY_MS = 60_000L

        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        private const val KEY_STORE = "AndroidKeyStore"
        private const val VAULT_ALIAS = "kiri.autoBackup"
        private const val VAULT_CIPHER = "AES/GCM/NoPadding"

        private const val FOLDER = "folder"
        private const val FILE = "file"
        private const val KEY_ON = "on"
        private const val KEY_SINCE = "since"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_EVERY = "everyDays"
        private const val KEY_WEEKDAY = "weekday"
        private const val KEY_MONTH_DAY = "monthDay"
        private const val KEY_HOUR = "hour"
        private const val KEY_TARGET = "target"
        private const val KEY_KEEP = "keep"
        private const val KEY_ONLY_CHANGED = "onlyWhenChanged"
        private const val KEY_MADE = "made"
        private const val KEY_SAVED_PRINT = "saved.fingerprint"
        private const val KEY_SAVED_URI = "saved.uri"
        private const val KEY_SAVED_NAME = "saved.name"
        private const val KEY_LAST_AT = "last.at"
        private const val KEY_LAST_OUTCOME = "last.outcome"
        private const val KEY_LAST_NAME = "last.name"
        private const val KEY_LAST_KIND = "last.kind"
        private const val KEY_LAST_KEYS_LEFT = "last.keysLeftOut"
        private const val KEY_KEYS_ON = "keys.on"
        private const val KEY_KEY_SALT = "keys.salt"
        private const val KEY_KEY_ROUNDS = "keys.rounds"
        private const val KEY_KEY_IV = "keys.iv"
        private const val KEY_KEY_DATA = "keys.data"

        @Volatile
        private var instance: AutoBackups? = null

        fun of(context: Context): AutoBackups = instance ?: synchronized(this) {
            instance ?: AutoBackups(context.applicationContext).also { instance = it }
        }
    }
}
