package io.github.johankrs.kiri.widgets.system

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import android.util.SparseIntArray
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.WidgetLook
import io.github.johankrs.kiri.core.look.WidgetTones
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.spec

/**
 * What is done to the views of a widget of another app so that it looks like one of Kiri's
 * own on its pane of glass. The glass itself, its corners and the margin are not in here: those
 * are drawn around the widget, not done to it.
 */
internal class WidgetDress(
    /** Whether the background the widget brings along is taken out of sight. */
    val hideBackground: Boolean,
    /** The colour of writing on the glass, and of what is said more quietly. Null: left alone. */
    private val text: Int?,
    private val textMuted: Int?,
    /** Whether the glass is the dark kind, written on in a light colour. */
    private val darkGlass: Boolean,
    /** Kiri's typeface, or null when the widget keeps its own. */
    val fonts: KiriTypefaces?,
    /** The tones a widget of Android 12 or later picks its colours from, or null for its own. */
    val tones: SparseIntArray?,
    /** Tells one set of [tones] from another. */
    val tonesKey: String?,
) {
    val recolours: Boolean get() = text != null
    val changesWriting: Boolean get() = recolours || fonts != null

    /**
     * The colour to give something the widget wrote in [original], or null to leave it. Black,
     * white and greys are what a widget writes in to be read on its own background, so those
     * change: the strong ones to the colour of writing on the glass, the in-between ones to the
     * quieter one. A real colour usually says something (a gain, a loss, a warning) and stays
     * that colour, only made lighter or darker where it would be lost on the glass.
     */
    fun colorFor(original: Int): Int? {
        val text = text ?: return null
        val alpha = Color.alpha(original)
        if (alpha < FAINT) return null
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(original, hsl)
        val coloured = hsl[1] > COLOURED && hsl[2] in 0.12f..0.92f
        if (coloured) {
            val readable = if (darkGlass) hsl[2].coerceAtLeast(ON_DARK) else hsl[2].coerceAtMost(ON_LIGHT)
            if (readable == hsl[2]) return null
            hsl[2] = readable
            return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), alpha)
        }
        val strong = hsl[2] < 0.3f || hsl[2] > 0.7f
        val base = if (strong) text else textMuted ?: text
        return ColorUtils.setAlphaComponent(base, Color.alpha(base) * alpha / 255)
    }

    override fun equals(other: Any?): Boolean =
        other is WidgetDress && hideBackground == other.hideBackground && text == other.text && darkGlass == other.darkGlass &&
            textMuted == other.textMuted && (fonts != null) == (other.fonts != null) && tonesKey == other.tonesKey

    override fun hashCode(): Int =
        listOf(hideBackground, text, textMuted, darkGlass, fonts != null, tonesKey).hashCode()

    private companion object {
        /** Writing fainter than this is not meant to be seen. */
        const val FAINT = 16

        /** From this much colour on, a colour is taken to mean something. */
        const val COLOURED = 0.28f

        /** How light a colour is at least on dark glass, and at most on light glass. */
        const val ON_DARK = 0.7f
        const val ON_LIGHT = 0.4f
    }
}

/** What [look] asks to be done to the views of a widget, or null when it asks for nothing. */
@Composable
internal fun rememberWidgetDress(look: WidgetLook): WidgetDress? {
    val glass = look.glass ?: return null
    val context = LocalContext.current
    val spec = glass.spec()
    val accent = KiriTheme.colors.accent.toArgb()
    val darkTheme = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return remember(look, context, accent, darkTheme) {
        // Glass that is written on in a light colour is dark glass.
        val darkGlass = spec.content.luminance() > 0.5f
        val inverted = WidgetTones.inverted(darkGlass = darkGlass, darkTheme = darkTheme)
        val recolours = look.recolours
        val handsTones = recolours && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        WidgetDress(
            hideBackground = look.hidesBackground,
            text = if (recolours) spec.content.toArgb() else null,
            textMuted = if (recolours) spec.contentMuted.toArgb() else null,
            darkGlass = darkGlass,
            fonts = if (look.changesFont) KiriTypefaces.of(context) else null,
            tones = if (handsTones && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) kiriTones(accent, inverted) else null,
            tonesKey = if (handsTones) "$accent/$inverted" else null,
        )
    }
}

/**
 * Kiri's typeface for the views of a widget, in the weight nearest to what the widget asked
 * for. One file holds every weight, so each is made once and kept.
 */
internal class KiriTypefaces private constructor(private val context: Context) {
    private val byWeight = HashMap<Int, Typeface?>()

    /** Kiri's typeface standing in for [original]: as heavy as it, as far as Kiri's goes. */
    fun like(original: Typeface?): Typeface? {
        val asked = original?.weight ?: REGULAR
        val weight = (asked / 100 * 100).coerceIn(LIGHTEST, HEAVIEST)
        return byWeight.getOrPut(weight) {
            runCatching {
                val font = Font.Builder(context.resources, R.font.figtree)
                    .setFontVariationSettings("'wght' $weight")
                    .setWeight(weight)
                    .build()
                Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
            }.getOrNull()
        } ?: original
    }

    companion object {
        private const val REGULAR = 400
        private const val LIGHTEST = 300
        private const val HEAVIEST = 700

        @Volatile
        private var instance: KiriTypefaces? = null

        fun of(context: Context): KiriTypefaces = instance ?: synchronized(this) {
            instance ?: KiriTypefaces(context.applicationContext).also { instance = it }
        }
    }
}

/**
 * The tones for a widget that picks its colours from Android's own (most made for Android 12
 * or later do): greys that carry a breath of Kiri's [accent], and the accent itself. [inverted]
 * hands the dark ones where the light ones are asked for, see [WidgetTones].
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun kiriTones(accent: Int, inverted: Boolean): SparseIntArray {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent, hsl)
    val hue = hsl[0]
    val families = listOf(
        android.R.color.system_neutral1_0 to 0.05f,
        android.R.color.system_neutral2_0 to 0.09f,
        android.R.color.system_accent1_0 to 0.42f,
        android.R.color.system_accent2_0 to 0.22f,
        android.R.color.system_accent3_0 to 0.30f,
    )
    val tones = SparseIntArray(families.size * WidgetTones.SHADES.size)
    for ((first, colour) in families) {
        // Android numbers the shades of a family one after the other, lightest first.
        WidgetTones.SHADES.forEachIndexed { step, shade ->
            val light = WidgetTones.lightness(shade, inverted) / 100f
            tones.put(first + step, ColorUtils.HSLToColor(floatArrayOf(hue, colour, light)))
        }
    }
    return tones
}
