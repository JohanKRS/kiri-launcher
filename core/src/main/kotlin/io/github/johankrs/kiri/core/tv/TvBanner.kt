package io.github.johankrs.kiri.core.tv

/** A line drawing Kiri can put on the banner of an input. */
enum class TvGlyph(val key: String) {
    TV("tv"),
    ANTENNA("antenna"),
    HDMI("hdmi"),
    CONTROLLER("controller"),
    HANDHELD("handheld"),
    CONSOLE("console"),
    COMPUTER("computer"),
    NOTEBOOK("notebook"),
    DISC("disc"),
    BOX("box"),
    SOUND("sound"),
    CAMERA("camera"),
    ;

    companion object {
        fun fromKey(key: String?): TvGlyph? = entries.firstOrNull { it.key == key }
    }
}

/** The color of a banner Kiri draws for an input, as 0xRRGGBB. */
enum class TvColor(val key: String, val rgb: Int) {
    GRAFITE("grafite", 0x343C48),
    AZUL("azul", 0x2660C4),
    VERMELHO("vermelho", 0xB22C34),
    VERDE("verde", 0x1E8258),
    ROXO("roxo", 0x6C40BE),
    LARANJA("laranja", 0xD66C24),
    ;

    companion object {
        fun fromKey(key: String?): TvColor? = entries.firstOrNull { it.key == key }
    }
}

/** What the banner of one place on the TV's home screen shows. */
sealed interface TvBanner {
    /** How it is saved. */
    val stored: String

    /** The banner the app came with, or Kiri's when it has none. */
    data object Own : TvBanner {
        override val stored: String get() = "own"
    }

    /** Kiri's own for an app: its icon and its name, on glass. */
    data object Kiri : TvBanner {
        override val stored: String get() = "kiri"
    }

    /** A drawing on a color, with the name: what Kiri gives an input. */
    data class Drawn(val glyph: TvGlyph, val color: TvColor) : TvBanner {
        override val stored: String get() = "drawn:${glyph.key}:${color.key}"
    }

    /** A ready-made banner of a console or computer, in one of the styles. */
    data class Console(val consoleId: String, val style: ConsoleStyle) : TvBanner {
        override val stored: String get() = "console:$consoleId:${style.key}"
    }

    companion object {
        private const val KEY_PREFIX = "tv.banner."

        fun storageKey(itemKey: String): String = KEY_PREFIX + itemKey

        /** The place a saved setting belongs to, or null when it is not about a banner. */
        fun itemKeyOf(storageKey: String): String? =
            storageKey.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)?.takeIf { it.isNotEmpty() }

        fun fromStored(stored: String?): TvBanner? {
            val parts = stored?.split(':') ?: return null
            return when (parts[0]) {
                "own" -> Own
                "kiri" -> Kiri
                "drawn" -> {
                    val glyph = TvGlyph.fromKey(parts.getOrNull(1)) ?: return null
                    val color = TvColor.fromKey(parts.getOrNull(2)) ?: return null
                    Drawn(glyph, color)
                }
                "console" -> {
                    val console = parts.getOrNull(1)?.let(TvConsoles::byId) ?: return null
                    Console(console.id, ConsoleStyle.fromKey(parts.getOrNull(2)) ?: ConsoleStyle.NOITE)
                }
                else -> null
            }
        }

        /** What an input of [kind] shows until the person picks something else. */
        fun forInput(kind: TvInputKind): Drawn = when (kind) {
            TvInputKind.TUNER -> Drawn(TvGlyph.ANTENNA, TvColor.AZUL)
            TvInputKind.HDMI -> Drawn(TvGlyph.HDMI, TvColor.GRAFITE)
            TvInputKind.COMPOSITE, TvInputKind.COMPONENT, TvInputKind.SCART, TvInputKind.SVIDEO -> Drawn(TvGlyph.TV, TvColor.GRAFITE)
            TvInputKind.DISPLAY_PORT, TvInputKind.DVI, TvInputKind.VGA -> Drawn(TvGlyph.COMPUTER, TvColor.GRAFITE)
            TvInputKind.OTHER -> Drawn(TvGlyph.BOX, TvColor.GRAFITE)
        }
    }
}

/** The kinds of input a TV has, as far as the banner and its name care. */
enum class TvInputKind {
    TUNER, HDMI, COMPOSITE, COMPONENT, SCART, SVIDEO, DISPLAY_PORT, DVI, VGA, OTHER,
}

/**
 * The names the person gives to the TV's inputs ("PlayStation 5" for "HDMI 2"). An app's name is
 * the app's own (see `AppNames`): the same everywhere, on a TV too.
 */
object TvInputNames {
    private const val KEY_PREFIX = "tv.name."

    /** The longest name kept: a banner has room for little more. */
    const val MAX_LENGTH = 24

    fun storageKey(inputId: String): String = KEY_PREFIX + inputId

    fun inputIdOf(storageKey: String): String? =
        storageKey.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)?.takeIf { it.isNotEmpty() }

    /** What to save for [typed]; null when nothing is left or it is the input's own name again. */
    fun clean(typed: String, own: String): String? {
        val name = typed.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH).trim()
        return name.takeIf { it.isNotEmpty() && it != own }
    }
}
