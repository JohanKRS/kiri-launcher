package io.github.johankrs.kiri.core.tv

/** A widget Kiri draws over the TV's home screen. [widths] are its sizes, small to large. */
enum class TvWidgetKind(val key: String, private val widths: FloatArray) {
    CLOCK("clock", floatArrayOf(210f, 270f, 330f)),

    /** Stocks and currencies: the same list as the widget of the phone's home screen. */
    MARKET("market", floatArrayOf(250f, 290f, 340f)),
    ;

    /** How wide it is at the size [level], in the units of [TvArrange]. */
    fun widthAt(level: Int): Float = widths[level.coerceIn(TvWidget.SIZES)]

    companion object {
        fun fromKey(key: String?): TvWidgetKind? = entries.firstOrNull { it.key == key }
    }
}

/** One widget over the TV's home screen, as large as [size]: 0 small, 1 medium, 2 large. */
data class TvWidget(val kind: TvWidgetKind, val size: Int = 0) {
    val width: Float get() = kind.widthAt(size)

    /** Where the focus is when it is on this widget, unlike any banner's. */
    val key: String get() = KEY_PREFIX + kind.key

    companion object {
        val SIZES = 0..2
        const val KEY_PREFIX = "@widget:"
    }
}

/** The widgets over the TV's home screen, from left to right, each kind at most once. */
data class TvWidgets(val list: List<TvWidget>) {
    fun has(kind: TvWidgetKind): Boolean = list.any { it.kind == kind }

    fun of(kind: TvWidgetKind): TvWidget? = list.firstOrNull { it.kind == kind }

    fun byKey(key: String?): TvWidget? = list.firstOrNull { it.key == key }

    /** With [kind] at [size]: where it was, or added at the end. */
    fun with(kind: TvWidgetKind, size: Int): TvWidgets {
        val sized = TvWidget(kind, size.coerceIn(TvWidget.SIZES))
        return if (has(kind)) TvWidgets(list.map { if (it.kind == kind) sized else it }) else TvWidgets(list + sized)
    }

    fun without(kind: TvWidgetKind): TvWidgets = TvWidgets(list.filter { it.kind != kind })

    /** With [kind] one size larger, or back to the smallest after the largest. */
    fun grown(kind: TvWidgetKind): TvWidgets {
        val now = of(kind) ?: return this
        return with(kind, if (now.size >= TvWidget.SIZES.last) TvWidget.SIZES.first else now.size + 1)
    }

    /** With [kind] one place along: [by] -1 to the left, 1 to the right. */
    fun moved(kind: TvWidgetKind, by: Int): TvWidgets {
        val at = list.indexOfFirst { it.kind == kind }
        val to = at + by
        if (at < 0 || to !in list.indices) return this
        val next = list.toMutableList()
        next[at] = list[to]
        next[to] = list[at]
        return TvWidgets(next)
    }

    /** The kinds that are not on the screen yet, in their own order. */
    val missing: List<TvWidgetKind> get() = TvWidgetKind.entries.filter { !has(it) }

    companion object {
        const val KEY = "tv.home.widgets"

        /** What the TV starts with: the clock, then stocks and currencies. */
        val DEFAULT = TvWidgets(listOf(TvWidget(TvWidgetKind.CLOCK), TvWidget(TvWidgetKind.MARKET)))
        val NONE = TvWidgets(emptyList())

        /** "clock:0,market:1": every widget with its size. Nothing at all when there are none. */
        fun encode(widgets: TvWidgets): String = widgets.list.joinToString(",") { "${it.kind.key}:${it.size}" }

        /** What [encode] made, or [DEFAULT] when nothing was saved yet. Unknown kinds are left out. */
        fun decode(text: String?): TvWidgets {
            if (text == null) return DEFAULT
            val seen = HashSet<TvWidgetKind>()
            val list = text.split(",").mapNotNull { part ->
                val kind = TvWidgetKind.fromKey(part.substringBefore(":").trim()) ?: return@mapNotNull null
                if (!seen.add(kind)) return@mapNotNull null
                val size = part.substringAfter(":", "").trim().toIntOrNull() ?: 0
                TvWidget(kind, size.coerceIn(TvWidget.SIZES))
            }
            return TvWidgets(list)
        }
    }
}
