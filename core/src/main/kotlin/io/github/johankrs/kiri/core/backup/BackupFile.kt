package io.github.johankrs.kiri.core.backup

import io.github.johankrs.kiri.core.FormFactor
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Where and when a backup was made. */
data class BackupInfo(
    /** Milliseconds since 1970, in UTC. */
    val createdAt: Long,
    /** The name of the device, as it calls itself. */
    val device: String,
    val formFactor: FormFactor,
    val appVersion: String,
    /** The version of the file's layout it was written with. */
    val format: Int = BackupFile.FORMAT,
)

/**
 * Everything a backup holds: the launcher's settings, the market widget's, its keys (locked
 * with a password, or not at all), the names of the apps and icon packs the settings mention,
 * so the ones missing can be named, and the picture chosen as wallpaper.
 */
class Backup(
    val info: BackupInfo,
    val settings: Map<String, StoredValue>,
    val market: Map<String, StoredValue>,
    /**
     * The market keys as they are. Only in the copy Kiri keeps for itself, on the device, to
     * undo a restore: a file to be saved anywhere else carries them [lockedSecrets] or not at all.
     */
    val secrets: Map<String, StoredValue>? = null,
    val lockedSecrets: Locked? = null,
    /** By `AppEntry.key` or icon pack package: its name when the backup was made. */
    val names: Map<String, String> = emptyMap(),
    val wallpaper: ByteArray? = null,
) {
    /** The market keys locked inside, opened with [password]; null when it is the wrong one. */
    fun openSecrets(password: CharArray): Map<String, StoredValue>? {
        val locked = lockedSecrets ?: return null
        val text = SecretLock.open(locked, password) ?: return null
        return runCatching { StoredValues.fromJson(JSONObject(text)) }.getOrNull()
    }

    companion object {
        /** [secrets] locked with [password], to go in a file. */
        fun lock(secrets: Map<String, StoredValue>, password: CharArray): Locked =
            SecretLock.lock(StoredValues.toJson(secrets).toString(), password)
    }
}

/**
 * A backup as one file: a zip holding `kiri-backup.json` and, when there is one, the picture
 * chosen as wallpaper. Reading checks every size, so a file that is not a backup, or one made
 * to be harmful, is turned down instead of filling the memory.
 */
object BackupFile {
    /** The layout written now. A later Kiri may add to it; this one reads what it knows. */
    const val FORMAT = 1

    const val EXTENSION = "kiri"

    /** What the file is said to be, so no app adds an extension of its own to its name. */
    const val MIME = "application/octet-stream"

    private const val JSON_ENTRY = "kiri-backup.json"
    private const val WALLPAPER_ENTRY = "wallpaper"
    private const val MAGIC = "kiri-backup"
    private const val MAX_JSON = 16L * 1024 * 1024
    const val MAX_WALLPAPER = 64L * 1024 * 1024
    private const val MAX_ENTRIES = 16
    private const val MAX_NAMES = 20_000

    fun write(backup: Backup, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(JSON_ENTRY))
        zip.write(json(backup).toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        backup.wallpaper?.let { picture ->
            zip.putNextEntry(ZipEntry(WALLPAPER_ENTRY))
            zip.write(picture)
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /** The backup in [input], or null when it is not one Kiri can read. */
    fun read(input: InputStream): Backup? = runCatching {
        var json: JSONObject? = null
        var wallpaper: ByteArray? = null
        ZipInputStream(input).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > MAX_ENTRIES) return null
                when (entry.name) {
                    JSON_ENTRY -> json = JSONObject(String(zip.readAtMost(MAX_JSON) ?: return null, Charsets.UTF_8))
                    WALLPAPER_ENTRY -> wallpaper = zip.readAtMost(MAX_WALLPAPER) ?: return null
                }
            }
        }
        parse(json ?: return null, wallpaper)
    }.getOrNull()

    private fun json(backup: Backup): JSONObject {
        val info = backup.info
        val json = JSONObject()
            .put("kiri", MAGIC)
            .put("format", info.format)
            .put("createdAt", info.createdAt)
            .put("device", info.device)
            .put("formFactor", info.formFactor.name.lowercase())
            .put("appVersion", info.appVersion)
            .put("settings", StoredValues.toJson(backup.settings))
            .put("market", StoredValues.toJson(backup.market))
            .put("names", JSONObject(backup.names.toSortedMap() as Map<*, *>))
        backup.secrets?.let { json.put("secrets", StoredValues.toJson(it)) }
        backup.lockedSecrets?.let { json.put("lockedSecrets", it.toJson()) }
        return json
    }

    private fun parse(json: JSONObject, wallpaper: ByteArray?): Backup? {
        if (json.optString("kiri") != MAGIC) return null
        val info = BackupInfo(
            createdAt = json.optLong("createdAt"),
            device = json.optString("device").take(120),
            formFactor = FormFactor.entries.firstOrNull { it.name.lowercase() == json.optString("formFactor") } ?: FormFactor.PHONE,
            appVersion = json.optString("appVersion").take(40),
            format = json.optInt("format", FORMAT),
        )
        val names = HashMap<String, String>()
        json.optJSONObject("names")?.let { all ->
            for (key in all.keys()) {
                if (names.size >= MAX_NAMES) break
                all.optString(key).takeIf { it.isNotBlank() }?.let { names[key] = it.take(200) }
            }
        }
        return Backup(
            info = info,
            settings = StoredValues.fromJson(json.optJSONObject("settings")),
            market = StoredValues.fromJson(json.optJSONObject("market")),
            secrets = json.optJSONObject("secrets")?.let(StoredValues::fromJson),
            lockedSecrets = Locked.fromJson(json.optJSONObject("lockedSecrets")),
            names = names,
            wallpaper = wallpaper?.takeIf { it.isNotEmpty() },
        )
    }

    /** What is left of this entry, or null when it is longer than [limit]. */
    private fun InputStream.readAtMost(limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
