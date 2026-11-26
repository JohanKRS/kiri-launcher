package io.github.johankrs.kiri.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.look.GlassLevel
import io.github.johankrs.kiri.core.look.GlassShine
import io.github.johankrs.kiri.core.look.IconGlassUse
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.core.look.IconShape
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

/**
 * Chooses how every icon is dressed: its shape, whether it sits on glass, the size of the
 * drawing on the glass, and the tile of glass it is, which widgets and menus are made of too. [preview] shows real apps, which follow every change
 * as soon as it is saved.
 *
 * [ownSizes] is how many apps were given a size of drawing of their own; [onClearOwnSizes]
 * makes them follow the others again.
 */
@Composable
fun IconLookScreen(
    look: IconLook,
    onChange: (IconLook) -> Unit,
    onBack: () -> Unit,
    ownSizes: Int,
    onClearOwnSizes: () -> Unit,
    preview: @Composable () -> Unit,
) {
    var draft by remember { mutableStateOf(look) }
    val change: (IconLook) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onChange(next)
        }
    }
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp

    SettingsScaffold(title = stringResource(R.string.settings_icons), onBack = onBack) {
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // A picture for sighted adjustment only: nothing in it should be announced.
                Box(
                    Modifier
                        .weight(1f)
                        .clearAndSetSemantics { },
                ) { preview() }
                IconLookControls(
                    look = draft,
                    onChange = change,
                    ownSizes = ownSizes,
                    onClearOwnSizes = onClearOwnSizes,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
            ) { preview() }
            Spacer(Modifier.height(12.dp))
            IconLookControls(
                look = draft,
                onChange = change,
                ownSizes = ownSizes,
                onClearOwnSizes = onClearOwnSizes,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun IconLookControls(
    look: IconLook,
    onChange: (IconLook) -> Unit,
    ownSizes: Int,
    onClearOwnSizes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which list of choices is open, if any, by its name.
    var open by remember { mutableStateOf<String?>(null) }
    val onGlass = look.glassUse != IconGlassUse.OFF
    val tiled = look.relief != GlassLevel.OFF

    // One list of choices: it opens in place, and choosing closes it.
    @Composable
    fun <T> Choice(name: String, label: String, entries: List<T>, chosen: T, text: @Composable (T) -> String, choose: (T) -> Unit) {
        ChoiceCard(
            label = label,
            valueText = text(chosen),
            expanded = open == name,
            onToggle = { open = if (open == name) null else name },
            options = entries.map { text(it) },
            selectedIndex = entries.indexOf(chosen),
            onSelect = { index ->
                choose(entries[index])
                open = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val levels = GlassLevel.entries
    val levelName: @Composable (GlassLevel) -> String = { stringResource(it.labelRes()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SettingsGroup {
            Choice("shape", stringResource(R.string.icons_shape), IconShape.entries, look.shape, { stringResource(it.labelRes()) }) {
                onChange(look.copy(shape = it))
            }
            Choice("use", stringResource(R.string.icons_glass_use), IconGlassUse.entries, look.glassUse, { stringResource(it.labelRes()) }) {
                onChange(look.copy(glassUse = it))
            }
            Choice("glass", stringResource(R.string.icons_glass_style), GlassStyle.entries, look.glass, { stringResource(it.labelRes()) }) {
                onChange(look.copy(glass = it))
            }
            StepperCard(
                label = stringResource(R.string.icons_glass_opacity),
                valueText = "${look.glassOpacity}%",
                canDecrease = onGlass && look.glassOpacity > IconLook.GLASS_OPACITY.first,
                canIncrease = onGlass && look.glassOpacity < IconLook.GLASS_OPACITY.last,
                onDecrease = { onChange(look.withGlassOpacity(look.glassOpacity - IconLook.GLASS_OPACITY_STEP)) },
                onIncrease = { onChange(look.withGlassOpacity(look.glassOpacity + IconLook.GLASS_OPACITY_STEP)) },
                modifier = Modifier.fillMaxWidth(),
            )
            StepperCard(
                label = stringResource(R.string.icons_art),
                valueText = "${look.artPercent}%",
                canDecrease = onGlass && look.artPercent > IconLook.ART_PERCENT.first,
                canIncrease = onGlass && look.artPercent < IconLook.ART_PERCENT.last,
                onDecrease = { onChange(look.withArt(look.artPercent - IconLook.ART_STEP)) },
                onIncrease = { onChange(look.withArt(look.artPercent + IconLook.ART_STEP)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The tile of glass: icons, widgets and menus are all made of it.
        SectionTitle(stringResource(R.string.icons_tile))
        SettingsGroup {
            Choice("relief", stringResource(R.string.icons_border), levels, look.relief, levelName) {
                onChange(look.copy(relief = it))
            }
            if (tiled) {
                Choice("edgeLight", stringResource(R.string.icons_edge_light), levels, look.edgeLight, levelName) {
                    onChange(look.copy(edgeLight = it))
                }
                StepperCard(
                    label = stringResource(R.string.icons_edge_width),
                    valueText = "${look.edgeWidth}",
                    canDecrease = look.edgeWidth > IconLook.EDGE_WIDTH.first,
                    canIncrease = look.edgeWidth < IconLook.EDGE_WIDTH.last,
                    onDecrease = { onChange(look.copy(edgeWidth = (look.edgeWidth - 1).coerceIn(IconLook.EDGE_WIDTH))) },
                    onIncrease = { onChange(look.copy(edgeWidth = (look.edgeWidth + 1).coerceIn(IconLook.EDGE_WIDTH))) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Choice("shadow", stringResource(R.string.icons_shadow), levels, look.shadow, { stringResource(it.shadowLabelRes()) }) {
                    onChange(look.copy(shadow = it))
                }
            }
            Choice("shine", stringResource(R.string.icons_shine), GlassShine.entries, look.shine, { stringResource(it.labelRes()) }) {
                onChange(look.copy(shine = it))
            }
        }

        if (ownSizes > 0) {
            SettingsGroup {
                SettingsLinkRow(
                    title = stringResource(R.string.icons_clear_own_sizes),
                    summary = stringResource(R.string.icons_clear_own_sizes_summary, ownSizes),
                    onClick = onClearOwnSizes,
                )
            }
        }
        BasicText(
            text = stringResource(R.string.icons_note),
            style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

private fun IconShape.labelRes(): Int = when (this) {
    IconShape.CIRCLE -> R.string.icon_shape_circle
    IconShape.ROUNDED -> R.string.icon_shape_rounded
    IconShape.SQUIRCLE -> R.string.icon_shape_squircle
    IconShape.SOFT_SQUARE -> R.string.icon_shape_soft_square
}

// The levels of the tile have the names of the light; the shadow is a feminine word.
private fun GlassLevel.labelRes(): Int = when (this) {
    GlassLevel.OFF -> R.string.icons_shine_off
    GlassLevel.LIGHT -> R.string.icons_shine_light
    GlassLevel.MEDIUM -> R.string.icons_shine_medium
    GlassLevel.STRONG -> R.string.icons_shine_strong
}

private fun GlassLevel.shadowLabelRes(): Int = when (this) {
    GlassLevel.OFF -> R.string.icons_shadow_off
    GlassLevel.LIGHT -> R.string.icons_shadow_light
    GlassLevel.MEDIUM -> R.string.icons_shadow_medium
    GlassLevel.STRONG -> R.string.icons_shadow_strong
}

private fun GlassShine.labelRes(): Int = when (this) {
    GlassShine.OFF -> R.string.icons_shine_off
    GlassShine.LIGHT -> R.string.icons_shine_light
    GlassShine.MEDIUM -> R.string.icons_shine_medium
    GlassShine.STRONG -> R.string.icons_shine_strong
}

private fun IconGlassUse.labelRes(): Int = when (this) {
    IconGlassUse.OFF -> R.string.icons_glass_off
    IconGlassUse.TRANSPARENT_ONLY -> R.string.icons_glass_transparent
    IconGlassUse.ALL -> R.string.icons_glass_all
}
