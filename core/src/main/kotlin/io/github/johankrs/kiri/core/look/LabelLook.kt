package io.github.johankrs.kiri.core.look

/** How heavy the letters of the names of apps are. */
enum class LabelWeight(val key: String, val weight: Int) {
    LIGHT("light", 300),
    REGULAR("regular", 400),
    MEDIUM("medium", 500);

    companion object {
        fun fromKey(key: String?): LabelWeight? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How the names of apps are written, on the home screen and in the drawer alike. The typeface
 * is the one of Kiri itself; its size and weight are the person's to choose.
 */
data class LabelLook(
    /** The size of the letters, in sp. From [SIZE_SP]. */
    val sizeSp: Int = DEFAULT_SIZE_SP,
    val weight: LabelWeight = LabelWeight.REGULAR,
) {
    fun withSize(sp: Int): LabelLook = copy(sizeSp = sp.coerceIn(SIZE_SP))

    companion object {
        val SIZE_SP = 9..18
        const val DEFAULT_SIZE_SP = 12

        const val KEY_SIZE = "labels.size"
        const val KEY_WEIGHT = "labels.weight"

        fun fromStored(string: (key: String) -> String?, int: (key: String) -> Int?): LabelLook {
            return LabelLook(weight = LabelWeight.fromKey(string(KEY_WEIGHT)) ?: LabelLook().weight)
                .withSize(int(KEY_SIZE) ?: DEFAULT_SIZE_SP)
        }
    }
}
