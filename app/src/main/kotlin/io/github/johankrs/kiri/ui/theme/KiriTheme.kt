package io.github.johankrs.kiri.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.GlassBevel
import io.github.johankrs.kiri.core.look.LabelLook

@Immutable
data class KiriColors(
    /** Translucent panel over the wallpaper: the drawer, folders, widgets. */
    val fog: Color,
    val onFog: Color,
    /** The colour of the sheet the settings lie on, which lets a little of the wallpaper through. */
    val surface: Color,
    /** A block of settings on that sheet: a pane a step darker than it, in either theme. */
    val card: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    /** Neutral controls: an unchecked switch. */
    val control: Color,
    /** The faint line between two rows of one block of settings. */
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    /** Stands in for the wallpaper inside previews. */
    val previewBackdrop: Color,
    /** Whether these are the colours of the dark theme. */
    val dark: Boolean,
)

private val LightColors = KiriColors(
    fog = Color(0xA3FFFFFF),
    onFog = Color(0xFF14262B),
    surface = Color(0xFFEEF2F1),
    card = Color(0x1014262B),
    onSurface = Color(0xFF14262B),
    onSurfaceMuted = Color(0xFF4A5F65),
    control = Color(0x2414262B),
    divider = Color(0x1414262B),
    accent = Color(0xFF1F6F78),
    onAccent = Color(0xFFFFFFFF),
    previewBackdrop = Color(0xFF5D8185),
    dark = false,
)

private val DarkColors = KiriColors(
    fog = Color(0xB8101C20),
    onFog = Color(0xFFE8EFEE),
    surface = Color(0xFF0F181B),
    card = Color(0x66050C0F),
    onSurface = Color(0xFFE8EFEE),
    onSurfaceMuted = Color(0xFFA9BABD),
    control = Color(0xFF263438),
    divider = Color(0x1AFFFFFF),
    accent = Color(0xFF7FC4CC),
    onAccent = Color(0xFF06262B),
    previewBackdrop = Color(0xFF2B4A52),
    dark = true,
)

private val LocalKiriColors = staticCompositionLocalOf { LightColors }

/** The colours for the device's light or dark mode, and [labelLook] for the names of apps. */
@Composable
fun KiriTheme(
    dark: Boolean = isSystemInDarkTheme(),
    labelLook: LabelLook = LabelLook(),
    glassTile: GlassBevel.Tile? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKiriColors provides if (dark) DarkColors else LightColors,
        LocalLabelLook provides labelLook,
        LocalGlassTile provides glassTile,
        content = content,
    )
}

object KiriTheme {
    val colors: KiriColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKiriColors.current
}

/**
 * The typeface of Kiri: settings, menus, widgets and the names of apps. One file holds every
 * weight, so a weight is asked for by its number.
 */
object KiriFonts {
    @OptIn(ExperimentalTextApi::class)
    val figtree: FontFamily = FontFamily(
        listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
            Font(
                resId = R.font.figtree,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
}

/** How the names of apps are written, as the person chose. */
val LocalLabelLook = androidx.compose.runtime.compositionLocalOf { LabelLook() }

/**
 * How far apart the lines of the name of an app are, in letter sizes: as close as they go
 * before the tail of a letter above meets the accent of a capital below.
 */
private const val LABEL_LINE = 1.14f

/** The style of the name of an app, without a colour: [KiriType.appLabel] in the chosen look. */
@Composable
@ReadOnlyComposable
fun appLabelStyle(): TextStyle {
    val look = LocalLabelLook.current
    return KiriType.appLabel.copy(
        fontSize = look.sizeSp.sp,
        lineHeight = (look.sizeSp * LABEL_LINE).sp,
        fontWeight = FontWeight(look.weight.weight),
    )
}

/**
 * Text styles without a color: each use adds the color that fits its background.
 *
 * One typeface throughout, kept light: a title is large and thin, and nothing is heavier than
 * it needs to be to stay easy to read. Lines sit close together, as close as they can without
 * one touching the next.
 */
object KiriType {
    val title = TextStyle(
        fontFamily = KiriFonts.figtree,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Light,
    )
    // Lines a little apart, so a text that wraps does not run into itself.
    val body = TextStyle(
        fontFamily = KiriFonts.figtree,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )
    val caption = TextStyle(
        fontFamily = KiriFonts.figtree,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )
    /** The name of a block of settings, written in capitals above it. */
    val section = TextStyle(
        fontFamily = KiriFonts.figtree,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
    )

    /** A number between a minus and a plus: its digits all as wide, so it does not shift. */
    val value = TextStyle(
        fontFamily = KiriFonts.figtree,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        fontFeatureSettings = "tnum",
    )
    val appLabel = TextStyle(
        fontFamily = KiriFonts.figtree,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        textAlign = TextAlign.Center,
        // A word too long for its cell is split where a dictionary would, with a hyphen.
        hyphens = Hyphens.Auto,
    )
}

/**
 * The shared "glass and mist" panel. Everything that floats over the wallpaper uses it, so the
 * look is adjusted in one place. For now it is a translucent tint; blur arrives later and every
 * caller inherits it.
 */
@Composable
fun KiriPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(KiriTheme.colors.fog),
        content = content,
    )
}
