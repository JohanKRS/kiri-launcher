package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle

/**
 * How a widget of another app is dressed to look like one of Kiri's own, as the person chose
 * for that one widget. With no [glass] the widget is shown as its app made it and nothing else
 * here counts: the other choices are kept, so they are back when the glass is.
 */
data class WidgetLook(
    /** The pane of glass put behind the widget, with Kiri's corners. Null: as the app made it. */
    val glass: GlassStyle? = null,
    /** Whether the background the widget brings along stays in view, over the glass. */
    val ownBackground: Boolean = false,
    /**
     * Whether what the widget writes takes the colours that are read well on the glass. It only
     * counts while the widget's own background is out of sight, see [recolours].
     */
    val kiriColors: Boolean = true,
    /** Whether what the widget writes is set in Kiri's typeface. */
    val kiriFont: Boolean = true,
    /** How far the widget stays from the edge of the glass, one of [MARGIN_LEVELS]. */
    val marginLevel: Int = 0,
) {
    val hidesBackground: Boolean get() = glass != null && !ownBackground

    /** Only with its own background out of sight is what the widget writes read on the glass. */
    val recolours: Boolean get() = hidesBackground && kiriColors
    val changesFont: Boolean get() = glass != null && kiriFont
    val marginDp: Int get() = if (glass != null) marginLevel.coerceIn(MARGIN_LEVELS) * MARGIN_STEP_DP else 0

    /** The look one step further along [GLASSES], or null where there is no further step. */
    fun withGlassStepped(by: Int): WidgetLook? {
        val next = GLASSES.indexOf(glass) + by
        return if (next in GLASSES.indices) copy(glass = GLASSES[next]) else null
    }

    /** The look with its margin one step wider or narrower, or null at the end of the levels. */
    fun withMarginStepped(by: Int): WidgetLook? {
        val next = marginLevel + by
        return if (next in MARGIN_LEVELS) copy(marginLevel = next) else null
    }

    fun encode(): String = listOf(
        "$GLASS=${glass?.key ?: NO_GLASS}",
        "$BACKGROUND=${flag(ownBackground)}",
        "$COLORS=${flag(kiriColors)}",
        "$FONT=${flag(kiriFont)}",
        "$MARGIN=$marginLevel",
    ).joinToString(SEPARATOR)

    companion object {
        /** A widget shown as its app made it. */
        val AsMade = WidgetLook()

        val MARGIN_LEVELS = 0..4
        const val MARGIN_STEP_DP = 4

        /** The choices of glass in the order they are stepped through: none first. */
        val GLASSES: List<GlassStyle?> = listOf(null) + GlassStyle.entries

        private const val GLASS = "glass"
        private const val BACKGROUND = "background"
        private const val COLORS = "colors"
        private const val FONT = "font"
        private const val MARGIN = "margin"
        private const val NO_GLASS = "none"
        private const val SEPARATOR = ";"
        private const val KEY_START = "appwidget."
        private const val KEY_END = ".look"

        private fun flag(on: Boolean) = if (on) "1" else "0"

        /**
         * Reads what [encode] wrote. Parts that are missing or not understood keep what a new
         * look has, so a look saved by a later version is still read as far as it is known.
         */
        fun decode(text: String?): WidgetLook {
            if (text.isNullOrBlank()) return AsMade
            val parts = text.split(SEPARATOR).mapNotNull { part ->
                val at = part.indexOf('=')
                if (at <= 0) null else part.substring(0, at) to part.substring(at + 1)
            }.toMap()
            val fresh = AsMade
            return WidgetLook(
                glass = parts[GLASS]?.let { key -> GlassStyle.entries.firstOrNull { it.key == key } },
                ownBackground = parts[BACKGROUND]?.let { it == "1" } ?: fresh.ownBackground,
                kiriColors = parts[COLORS]?.let { it == "1" } ?: fresh.kiriColors,
                kiriFont = parts[FONT]?.let { it == "1" } ?: fresh.kiriFont,
                marginLevel = parts[MARGIN]?.toIntOrNull()?.coerceIn(MARGIN_LEVELS) ?: fresh.marginLevel,
            )
        }

        /** Where the look of the copy [appWidgetId] of a widget is saved. */
        fun storageKey(appWidgetId: Int): String = "$KEY_START$appWidgetId$KEY_END"

        /** Which copy of a widget the saved setting [key] is the look of, or null for any other. */
        fun appWidgetIdOf(key: String): Int? =
            if (key.startsWith(KEY_START) && key.endsWith(KEY_END)) {
                key.substring(KEY_START.length, key.length - KEY_END.length).toIntOrNull()
            } else {
                null
            }
    }
}

/**
 * The tones Android's widgets pick their colours from: five families, each from a lightest
 * shade (0) to a darkest (1000). A widget in the light theme writes with the dark shades on the
 * light ones; in the dark theme, the other way round.
 */
object WidgetTones {
    val SHADES = listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

    /**
     * How light the shade [shade] is, from 0 (black) to 100 (white). [inverted] swaps the two
     * ends, which makes a widget that thinks it is on a light ground write in light tones: the
     * way to dress it for dark glass while the device is in its light theme, and the reverse.
     */
    fun lightness(shade: Int, inverted: Boolean): Int {
        val natural = when {
            shade <= 0 -> 100
            shade <= 10 -> 99
            shade <= 50 -> 95
            shade >= 1000 -> 0
            else -> 100 - shade / 10
        }
        return if (inverted) 100 - natural else natural
    }

    /** Whether the tones have to be swapped: only when glass and theme disagree on dark or light. */
    fun inverted(darkGlass: Boolean, darkTheme: Boolean): Boolean = darkGlass != darkTheme
}
