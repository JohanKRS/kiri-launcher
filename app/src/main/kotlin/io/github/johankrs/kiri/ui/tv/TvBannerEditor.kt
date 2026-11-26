package io.github.johankrs.kiri.ui.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppNames
import io.github.johankrs.kiri.core.tv.Console
import io.github.johankrs.kiri.core.tv.ConsoleFamily
import io.github.johankrs.kiri.core.tv.ConsoleStyle
import io.github.johankrs.kiri.core.tv.TvArrange
import io.github.johankrs.kiri.core.tv.TvBanner
import io.github.johankrs.kiri.core.tv.TvColor
import io.github.johankrs.kiri.core.tv.TvConsoles
import io.github.johankrs.kiri.core.tv.TvDirection
import io.github.johankrs.kiri.core.tv.TvGlyph
import io.github.johankrs.kiri.core.tv.TvInputNames
import io.github.johankrs.kiri.data.AppRepository

private const val EDITOR_WIDTH = 700f
private const val EDITOR_HEIGHT = 440f
private const val LEFT_COLUMN = 250f
private const val RIGHT_COLUMN = EDITOR_WIDTH - 2 * 28f - LEFT_COLUMN - 28f

/**
 * "Banner and name" of one place on the TV's home screen. An input gets a ready-made banner of
 * a console (brand, model and one of the styles) or a drawing on a color; an app gets its own
 * banner or Kiri's. Every change is saved and seen at once, here and on the home screen.
 */
