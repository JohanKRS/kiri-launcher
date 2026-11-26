package io.github.johankrs.kiri.widgets.market

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.market.Instrument
import io.github.johankrs.kiri.market.KeyService
import io.github.johankrs.kiri.market.Market
import io.github.johankrs.kiri.market.MarketRepository
import io.github.johankrs.kiri.market.MarketSettings
import io.github.johankrs.kiri.market.MarketWidgetSpec
import io.github.johankrs.kiri.market.WatchEntry
import io.github.johankrs.kiri.ui.settings.SectionTitle
import io.github.johankrs.kiri.ui.settings.SettingsScaffold
import io.github.johankrs.kiri.ui.settings.StepperCard
import io.github.johankrs.kiri.ui.settings.ToggleCard
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import kotlinx.coroutines.launch

/**
 * Everything about the market widget on one screen: the widget itself at the top, changing as
 * each control is touched, and below it the look, the list and the keys.
 */
@Composable
fun MarketSettingsScreen(
    store: MarketStore,
    controller: MarketController,
    repository: MarketRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stored by store.prefs.collectAsState(initial = null)
    // The screen keeps its own working copy, so the preview follows every tap at once.
    var draft by remember { mutableStateOf<MarketPrefs?>(null) }
    val prefs = draft ?: stored
    val title = stringResource(R.string.market_title)
    if (prefs == null) {
        SettingsScaffold(title = title, onBack = onBack) {}
        return
    }
    val change: (MarketPrefs) -> Unit = { next ->
        draft = next
        scope.launch { store.save(next) }
    }

    MarketAutoRefresh(controller, prefs)
    // The guide for one service's key opens over this screen, the way a settings screen opens
    // from the list: from the right, and back to the left.
    var guide by rememberSaveable { mutableStateOf<KeyService?>(null) }
    BackHandler(enabled = guide != null) { guide = null }
    var chosen by remember { mutableStateOf(MarketWidgetSpec.SPEC.defaultSize) }
    AnimatedContent(
        targetState = guide,
        transitionSpec = {
            val direction = if (targetState == null) -1 else 1
            val slide = tween<IntOffset>(280)
            (fadeIn(tween(220)) + slideInHorizontally(slide) { direction * it / 5 }) togetherWith
                (fadeOut(tween(140)) + slideOutHorizontally(slide) { -direction * it / 5 })
        },
        label = "market settings",
    ) { shown ->
        if (shown != null) {
            MarketKeyGuide(service = shown, prefs = prefs, repository = repository, onChange = change, onBack = { guide = null })
        } else {
            MarketSettingsPage(
                prefs = prefs,
                controller = controller,
                repository = repository,
                chosen = chosen,
                onChoose = { chosen = it },
                onChange = change,
                onOpenGuide = { guide = it },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun MarketSettingsPage(
    prefs: MarketPrefs,
    controller: MarketController,
    repository: MarketRepository,
    chosen: CellSpan,
    onChoose: (CellSpan) -> Unit,
    onChange: (MarketPrefs) -> Unit,
    onOpenGuide: (KeyService) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.market_title)
    val change = onChange
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp

    // The preview draws cells at about the size they have on a home screen, so it offers the
    // sizes that fit on this screen at that scale.
    val spec = MarketWidgetSpec.SPEC
    val previewWidth = (if (wide) configuration.screenWidthDp / 2 else configuration.screenWidthDp) - 56
    val previewHeight = configuration.screenHeightDp - (if (wide) 140 else 330)
    val limit = CellSpan(
        columns = (previewWidth / 80).coerceIn(spec.minSize.columns, spec.maxSize.columns),
        rows = (previewHeight / PREVIEW_CELL_HEIGHT.value.toInt()).coerceIn(2, if (wide) spec.maxSize.rows else 4),
    )
    val size = CellSpan(chosen.columns.coerceAtMost(limit.columns), chosen.rows.coerceAtMost(limit.rows))

    SettingsScaffold(title = title, onBack = onBack, focusFirst = true) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                WidgetPreview(
                    size = size,
                    limit = limit,
                    controller = controller,
                    prefs = prefs,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 16.dp),
                )
                Controls(
                    prefs = prefs,
                    repository = repository,
                    onOpenGuide = onOpenGuide,
                    size = size,
                    sizeLimit = limit,
                    onSizeChange = onChoose,
                    onChange = change,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            WidgetPreview(
                size = size,
                limit = limit,
                controller = controller,
                prefs = prefs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_CELL_HEIGHT * limit.rows + 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            Controls(
                prefs = prefs,
                repository = repository,
                onOpenGuide = onOpenGuide,
                size = size,
                sizeLimit = limit,
                onSizeChange = onChoose,
                onChange = change,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

private val PREVIEW_CELL_HEIGHT = 92.dp

/**
 * The real widget at [size] cells, in a frame that holds up to [limit] cells, so that resizing
 * it here looks like resizing it on the home screen.
 */
@Composable
private fun WidgetPreview(
    size: CellSpan,
    limit: CellSpan,
    controller: MarketController,
    prefs: MarketPrefs,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(KiriTheme.colors.previewBackdrop)
            .padding(12.dp),
    ) {
        val cellWidth = minOf(maxWidth / limit.columns, 100.dp)
        val cellHeight = minOf(maxHeight / limit.rows, PREVIEW_CELL_HEIGHT)
        // The real widget, so what it opens here is what it opens on the home screen.
        MarketWithPanes(
            controller = controller,
            prefs = prefs,
            modifier = Modifier.size(cellWidth * size.columns, cellHeight * size.rows),
        )
    }
}

@Composable
private fun Controls(
    prefs: MarketPrefs,
    repository: MarketRepository,
    onOpenGuide: (KeyService) -> Unit,
    size: CellSpan,
    sizeLimit: CellSpan,
    onSizeChange: (CellSpan) -> Unit,
    onChange: (MarketPrefs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = prefs.settings
    val setSettings: (MarketSettings) -> Unit = { onChange(prefs.copy(settings = it.coerced())) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.market_preview_size))
        val minSize = MarketWidgetSpec.SPEC.minSize
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            StepperCard(
                label = stringResource(R.string.market_preview_width),
                valueText = size.columns.toString(),
                canDecrease = size.columns > minSize.columns,
                canIncrease = size.columns < sizeLimit.columns,
                onDecrease = { onSizeChange(CellSpan(size.columns - 1, size.rows)) },
                onIncrease = { onSizeChange(CellSpan(size.columns + 1, size.rows)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            StepperCard(
                label = stringResource(R.string.market_preview_height),
                valueText = size.rows.toString(),
                canDecrease = size.rows > minSize.rows,
                canIncrease = size.rows < sizeLimit.rows,
                onDecrease = { onSizeChange(CellSpan(size.columns, size.rows - 1)) },
                onIncrease = { onSizeChange(CellSpan(size.columns, size.rows + 1)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        BasicText(
            text = stringResource(R.string.market_preview_note),
            style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionTitle(stringResource(R.string.market_section_glass))
        ChoiceRow(
            options = listOf(
                GlassStyle.FUME to stringResource(R.string.market_glass_fume),
                GlassStyle.CRISTAL to stringResource(R.string.market_glass_cristal),
                GlassStyle.NEVOA to stringResource(R.string.market_glass_nevoa),
            ),
            selected = settings.glass,
            onSelect = { setSettings(settings.withGlass(it)) },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            StepperCard(
                label = stringResource(R.string.market_opacity),
                valueText = stringResource(R.string.market_percent, settings.opacityPercent),
                canDecrease = settings.opacityPercent > GlassStyle.OPACITY_PERCENT.first,
                canIncrease = settings.opacityPercent < GlassStyle.OPACITY_PERCENT.last,
                onDecrease = { setSettings(settings.copy(opacityPercent = settings.opacityPercent - 1)) },
                onIncrease = { setSettings(settings.copy(opacityPercent = settings.opacityPercent + 1)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            StepperCard(
                label = stringResource(R.string.market_corners),
                // A level, not a measure: each tap rounds the corners one step more.
                valueText = settings.cornerDp.toString(),
                canDecrease = settings.cornerDp > MarketSettings.CORNER_DP.first,
                canIncrease = settings.cornerDp < MarketSettings.CORNER_DP.last,
                onDecrease = { setSettings(settings.copy(cornerDp = settings.cornerDp - 1)) },
                onIncrease = { setSettings(settings.copy(cornerDp = settings.cornerDp + 1)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        // Each on a line of its own: side by side, "30 min" and the name of the switch had no room.
        val shorter = settings.refreshStepped(-1)
        val longer = settings.refreshStepped(1)
        StepperCard(
            label = stringResource(R.string.market_refresh),
            valueText = if (settings.refreshMinutes < 60) {
                stringResource(R.string.market_minutes, settings.refreshMinutes)
            } else {
                stringResource(R.string.market_hours, settings.refreshMinutes / 60)
            },
            canDecrease = shorter != null,
            canIncrease = longer != null,
            onDecrease = { shorter?.let(setSettings) },
            onIncrease = { longer?.let(setSettings) },
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleCard(
            label = stringResource(R.string.market_chart),
            checked = settings.showChart,
            onCheckedChange = { setSettings(settings.copy(showChart = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.market_section_list))
        prefs.watchlist.entries.forEachIndexed { index, entry ->
            EntryRow(
                entry = entry,
                canMoveUp = index > 0,
                canMoveDown = index < prefs.watchlist.entries.lastIndex,
                onToggle = { onChange(prefs.copy(watchlist = prefs.watchlist.setEnabled(index, it))) },
                onMove = { by -> onChange(prefs.copy(watchlist = prefs.watchlist.move(index, by))) },
                onRemove = { onChange(prefs.copy(watchlist = prefs.watchlist.removeAt(index))) },
            )
        }
        AddEntry(onAdd = { onChange(prefs.copy(watchlist = prefs.watchlist.add(it))) })

        MarketSourcesSection(prefs = prefs, repository = repository, onChange = onChange, onOpenGuide = onOpenGuide)
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Choices where exactly one is on. They share the line evenly while their words fit; a choice
 * with more to say gets more room, and when the line is too short they go one under the other.
 */
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = KiriTheme.colors
    ChoiceFlow(slots = options.size, gap = 6.dp, modifier = Modifier.fillMaxWidth()) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) colors.accent else colors.card)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                BasicText(
                    text = label,
                    style = KiriType.body.copy(
                        color = if (on) colors.onAccent else colors.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WatchEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = KiriTheme.colors
    val symbol = entry.instrument.symbol
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .toggleable(value = entry.enabled, role = Role.Checkbox, onValueChange = onToggle)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            CheckBox(entry.enabled)
            Spacer(Modifier.width(10.dp))
            Column {
                BasicText(text = symbol, style = KiriType.body.copy(color = colors.onSurface))
                BasicText(
                    text = stringResource(
                        when (entry.instrument.market) {
                            Market.B3 -> R.string.market_entry_b3
                            Market.US -> R.string.market_entry_us
                            Market.FX -> R.string.market_entry_fx
                        },
                    ),
                    style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                )
            }
        }
        SmallButton(Mark.UP, stringResource(R.string.market_move_up, symbol), canMoveUp) { onMove(-1) }
        SmallButton(Mark.DOWN, stringResource(R.string.market_move_down, symbol), canMoveDown) { onMove(1) }
        SmallButton(Mark.REMOVE, stringResource(R.string.market_remove, symbol), true, onRemove)
    }
}

@Composable
private fun AddEntry(onAdd: (Instrument) -> Unit) {
    val colors = KiriTheme.colors
    var market by remember { mutableStateOf(Market.B3) }
    var typed by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val add = {
        val instrument = Instrument.fromInput(market, typed)
        if (instrument == null) {
            invalid = true
        } else {
            onAdd(instrument)
            typed = ""
            invalid = false
        }
    }
    val label = stringResource(R.string.market_add_label)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(8.dp),
    ) {
        BasicText(
            text = label,
            style = KiriType.caption.copy(color = colors.onSurfaceMuted),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            for (option in Market.entries) {
                val on = option == market
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) colors.accent else colors.control)
                        .selectable(selected = on, role = Role.RadioButton) {
                            market = option
                            invalid = false
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    BasicText(
                        text = stringResource(
                            when (option) {
                                Market.B3 -> R.string.market_market_b3
                                Market.US -> R.string.market_market_us
                                Market.FX -> R.string.market_market_fx
                            },
                        ),
                        style = KiriType.body.copy(
                            color = if (on) colors.onAccent else colors.onSurface,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            KiriTextField(
                value = typed,
                onValueChange = {
                    typed = it
                    invalid = false
                },
                label = label,
                placeholder = stringResource(
                    when (market) {
                        Market.B3 -> R.string.market_add_hint_b3
                        Market.US -> R.string.market_add_hint_us
                        Market.FX -> R.string.market_add_hint_fx
                    },
                ),
                capitalize = true,
                onDone = add,
                modifier = Modifier.weight(1f),
            )
            TextButton(text = stringResource(R.string.market_add), enabled = typed.isNotBlank(), onClick = add)
        }
        if (invalid) {
            BasicText(
                text = stringResource(R.string.market_add_invalid),
                style = KiriType.caption.copy(color = colors.onSurface),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun KiriTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    capitalize: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = KiriType.body.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.control)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (value.isEmpty()) {
                    BasicText(text = placeholder, style = KiriType.body.copy(color = colors.onSurfaceMuted))
                }
                field()
            }
        },
    )
}

@Composable
private fun TextButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = KiriTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) colors.accent else colors.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BasicText(
            text = text,
            style = KiriType.body.copy(color = if (enabled) colors.onAccent else colors.onSurfaceMuted),
        )
    }
}

private enum class Mark { UP, DOWN, REMOVE, CHECK }

@Composable
private fun MarkIcon(mark: Mark, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color = color,
            start = Offset(x1 * size.width, y1 * size.height),
            end = Offset(x2 * size.width, y2 * size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        when (mark) {
            Mark.UP -> {
                line(0.2f, 0.62f, 0.5f, 0.32f)
                line(0.5f, 0.32f, 0.8f, 0.62f)
            }

            Mark.DOWN -> {
                line(0.2f, 0.38f, 0.5f, 0.68f)
                line(0.5f, 0.68f, 0.8f, 0.38f)
            }

            Mark.REMOVE -> {
                line(0.25f, 0.25f, 0.75f, 0.75f)
                line(0.75f, 0.25f, 0.25f, 0.75f)
            }

            Mark.CHECK -> {
                line(0.2f, 0.52f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.8f, 0.28f)
            }
        }
    }
}

@Composable
private fun SmallButton(mark: Mark, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = KiriTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        MarkIcon(mark, if (enabled) colors.onSurface else colors.onSurfaceMuted.copy(alpha = 0.35f))
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    val colors = KiriTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) colors.accent else colors.control),
    ) {
        if (checked) MarkIcon(Mark.CHECK, colors.onAccent, Modifier.size(18.dp))
    }
}
