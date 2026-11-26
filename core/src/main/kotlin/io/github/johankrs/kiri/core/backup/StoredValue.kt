package io.github.johankrs.kiri.core.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved setting as a backup keeps it: its value and the kind of value it is, so it comes
 * back exactly as it was saved. These are the kinds Android's settings files can hold.
 */
sealed interface StoredValue {
    data class Text(val value: String) : StoredValue
    data class Whole(val value: Int) : StoredValue
    data class Big(val value: Long) : StoredValue
    data class Flag(val value: Boolean) : StoredValue
    data class Fraction(val value: Float) : StoredValue
    data class Precise(val value: Double) : StoredValue
    data class Texts(val value: Set<String>) : StoredValue

    /** The value itself, as Android's settings files hold it. */
    val raw: Any
        get() = when (this) {
            is Text -> value
            is Whole -> value
            is Big -> value
            is Flag -> value
            is Fraction -> value
            is Precise -> value
            is Texts -> value
        }

    companion object {
        /** Wraps what a settings file holds; null for anything it should not hold. */
        fun of(raw: Any?): StoredValue? = when (raw) {
            is String -> Text(raw)
            is Int -> Whole(raw)
            is Long -> Big(raw)
            is Boolean -> Flag(raw)
            is Float -> Fraction(raw)
            is Double -> Precise(raw)
            is Set<*> -> if (raw.all { it is String }) Texts(raw.mapTo(LinkedHashSet()) { it as String }) else null
            else -> null
        }
    }
}

/**
 * Settings written as JSON and read back: one object, a member per setting, each holding the
 * kind of its value (`t`) and the value (`v`). What cannot be read is left out, so one broken
 * setting never costs the rest.
 */
object StoredValues {
    /** No backup holds more settings than this; past it the file is not one Kiri wrote. */
    const val MAX_SETTINGS = 50_000

    /** No single text is longer than this. */
    const val MAX_TEXT = 1_000_000

    fun toJson(values: Map<String, StoredValue>): JSONObject {
        val json = JSONObject()
        for ((key, value) in values.toSortedMap()) {
            val entry = JSONObject()
            when (value) {
                is StoredValue.Text -> entry.put(T, "s").put(V, value.value)
                is StoredValue.Whole -> entry.put(T, "i").put(V, value.value)
                is StoredValue.Big -> entry.put(T, "l").put(V, value.value)
                is StoredValue.Flag -> entry.put(T, "b").put(V, value.value)
                is StoredValue.Fraction -> entry.put(T, "f").put(V, value.value.toDouble())
                is StoredValue.Precise -> entry.put(T, "d").put(V, value.value)
                is StoredValue.Texts -> entry.put(T, "ss").put(V, JSONArray(value.value.sorted()))
            }
            json.put(key, entry)
        }
        return json
    }

    fun fromJson(json: JSONObject?): Map<String, StoredValue> {
        if (json == null) return emptyMap()
        val values = LinkedHashMap<String, StoredValue>()
        for (key in json.keys()) {
            if (values.size >= MAX_SETTINGS) break
            if (key.isEmpty() || key.length > 512) continue
            val entry = json.optJSONObject(key) ?: continue
            read(entry)?.let { values[key] = it }
        }
        return values
    }

    private fun read(entry: JSONObject): StoredValue? = runCatching {
        when (entry.optString(T)) {
            "s" -> entry.getString(V).takeIf { it.length <= MAX_TEXT }?.let { StoredValue.Text(it) }
            "i" -> StoredValue.Whole(entry.getInt(V))
            "l" -> StoredValue.Big(entry.getLong(V))
            "b" -> StoredValue.Flag(entry.getBoolean(V))
            "f" -> StoredValue.Fraction(entry.getDouble(V).toFloat())
            "d" -> StoredValue.Precise(entry.getDouble(V))
            "ss" -> {
                val array = entry.getJSONArray(V)
                val set = LinkedHashSet<String>()
                for (index in 0 until array.length()) set += array.getString(index)
                StoredValue.Texts(set)
            }
            else -> null
        }
    }.getOrNull()

    private const val T = "t"
    private const val V = "v"
}
