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
import io.github.johankrs.kiri.core.look.FolderGround
import io.github.johankrs.kiri.core.look.FolderLook
import io.github.johankrs.kiri.core.look.IconShape
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

/**
 * Chooses how every folder looks: closed (its ground, its shape and its small icons) and open
 * (the glass of the panel and how its apps are laid out). [preview] shows folders of real apps
 * in the look being adjusted, closed and open, following every change at once.
 */
@Composable
fun FolderSettingsScreen(
    look: FolderLook,
    onChange: (FolderLook) -> Unit,
    onBack: () -> Unit,
    preview: @Composable (FolderLook) -> Unit,
) {
    var draft by remember { mutableStateOf(look) }
    val change: (FolderLook) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onChange(next)
        }
    }
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp

    SettingsScaffold(title = stringResource(R.string.settings_folders), onBack = onBack) {
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
                ) { preview(draft) }
                FolderControls(
                    look = draft,
                    onChange = change,
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
            ) { preview(draft) }
            Spacer(Modifier.height(12.dp))
            FolderControls(
                look = draft,
                onChange = change,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun FolderControls(look: FolderLook, onChange: (FolderLook) -> Unit, modifier: Modifier = Modifier) {
    // Which list of choices is open, if any, by its name.
    var open by remember { mutableStateOf<String?>(null) }

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

    @Composable
    fun Level(label: String, value: Int, range: IntRange, text: String = "$value", enabled: Boolean = true, set: (Int) -> Unit) {
        StepperCard(
            label = label,
            valueText = text,
            canDecrease = enabled && value > range.first,
            canIncrease = enabled && value < range.last,
            onDecrease = { set((value - 1).coerceIn(range)) },
            onIncrease = { set((value + 1).coerceIn(range)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    fun Opacity(value: Int, enabled: Boolean, set: (Int) -> Unit) {
        StepperCard(
            label = stringResource(R.string.folders_opacity),
            valueText = "$value%",
            canDecrease = enabled && value > FolderLook.OPACITY.first,
            canIncrease = enabled && value < FolderLook.OPACITY.last,
            onDecrease = { set(value - FolderLook.OPACITY_STEP) },
            onIncrease = { set(value + FolderLook.OPACITY_STEP) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.folders_closed))
        SettingsGroup {
            Choice("ground", stringResource(R.string.folders_ground), FolderGround.entries, look.ground, { stringResource(it.labelRes()) }) {
                onChange(look.copy(ground = it))
            }
            Opacity(look.groundOpacity, enabled = look.ground != FolderGround.NONE) { onChange(look.withGroundOpacity(it)) }
            val shapes = listOf<IconShape?>(null) + IconShape.entries
            Choice("shape", stringResource(R.string.folders_shape), shapes, look.shape, { stringResource(shapeLabelRes(it)) }) {
                onChange(look.copy(shape = it))
            }
            Choice("minis", stringResource(R.string.folders_minis), FolderLook.MINIS, look.minis, { stringResource(R.string.folders_minis_up_to, it) }) {
                onChange(look.copy(minis = it))
            }
            Level(stringResource(R.string.folders_margin), look.margin, FolderLook.LEVELS) { onChange(look.copy(margin = it)) }
            Level(stringResource(R.string.folders_spacing), look.spacing, FolderLook.LEVELS) { onChange(look.copy(spacing = it)) }
            ToggleCard(
                label = stringResource(R.string.folders_show_name),
                checked = look.showName,
                onCheckedChange = { onChange(look.copy(showName = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle(stringResource(R.string.folders_open))
        SettingsGroup {
            Choice("panelGlass", stringResource(R.string.folders_glass), GlassStyle.entries, look.panelGlass, { stringResource(it.labelRes()) }) {
                onChange(look.copy(panelGlass = it))
            }
            Opacity(look.panelOpacity, enabled = true) { onChange(look.withPanelOpacity(it)) }
            Choice("columns", stringResource(R.string.folders_columns), COLUMN_CHOICES, look.columns, { columnsText(it) }) {
                onChange(look.copy(columns = it))
            }
            Level(stringResource(R.string.folders_panel_spacing), look.panelSpacing, FolderLook.LEVELS) {
                onChange(look.copy(panelSpacing = it))
            }
            Level(stringResource(R.string.folders_panel_margin), look.panelMargin, FolderLook.LEVELS) {
                onChange(look.copy(panelMargin = it))
            }
            ToggleCard(
                label = stringResource(R.string.folders_panel_names),
                checked = look.panelNames,
                onCheckedChange = { onChange(look.copy(panelNames = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleCard(
                label = stringResource(R.string.folders_panel_title),
                checked = look.panelTitle,
                onCheckedChange = { onChange(look.copy(panelTitle = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Level(stringResource(R.string.folders_dim), look.dim, FolderLook.DIM) { onChange(look.copy(dim = it)) }
        }

        if (look != FolderLook()) {
            SettingsGroup {
                SettingsLinkRow(
                    title = stringResource(R.string.folders_reset),
                    summary = stringResource(R.string.folders_reset_summary),
                    onClick = { onChange(FolderLook()) },
                )
            }
        }
        BasicText(
            text = stringResource(R.string.folders_note),
            style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** The columns an open folder may be given: the best fit, or a number of them. */
private val COLUMN_CHOICES = listOf(FolderLook.BEST_FIT, 2, 3, 4, 5, 6)

@Composable
private fun columnsText(columns: Int): String =
    if (columns == FolderLook.BEST_FIT) stringResource(R.string.folders_best_fit) else "$columns"

private fun shapeLabelRes(shape: IconShape?): Int = when (shape) {
    null -> R.string.folders_shape_icons
    IconShape.CIRCLE -> R.string.icon_shape_circle
    IconShape.ROUNDED -> R.string.icon_shape_rounded
    IconShape.SQUIRCLE -> R.string.icon_shape_squircle
    IconShape.SOFT_SQUARE -> R.string.icon_shape_soft_square
}

/** The name of what a closed folder sits on. */
internal fun FolderGround.labelRes(): Int = when (this) {
    FolderGround.FUME -> R.string.market_glass_fume
    FolderGround.CRISTAL -> R.string.market_glass_cristal
    FolderGround.NEVOA -> R.string.market_glass_nevoa
    FolderGround.NONE -> R.string.folders_ground_none
}
