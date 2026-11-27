package io.github.johankrs.kiri.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.GlassLevel
import io.github.johankrs.kiri.core.tv.TvAlign
import io.github.johankrs.kiri.core.tv.TvArrangement
import io.github.johankrs.kiri.core.tv.TvLook
import io.github.johankrs.kiri.core.tv.TvNameShow
import io.github.johankrs.kiri.core.tv.TvWallpaper
import io.github.johankrs.kiri.core.tv.TvWidget
import io.github.johankrs.kiri.core.tv.TvWidgetKind
import io.github.johankrs.kiri.core.tv.TvWidgets
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

/**
 * How the TV's home screen looks as a whole: a grid or a carousel, where the banners sit, how
 * large they are and how far apart, when their names show, how dark their shadow is, the
 * wallpaper and the widgets over it. [preview] is
 * the TV's home screen small, which follows every change as soon as it is saved.
 * [onPickPicture] opens the pictures to choose one for the wallpaper from.
 */
@Composable
fun TvScreenSettings(
    look: TvLook,
    onChange: (TvLook) -> Unit,
    widgets: TvWidgets,
    onWidgets: (TvWidgets) -> Unit,
    onPickPicture: () -> Unit,
    onBack: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    var draft by remember { mutableStateOf(look) }
    val change: (TvLook) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onChange(next)
        }
    }
    var widgetsDraft by remember { mutableStateOf(widgets) }
    val changeWidgets: (TvWidgets) -> Unit = { next ->
        if (next != widgetsDraft) {
            widgetsDraft = next
            onWidgets(next)
        }
    }

    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp
    // With a remote, it starts on the first setting.
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        TvLookControls(
            look = draft,
            change = change,
            widgets = widgetsDraft,
            changeWidgets = changeWidgets,
            onPickPicture = onPickPicture,
            first = first,
            modifier = modifier,
        )
    }

    SettingsScaffold(title = stringResource(R.string.settings_tv_screen), onBack = onBack) {
        if (wide) {
            // A TV: the screen small on the left, what changes it on the right.
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .padding(bottom = 16.dp),
                ) { Preview(preview) }
                controls(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Preview(preview)
            Spacer(Modifier.height(12.dp))
            controls(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/** The TV's home screen small, 16:9, as it is now. */
@Composable
private fun Preview(preview: @Composable (Modifier) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .clearAndSetSemantics { },
    ) { preview(Modifier) }
}

@Composable
private fun TvLookControls(
    look: TvLook,
    change: (TvLook) -> Unit,
    widgets: TvWidgets,
    changeWidgets: (TvWidgets) -> Unit,
    onPickPicture: () -> Unit,
    first: FocusRequester,
    modifier: Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SettingsGroup {
            Named(
                label = stringResource(R.string.tv_look_arrangement),
                values = TvArrangement.entries,
                value = look.arrangement,
                name = { arrangementName(it) },
                onPick = { change(look.copy(arrangement = it)) },
                firstButton = first,
            )
            Named(
                label = stringResource(R.string.tv_look_align),
                values = TvAlign.entries,
                value = look.align,
                name = { alignName(it) },
                onPick = { change(look.copy(align = it)) },
            )
            StepperCard(
                label = stringResource(R.string.tv_look_size),
                valueText = look.bannerSize.toString(),
                canDecrease = look.bannerSize > TvLook.SIZES.first,
                canIncrease = look.bannerSize < TvLook.SIZES.last,
                onDecrease = { change(look.withBannerSize(look.bannerSize - 1)) },
                onIncrease = { change(look.withBannerSize(look.bannerSize + 1)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // A level, as everywhere: the middle one is the usual room.
            StepperCard(
                label = stringResource(R.string.tv_look_gap),
                valueText = look.gap.toString(),
                canDecrease = look.gap > TvLook.GAPS.first,
                canIncrease = look.gap < TvLook.GAPS.last,
                onDecrease = { change(look.withGap(look.gap - 1)) },
                onIncrease = { change(look.withGap(look.gap + 1)) },
                values = TvLook.GAPS.map { it.toString() },
                modifier = Modifier.fillMaxWidth(),
            )
            Named(
                label = stringResource(R.string.tv_look_names),
                values = TvNameShow.entries,
                value = look.names,
                name = { namesName(it) },
                onPick = { change(look.copy(names = it)) },
            )
            Named(
                label = stringResource(R.string.tv_look_shadow),
                values = GlassLevel.entries,
                value = look.shadow,
                name = { shadowName(it) },
                onPick = { change(look.copy(shadow = it)) },
            )
            Named(
                label = stringResource(R.string.tv_look_wallpaper),
                values = TvWallpaper.entries,
                value = look.wallpaper,
                name = { wallpaperName(it) },
                onPick = { change(look.copy(wallpaper = it)) },
            )
            SettingsLinkRow(
                title = stringResource(R.string.tv_wallpaper_pick),
                summary = stringResource(R.string.tv_wallpaper_pick_summary),
                onClick = onPickPicture,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionTitle(stringResource(R.string.tv_look_widgets))
        SettingsGroup {
            for (kind in TvWidgetKind.entries) {
                // Off, or on at one of its sizes.
                Named(
                    label = widgetLabel(kind),
                    values = listOf(OFF) + TvWidget.SIZES.toList(),
                    value = widgets.of(kind)?.size ?: OFF,
                    name = { level -> if (level == OFF) stringResource(R.string.tv_widget_off) else sizeName(level) },
                    onPick = { level -> changeWidgets(if (level == OFF) widgets.without(kind) else widgets.with(kind, level)) },
                )
            }
        }
        BasicText(
            text = stringResource(R.string.tv_settings_caption),
            style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** A value that is a name, between ‹ and ›, going round. */
@Composable
private fun <T> Named(
    label: String,
    values: List<T>,
    value: T,
    name: @Composable (T) -> String,
    onPick: (T) -> Unit,
    firstButton: FocusRequester? = null,
) {
    val at = values.indexOf(value).coerceAtLeast(0)
    StepperCard(
        label = label,
        valueText = name(value),
        canDecrease = true,
        canIncrease = true,
        onDecrease = { onPick(values[Math.floorMod(at - 1, values.size)]) },
        onIncrease = { onPick(values[(at + 1) % values.size]) },
        values = values.map { name(it) },
        named = true,
        modifier = Modifier.fillMaxWidth(),
        firstButton = firstButton,
    )
}

@Composable
private fun arrangementName(value: TvArrangement): String = stringResource(
    when (value) {
        TvArrangement.GRID -> R.string.tv_arrangement_grid
        TvArrangement.CAROUSEL -> R.string.tv_arrangement_carousel
    },
)

@Composable
private fun alignName(value: TvAlign): String = stringResource(
    when (value) {
        TvAlign.START -> R.string.tv_align_start
        TvAlign.CENTER -> R.string.tv_align_center
        TvAlign.SPREAD -> R.string.tv_align_spread
    },
)

@Composable
private fun namesName(value: TvNameShow): String = stringResource(
    when (value) {
        TvNameShow.ALWAYS -> R.string.tv_names_always
        TvNameShow.FOCUS -> R.string.tv_names_focus
        TvNameShow.NEVER -> R.string.tv_names_never
    },
)

@Composable
private fun shadowName(value: GlassLevel): String = stringResource(
    when (value) {
        GlassLevel.OFF -> R.string.icons_shadow_off
        GlassLevel.LIGHT -> R.string.icons_shadow_light
        GlassLevel.MEDIUM -> R.string.icons_shadow_medium
        GlassLevel.STRONG -> R.string.icons_shadow_strong
    },
)

@Composable
private fun wallpaperName(value: TvWallpaper): String = stringResource(
    when (value) {
        TvWallpaper.AURORA -> R.string.tv_wallpaper_aurora
        TvWallpaper.OCEANO -> R.string.tv_wallpaper_oceano
        TvWallpaper.GRAFITE -> R.string.tv_wallpaper_grafite
        TvWallpaper.POR_DO_SOL -> R.string.tv_wallpaper_por_do_sol
        TvWallpaper.SYSTEM -> R.string.tv_wallpaper_system
        TvWallpaper.OWN -> R.string.tv_wallpaper_own
    },
)

/** A widget that is not on the TV's home screen. */
private const val OFF = -1

@Composable
private fun widgetLabel(kind: TvWidgetKind): String = stringResource(
    when (kind) {
        TvWidgetKind.CLOCK -> R.string.tv_widget_clock
        TvWidgetKind.MARKET -> R.string.market_title
    },
)

@Composable
private fun sizeName(level: Int): String = stringResource(
    when (level) {
        0 -> R.string.tv_widget_size_small
        1 -> R.string.tv_widget_size_medium
        else -> R.string.tv_widget_size_large
    },
)