@Composable
internal fun TvBannerEditor(
    open: EditorOpen,
    item: TvShown,
    banner: TvBanner?,
    screenH: Float,
    repository: AppRepository?,
    actions: TvHomeActions,
    rootFocus: FocusRequester,
    onClose: () -> Unit,
    onKeys: (TvKeyActions) -> Unit,
) {
    val units = LocalTvUnits.current
    val targets = open.targets
    val tvItem = item.item ?: return
    val input = item.input
    val app = item.app
    val classic = stringResource(R.string.tv_console_classic)
    fun fullName(console: Console): String = console.fullName.replace(TvConsoles.WORD_CLASSIC, classic)

    val current: TvBanner = when {
        input != null -> banner?.takeIf { it is TvBanner.Console || it is TvBanner.Drawn } ?: TvBanner.forInput(input.kind)
        else -> if (banner == TvBanner.Kiri) TvBanner.Kiri else TvBanner.Own
    }
    val console = (current as? TvBanner.Console)?.let { TvConsoles.byId(it.consoleId) }
    val drawn = current as? TvBanner.Drawn

    var name by remember(open) { mutableStateOf(TextFieldValue(item.name, TextRange(item.name.length))) }
    var editingName by remember(open) { mutableStateOf(false) }
    val nameFocus = remember(open) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editingName) {
        if (editingName) {
            runCatching { nameFocus.requestFocus() }
            keyboard?.show()
        }
    }

    fun saveName(typed: String = name.text) {
        when {
            app != null -> actions.setAppName(app, AppNames.clean(typed, app.ownLabel))
            input != null -> actions.setInputName(input, TvInputNames.clean(typed, input.ownName))
        }
    }

    fun finishName() {
        if (!editingName) return
        editingName = false
        keyboard?.hide()
        runCatching { rootFocus.requestFocus() }
        saveName()
    }

    fun setName(text: String) {
        name = TextFieldValue(text, TextRange(text.length))
        saveName(text)
    }

    /** A console chosen: the name follows it, unless the person typed one of their own. */
    fun choose(next: Console?) {
        if (input == null) return
        val style = (current as? TvBanner.Console)?.style ?: ConsoleStyle.NOITE
        actions.setBanner(tvItem, next?.let { TvBanner.Console(it.id, style) })
        val automatic = console?.let(::fullName) ?: input.ownName
        if (name.text.trim() == automatic || name.text.trim() == input.ownName) setName(next?.let(::fullName) ?: input.ownName)
    }

    fun step(id: String, by: Int) {
        when (id) {
            "fam" -> choose(TvConsoles.stepFamily(console, by))
            "model" -> console?.let { choose(TvConsoles.stepModel(it, by)) }
            "kind" -> actions.setBanner(tvItem, if (current == TvBanner.Kiri) null else TvBanner.Kiri)
        }
    }

    fun press(id: String) {
        when {
            id == "name" -> editingName = true
            id == "fam" || id == "model" || id == "kind" -> step(id, 1)
            id == "restore" -> {
                actions.setBanner(tvItem, null)
                setName(app?.ownLabel ?: input?.ownName.orEmpty())
            }
            id == "done" -> {
                if (editingName) finishName() else saveName()
                onClose()
            }
            id.startsWith("style:") -> console?.let { c ->
                ConsoleStyle.fromKey(id.removePrefix("style:"))?.let { actions.setBanner(tvItem, TvBanner.Console(c.id, it)) }
            }
            id.startsWith("glyph:") -> TvGlyph.fromKey(id.removePrefix("glyph:"))?.let {
                actions.setBanner(tvItem, TvBanner.Drawn(it, drawn?.color ?: TvColor.GRAFITE))
            }
            id.startsWith("color:") -> TvColor.fromKey(id.removePrefix("color:"))?.let {
                actions.setBanner(tvItem, TvBanner.Drawn(drawn?.glyph ?: TvGlyph.TV, it))
            }
        }
    }

    // Where the focus starts: on what is chosen now.
    if (targets.focused == null) {
        targets.focused = when {
            app != null -> "kind"
            console != null -> "style:${(current as TvBanner.Console).style.key}"
            else -> "glyph:${drawn?.glyph?.key ?: TvGlyph.TV.key}"
        }
    }
    onKeys(
        TvKeyActions(
            ok = { targets.focused?.let(::press) },
            move = { direction ->
                val at = targets.focused
                if (editingName) finishName()
                val sideways = direction == TvDirection.LEFT || direction == TvDirection.RIGHT
                if (sideways && (at == "fam" || at == "model" || at == "kind")) {
                    step(at, if (direction == TvDirection.LEFT) -1 else 1)
                } else {
                    targets.move(direction)
                }
            },
            back = {
                if (editingName) {
                    finishName()
                } else {
                    saveName()
                    onClose()
                }
            },
        ),
    )

    val appear = remember(open) { Animatable(0f) }
    LaunchedEffect(open) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    val caption = TextStyle(fontFamily = TvFont, fontSize = units.sp(13f), color = Color.White.copy(alpha = 0.7f))
    val preview = when (val art = item.art) {
        is BannerArt.Drawn -> art.copy(name = name.text.ifBlank { input?.ownName.orEmpty() })
        is BannerArt.AppOwn -> if (current == TvBanner.Kiri) BannerArt.AppGlass(art.app) else art
        is BannerArt.AppGlass -> if (current == TvBanner.Kiri) art else BannerArt.AppOwn(art.app)
        else -> art
    }

    Box(Modifier.fillMaxSize().zIndex(30f)) {
        Row(
            modifier = Modifier
                .offset(units.dp((TvArrange.WIDTH - EDITOR_WIDTH) / 2f), units.dp(((screenH - EDITOR_HEIGHT) / 2f).coerceAtLeast(12f)))
                .size(units.dp(EDITOR_WIDTH), units.dp(EDITOR_HEIGHT))
                .graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    val s = 0.96f + 0.04f * appear.value
                    scaleX = s
                    scaleY = s
                }
                .then(targets.rootModifier)
                .tvGlass(RoundedCornerShape(units.dp(26f)), strong = true)
                .padding(horizontal = units.dp(28f), vertical = units.dp(26f)),
        ) {
            Column(Modifier.width(units.dp(LEFT_COLUMN))) {
                BasicText(
                    stringResource(R.string.tv_menu_edit),
                    style = TextStyle(fontFamily = TvFont, fontWeight = FontWeight(500), fontSize = units.sp(22f), color = Color.White),
                )
                Spacer(Modifier.height(units.dp(4f)))
                BasicText(input?.let { stringResource(R.string.tv_kind_input, it.ownName) } ?: stringResource(R.string.tv_kind_app), style = caption)
                Spacer(Modifier.height(units.dp(22f)))
                Box(
                    Modifier.drawBehind {
                        val line = units.px(2f)
                        drawRoundRect(
                            Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(-line / 2f, -line / 2f),
                            size = Size(size.width + line, size.height + line),
                            cornerRadius = CornerRadius(size.width * (12f / 148f) + line),
                            style = Stroke(line),
                        )
                    },
                ) {
                    TvBannerFace(preview, units.dp(LEFT_COLUMN), repository)
                }
                if (input != null) {
                    Spacer(Modifier.height(units.dp(16f)))
                    Label(stringResource(R.string.tv_editor_ready))
                    Selector(
                        id = "fam",
                        targets = targets,
                        value = console?.family?.let { familyName(it) } ?: stringResource(R.string.tv_editor_none),
                        width = 124f,
                    )
                    Spacer(Modifier.height(units.dp(4f)))
                    Selector(
                        id = "model",
                        targets = targets,
                        value = console?.pickerName?.replace(TvConsoles.WORD_CLASSIC, classic) ?: "—",
                        width = 124f,
                        enabled = console != null,
                    )
                }
                Spacer(Modifier.height(units.dp(10f)))
                BasicText(stringResource(R.string.tv_editor_everywhere), style = caption)
            }
            Spacer(Modifier.width(units.dp(28f)))
            Column(
                verticalArrangement = Arrangement.spacedBy(units.dp(14f)),
                modifier = Modifier
                    .width(units.dp(RIGHT_COLUMN))
                    .fillMaxHeight(),
            ) {
                Column {
                    Label(stringResource(R.string.tv_editor_name))
                    val shape = RoundedCornerShape(units.dp(12f))
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(units.dp(42f))
                            .tvTarget(targets, "name")
                            .background(Color.White.copy(alpha = 0.06f), shape)
                            .focusLight(targets.focused == "name", shape)
                            .padding(horizontal = units.dp(12f)),
                    ) {
                        val limit = if (input != null) TvInputNames.MAX_LENGTH else AppNames.MAX_LENGTH
                        BasicTextField(
                            value = name,
                            onValueChange = { typed -> if (typed.text.length <= limit) name = typed },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = TvFont, fontSize = units.sp(18f), color = Color.White),
                            cursorBrush = SolidColor(if (editingName) Color.White else Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { finishName() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocus)
                                .focusProperties { canFocus = editingName },
                        )
                    }
                }
                when {
                    app != null -> Column {
                        Label(stringResource(R.string.tv_editor_banner))
                        val shape = RoundedCornerShape(units.dp(18f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(units.dp(54f))
                                .background(Color.White.copy(alpha = 0.07f), shape)
                                .tvTarget(targets, "kind")
                                .focusLight(targets.focused == "kind", shape)
                                .padding(start = units.dp(16f), end = units.dp(8f)),
                        ) {
                            BasicText(
                                stringResource(R.string.tv_editor_banner),
                                style = TextStyle(fontFamily = TvFont, fontSize = units.sp(15.5f), color = Color.White),
                                modifier = Modifier.weight(1f),
                            )
                            Arrows(
                                value = stringResource(if (current == TvBanner.Kiri) R.string.tv_banner_kiri else R.string.tv_banner_own),
                                width = 84f,
                            )
                        }
                    }
                    console != null -> Column {
                        Label(stringResource(R.string.tv_editor_style))
                        val chosen = (current as TvBanner.Console).style
                        Tiles(ConsoleStyle.entries, columns = 3, gap = 10f, rowGap = 8f) { style, tileWidth ->
                            StyleTile(console, style, style == chosen, tileWidth, targets, repository)
                        }
                    }
                    else -> Column {
                        Label(stringResource(R.string.tv_editor_drawing))
                        Tiles(TvGlyph.entries, columns = 6, gap = 6f, rowGap = 6f) { glyph, tileWidth ->
                            GlyphTile(glyph, glyph == drawn?.glyph, tileWidth, targets)
                        }
                        Spacer(Modifier.height(units.dp(14f)))
                        Label(stringResource(R.string.tv_editor_color))
                        Row(horizontalArrangement = Arrangement.spacedBy(units.dp(8f))) {
                            for (color in TvColor.entries) ColorTile(color, color == drawn?.color, targets)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(units.dp(10f))) {
                    EditorButton("restore", stringResource(R.string.tv_editor_original), null, main = false, targets = targets)
                    EditorButton("done", stringResource(R.string.tv_editor_done), TvIcons.CHECK, main = true, targets = targets)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    val units = LocalTvUnits.current
    BasicText(
        text.uppercase(),
        style = TextStyle(
            fontFamily = TvFont,
            fontWeight = FontWeight(500),
            fontSize = units.sp(12.5f),
            letterSpacing = units.sp(1f),
            color = Color.White.copy(alpha = 0.62f),
        ),
        modifier = Modifier.padding(bottom = units.dp(6f)),
    )
}

@Composable
private fun familyName(family: ConsoleFamily): String = stringResource(
    when (family) {
        ConsoleFamily.NINTENDO -> R.string.tv_family_nintendo
        ConsoleFamily.PLAYSTATION -> R.string.tv_family_playstation
        ConsoleFamily.XBOX -> R.string.tv_family_xbox
        ConsoleFamily.SEGA -> R.string.tv_family_sega
        ConsoleFamily.OTHERS -> R.string.tv_family_others
        ConsoleFamily.PC -> R.string.tv_family_pc
    },
)

@Composable
internal fun styleName(style: ConsoleStyle): String = stringResource(
    when (style) {
        ConsoleStyle.NOITE -> R.string.tv_style_noite
        ConsoleStyle.COR -> R.string.tv_style_cor
        ConsoleStyle.VIDRO -> R.string.tv_style_vidro
        ConsoleStyle.RETRO -> R.string.tv_style_retro
        ConsoleStyle.FAIXAS -> R.string.tv_style_faixas
    },
)

/** A value between ‹ and ›: the left and right arrows of the remote change it. */
@Composable
private fun Selector(id: String, targets: TvTargets, value: String, width: Float, enabled: Boolean = true) {
    val units = LocalTvUnits.current
    val shape = RoundedCornerShape(units.dp(12f))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(units.dp(36f))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .then(if (enabled) Modifier.tvTarget(targets, id) else Modifier)
            .focusLight(enabled && targets.focused == id, shape),
    ) {
        Arrows(value, width)
    }
}

@Composable
private fun Arrows(value: String, width: Float) {
    val units = LocalTvUnits.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvLineIcon(TvIcons.LEFT, Color.White.copy(alpha = 0.9f), Modifier.size(units.dp(32f), units.dp(34f)).padding(units.dp(8f)), stroke = 1.8f)
        BasicText(
            value,
            maxLines = 1,
            style = TextStyle(fontFamily = TvFont, fontSize = units.sp(15f), color = Color.White, textAlign = TextAlign.Center),
            modifier = Modifier.width(units.dp(width)),
        )
        TvLineIcon(TvIcons.RIGHT, Color.White.copy(alpha = 0.9f), Modifier.size(units.dp(32f), units.dp(34f)).padding(units.dp(8f)), stroke = 1.8f)
    }
}

/** [entries] in rows of [columns], each tile as wide as the room shares out. */
@Composable
private fun <T> ColumnScope.Tiles(
    entries: List<T>,
    columns: Int,
    gap: Float,
    rowGap: Float,
    tile: @Composable (T, Float) -> Unit,
) {
    val units = LocalTvUnits.current
    val tileWidth = (RIGHT_COLUMN - gap * (columns - 1)) / columns
    Column(verticalArrangement = Arrangement.spacedBy(units.dp(rowGap))) {
        for (row in entries.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(units.dp(gap))) {
                for (entry in row) tile(entry, tileWidth)
            }
        }
    }
}

/** The short line under what is chosen. */
private fun Modifier.chosenMark(chosen: Boolean, units: TvUnits, share: Float = 0.4f): Modifier = drawBehind {
    if (chosen) {
        val w = size.width * (1f - 2f * share)
        drawRoundRect(
            Color.White,
            topLeft = Offset((size.width - w) / 2f, size.height - units.px(3f)),
            size = Size(w, units.px(2f)),
            cornerRadius = CornerRadius(units.px(1f)),
        )
    }
}

@Composable
private fun StyleTile(
    console: Console,
    style: ConsoleStyle,
    chosen: Boolean,
    width: Float,
    targets: TvTargets,
    repository: AppRepository?,
) {
    val units = LocalTvUnits.current
    val id = "style:${style.key}"
    val on = targets.focused == id
    val grow by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "style")
    val shape = RoundedCornerShape(units.dp(12f))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(units.dp(width))
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .tvTarget(targets, id)
            .focusLight(on, shape)
            .chosenMark(chosen, units)
            .padding(start = units.dp(5f), end = units.dp(5f), top = units.dp(5f), bottom = units.dp(6f)),
    ) {
        TvBannerFace(BannerArt.ConsoleArt(console, style), units.dp(width - 10f), repository, corner = units.dp(8f))
        Spacer(Modifier.height(units.dp(4f)))
        BasicText(
            styleName(style),
            style = TextStyle(
                fontFamily = TvFont,
                fontWeight = FontWeight(if (chosen) 600 else 400),
                fontSize = units.sp(12.5f),
                color = Color.White.copy(alpha = if (chosen) 1f else 0.8f),
            ),
        )
    }
}

