package io.github.johankrs.kiri.core.tv

/** One place on the TV's home screen: an app, or one of the TV's inputs (HDMI, AV, antenna). */
sealed interface TvItem {
    /** Stable identity, saved and used as list key. */
    val key: String

    /** An app, by `AppEntry.key`. */
    data class App(val appKey: String) : TvItem {
        override val key: String get() = APP + appKey
    }

    /** An input of the TV, by the id Android gives it (`TvInputInfo.getId`). */
    data class Input(val inputId: String) : TvItem {
        override val key: String get() = INPUT + inputId
    }

    companion object {
        private const val APP = "app:"
        private const val INPUT = "input:"

        fun fromKey(key: String): TvItem? = when {
            key.startsWith(APP) -> key.removePrefix(APP).takeIf { it.isNotEmpty() }?.let(::App)
            key.startsWith(INPUT) -> key.removePrefix(INPUT).takeIf { it.isNotEmpty() }?.let(::Input)
            else -> null
        }
    }
}

/**
 * What is on the TV's home screen, in order. Only what the person chose is there; "All apps"
 * always comes after these and is not one of them.
 */
data class TvHomeItems(val items: List<TvItem>) {
    operator fun contains(item: TvItem): Boolean = item in items

    /** With [item] at the end, unless it is already there. */
    fun with(item: TvItem): TvHomeItems = if (item in items) this else TvHomeItems(items + item)

    fun without(item: TvItem): TvHomeItems = if (item in items) TvHomeItems(items - item) else this

    /** With [item] taken out and put back so that it ends up at [to]. */
    fun moved(item: TvItem, to: Int): TvHomeItems {
        val from = items.indexOf(item)
        if (from < 0) return this
        val target = to.coerceIn(0, items.lastIndex)
        if (target == from) return this
        val list = items.toMutableList()
        list.removeAt(from)
        list.add(target, item)
        return TvHomeItems(list)
    }

    /**
     * Without what is gone: apps no longer installed (or hidden) and inputs the TV no longer
     * has. [isApp] and [isInput] are null while what exists is not known yet: then nothing goes.
     */
    fun present(isApp: ((String) -> Boolean)?, isInput: ((String) -> Boolean)?): TvHomeItems {
        val kept = items.filter { item ->
            when (item) {
                is TvItem.App -> isApp?.invoke(item.appKey) ?: true
                is TvItem.Input -> isInput?.invoke(item.inputId) ?: true
            }
        }
        return if (kept.size == items.size) this else TvHomeItems(kept)
    }

    /**
     * With each app of [moves] under its new key, old key to new, in the same place: it turned
     * to another of its icons. An app already here under its new key keeps its own place.
     */
    fun withAppsMoved(moves: Map<String, String>): TvHomeItems {
        if (moves.isEmpty() || items.none { it is TvItem.App && it.appKey in moves }) return this
        val seen = HashSet<TvItem>()
        val kept = items.filter { it !is TvItem.App || it.appKey !in moves }.toHashSet()
        val moved = items.mapNotNull { item ->
            val to = (item as? TvItem.App)?.appKey?.let(moves::get)?.let(TvItem::App) ?: return@mapNotNull item
            to.takeIf { it !in kept && seen.add(it) }
        }
        return TvHomeItems(moved)
    }

    companion object {
        const val KEY = "tv.home.items"

        /** Apps with a TV entry put on the home screen the first time, at most. */
        const val FIRST_APPS = 12

        val EMPTY = TvHomeItems(emptyList())

        /** One key a line: keys never hold a line break. */
        fun encode(home: TvHomeItems): String = home.items.joinToString("\n") { it.key }

        /** Null when nothing was saved yet. */
        fun decode(stored: String?): TvHomeItems? {
            if (stored == null) return null
            return TvHomeItems(stored.split('\n').mapNotNull { TvItem.fromKey(it) }.distinct())
        }

        /**
         * The home screen before the person changed anything: the TV's inputs, then the first
         * apps made for a TV. Apps installed afterwards never come in on their own.
         */
        fun firstRun(inputIds: List<String>, tvAppKeys: List<String>): TvHomeItems = TvHomeItems(
            inputIds.map { TvItem.Input(it) } + tvAppKeys.take(FIRST_APPS).map { TvItem.App(it) },
        )
    }
}
