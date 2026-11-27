package io.github.johankrs.kiri.core.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** A text locked with a password: what is needed to open it again, save the password. */
class Locked(val salt: ByteArray, val iv: ByteArray, val rounds: Int, val data: ByteArray) {
    fun toJson(): JSONObject = JSONObject()
        .put("kdf", KDF)
        .put("cipher", CIPHER)
        .put("rounds", rounds)
        .put("salt", base64(salt))
        .put("iv", base64(iv))
        .put("data", base64(data))

    companion object {
        /** Reads [toJson]'s output; null when it is not that, or asks for more work than is sane. */
        fun fromJson(json: JSONObject?): Locked? = runCatching {
            json ?: return null
            if (json.optString("kdf") != KDF || json.optString("cipher") != CIPHER) return null
            val rounds = json.getInt("rounds")
            if (rounds !in SecretLock.ROUNDS_ALLOWED) return null
            Locked(
                salt = unbase64(json.getString("salt")),
                iv = unbase64(json.getString("iv")),
                rounds = rounds,
                data = unbase64(json.getString("data")).takeIf { it.size <= MAX_DATA } ?: return null,
            )
        }.getOrNull()

        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val MAX_DATA = 1_000_000

        private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        private fun unbase64(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}

/**
 * The key a password stands for, drawn from it once with its [salt] and [rounds]: what locks a
 * text the same way the password would, without the password itself being kept. For the
 * automatic backup, which locks the market keys while nobody is there to type the password.
 */
class PreparedKey(val salt: ByteArray, val rounds: Int, val key: ByteArray)

/**
 * Locks a text with a password and opens it again: AES in GCM mode, with a key drawn from the
 * password by PBKDF2 (SHA-256) and a salt of its own. A wrong password, or a locked text that
 * was changed, opens to nothing.
 */
object SecretLock {
    /** How many rounds new locks take: slow enough to make guessing costly, quick enough on a phone. */
    const val ROUNDS = 150_000

    /** The rounds a lock may ask for when it is opened. */
    val ROUNDS_ALLOWED = 10_000..2_000_000

    /** A password shorter than this is not taken. */
    const val MIN_PASSWORD = 6

    fun lock(text: String, password: CharArray, random: SecureRandom = SecureRandom(), rounds: Int = ROUNDS): Locked {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, rounds), GCMParameterSpec(128, iv))
        return Locked(salt = salt, iv = iv, rounds = rounds, data = cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    /** The key [password] stands for, with a salt of its own, to [lock] with later. */
    fun prepare(password: CharArray, random: SecureRandom = SecureRandom(), rounds: Int = ROUNDS): PreparedKey {
        val salt = ByteArray(16).also(random::nextBytes)
        return PreparedKey(salt = salt, rounds = rounds, key = key(password, salt, rounds).encoded)
    }

    /**
     * Locks [text] with a key [prepare]d from a password: what comes out opens with [open] and
     * that password, exactly like a lock made with the password itself.
     */
    fun lock(text: String, prepared: PreparedKey, random: SecureRandom = SecureRandom()): Locked {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(prepared.key, "AES"), GCMParameterSpec(128, iv))
        return Locked(salt = prepared.salt.copyOf(), iv = iv, rounds = prepared.rounds, data = cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    /** The text [locked] holds, or null when [password] is not the one it was locked with. */
    fun open(locked: Locked, password: CharArray): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, locked.salt, locked.rounds), GCMParameterSpec(128, locked.iv))
        String(cipher.doFinal(locked.data), Charsets.UTF_8)
    } catch (_: AEADBadTagException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun key(password: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