@Composable
private fun GlyphTile(glyph: TvGlyph, chosen: Boolean, width: Float, targets: TvTargets) {
    val units = LocalTvUnits.current
    val id = "glyph:${glyph.key}"
    val shape = RoundedCornerShape(units.dp(12f))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(units.dp(width))
            .tvTarget(targets, id)
            .focusLight(targets.focused == id, shape)
            .chosenMark(chosen, units, share = 0.3f),
    ) {
        TvLineIcon(
            glyphLines(glyph),
            Color.White.copy(alpha = if (chosen) 1f else 0.8f),
            Modifier.size(units.dp(26f)),
            stroke = if (chosen) 2.1f else 1.5f,
        )
    }
}

@Composable
private fun ColorTile(color: TvColor, chosen: Boolean, targets: TvTargets) {
    val units = LocalTvUnits.current
    val id = "color:${color.key}"
    val shape = RoundedCornerShape(units.dp(12f))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(units.dp(40f))
            .tvTarget(targets, id)
            .focusLight(targets.focused == id, shape)
            .chosenMark(chosen, units, share = 0.3f),
    ) {
        Box(
            Modifier
                .size(units.dp(24f))
                .background(Color(0xFF000000.toInt() or color.rgb), RoundedCornerShape(units.dp(8f)))
                .drawBehind {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.3f),
                        cornerRadius = CornerRadius(units.px(8f)),
                        style = Stroke(units.px(1f)),
                    )
                },
        )
    }
}

@Composable
private fun EditorButton(id: String, label: String, icon: String?, main: Boolean, targets: TvTargets) {
    val units = LocalTvUnits.current
    val shape = RoundedCornerShape(units.dp(12f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(units.dp(42f))
            .then(if (main) Modifier.background(Color.White.copy(alpha = 0.1f), shape) else Modifier)
            .tvTarget(targets, id)
            .focusLight(targets.focused == id, shape)
            .padding(horizontal = units.dp(18f)),
    ) {
        if (icon != null) {
            TvLineIcon(icon, Color.White, Modifier.size(units.dp(19f)))
            Spacer(Modifier.width(units.dp(10f)))
        }
        BasicText(label, style = TextStyle(fontFamily = TvFont, fontSize = units.sp(15f), color = Color.White))
    }
}
