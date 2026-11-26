package io.github.johankrs.kiri.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import kotlinx.coroutines.delay

private const val HOLD_DELAY_MS = 450L
private const val HOLD_REPEAT_MS = 70L

enum class Glyph {
    MINUS, PLUS, BACK, FORWARD, CLOSE, INFO, SHAPES, TUNE, CHECK, WIDGETS, PICTURE, EDIT, STORE, UNINSTALL, EYE, HIDE,
    PAGES, SAVE, OPEN, UNDO, FILE,
}

/** Small line icons drawn by hand, so no icon library is needed. Thin lines: they are quiet. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color = color,
            start = Offset(x1 * size.width, y1 * size.height),
            end = Offset(x2 * size.width, y2 * size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        when (glyph) {
            Glyph.MINUS -> line(0.2f, 0.5f, 0.8f, 0.5f)
            Glyph.PLUS -> {
                line(0.2f, 0.5f, 0.8f, 0.5f)
                line(0.5f, 0.2f, 0.5f, 0.8f)
            }

            Glyph.BACK -> {
                line(0.62f, 0.2f, 0.32f, 0.5f)
                line(0.32f, 0.5f, 0.62f, 0.8f)
            }

            Glyph.FORWARD -> {
                line(0.38f, 0.2f, 0.68f, 0.5f)
                line(0.68f, 0.5f, 0.38f, 0.8f)
            }

            Glyph.CLOSE -> {
                line(0.25f, 0.25f, 0.75f, 0.75f)
                line(0.75f, 0.25f, 0.25f, 0.75f)
            }

            Glyph.CHECK -> {
                line(0.2f, 0.53f, 0.41f, 0.73f)
                line(0.41f, 0.73f, 0.8f, 0.29f)
            }

            Glyph.INFO -> {
                drawCircle(color = color, radius = size.minDimension * 0.4f, style = Stroke(width = stroke))
                line(0.5f, 0.46f, 0.5f, 0.68f)
                drawCircle(
                    color = color,
                    radius = stroke * 0.65f,
                    center = Offset(size.width * 0.5f, size.height * 0.31f),
                )
            }

            // A square and a circle: the shapes an icon can take. Stands for "change the icon".
            Glyph.SHAPES -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
                    size = Size(size.width * 0.5f, size.height * 0.5f),
                    cornerRadius = CornerRadius(size.width * 0.12f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.24f,
                    center = Offset(size.width * 0.64f, size.height * 0.64f),
                    style = Stroke(width = stroke),
                )
            }

            // Two sliders. Stands for "adjust".
            Glyph.TUNE -> {
                line(0.15f, 0.33f, 0.85f, 0.33f)
                line(0.15f, 0.67f, 0.85f, 0.67f)
                val knob = stroke * 1.5f
                drawCircle(color = color, radius = knob, center = Offset(size.width * 0.64f, size.height * 0.33f))
                drawCircle(color = color, radius = knob, center = Offset(size.width * 0.36f, size.height * 0.67f))
            }

            // A wide pane over two small ones: widgets of different sizes on a grid.
            Glyph.WIDGETS -> {
                fun pane(left: Float, top: Float, width: Float, height: Float) = drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * left, size.height * top),
                    size = Size(size.width * width, size.height * height),
                    cornerRadius = CornerRadius(size.width * 0.1f),
                    style = Stroke(width = stroke),
                )
                pane(0.15f, 0.15f, 0.7f, 0.3f)
                pane(0.15f, 0.6f, 0.3f, 0.25f)
                pane(0.6f, 0.6f, 0.25f, 0.25f)
            }

            // A frame with hills in it: a picture. Stands for the wallpaper.
            Glyph.PICTURE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.15f, size.height * 0.2f),
                    size = Size(size.width * 0.7f, size.height * 0.6f),
                    cornerRadius = CornerRadius(size.width * 0.12f),
                    style = Stroke(width = stroke),
                )
                line(0.2f, 0.7f, 0.38f, 0.5f)
                line(0.38f, 0.5f, 0.52f, 0.64f)
                line(0.52f, 0.64f, 0.62f, 0.55f)
                line(0.62f, 0.55f, 0.8f, 0.72f)
            }

            // A pencil. Stands for "edit": the icon and the name of an app.
            Glyph.EDIT -> {
                outline(stroke, color) {
                    moveTo(0.2f, 0.8f)
                    lineTo(0.23f, 0.63f)
                    lineTo(0.65f, 0.21f)
                    quadraticTo(0.79f, 0.21f, 0.79f, 0.35f)
                    lineTo(0.37f, 0.77f)
                    close()
                }
                line(0.58f, 0.28f, 0.72f, 0.42f)
            }

            // A shopping bag. Stands for "the app's page in the store".
            Glyph.STORE -> {
                outline(stroke, color) {
                    moveTo(0.21f, 0.35f)
                    lineTo(0.79f, 0.35f)
                    lineTo(0.75f, 0.82f)
                    lineTo(0.25f, 0.82f)
                    close()
                }
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.37f, size.height * 0.16f),
                    size = Size(size.width * 0.26f, size.height * 0.26f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                line(0.37f, 0.29f, 0.37f, 0.35f)
                line(0.63f, 0.29f, 0.63f, 0.35f)
            }

            // A bin. Stands for "uninstall".
            Glyph.UNINSTALL -> {
                line(0.18f, 0.28f, 0.82f, 0.28f)
                outline(stroke, color) {
                    moveTo(0.4f, 0.28f)
                    lineTo(0.4f, 0.2f)
                    lineTo(0.6f, 0.2f)
                    lineTo(0.6f, 0.28f)
                }
                outline(stroke, color) {
                    moveTo(0.26f, 0.28f)
                    lineTo(0.3f, 0.81f)
                    lineTo(0.7f, 0.81f)
                    lineTo(0.74f, 0.28f)
                }
                line(0.43f, 0.43f, 0.43f, 0.66f)
                line(0.57f, 0.43f, 0.57f, 0.66f)
            }

            // An open eye. Stands for "shown".
            Glyph.EYE -> eye(stroke, color, struck = 0f)

            // An eye struck through. Stands for "hide" and "hidden".
            Glyph.HIDE -> eye(stroke, color, struck = 1f)

            // Two tall pages side by side. Stands for the pages of the home screen.
            Glyph.PAGES -> {
                fun page(left: Float) = drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * left, size.height * 0.16f),
                    size = Size(size.width * 0.29f, size.height * 0.68f),
                    cornerRadius = CornerRadius(size.width * 0.08f),
                    style = Stroke(width = stroke),
                )
                page(0.17f)
                page(0.54f)
            }

            // An arrow down onto a line: keeping something, as a file.
            Glyph.SAVE -> {
                line(0.5f, 0.16f, 0.5f, 0.62f)
                line(0.31f, 0.44f, 0.5f, 0.63f)
                line(0.69f, 0.44f, 0.5f, 0.63f)
                line(0.2f, 0.82f, 0.8f, 0.82f)
            }

            // A folder with an arrow coming out of it: opening a file kept somewhere.
            Glyph.OPEN -> {
                outline(stroke, color) {
                    moveTo(0.14f, 0.3f)
                    quadraticTo(0.14f, 0.22f, 0.22f, 0.22f)
                    lineTo(0.4f, 0.22f)
                    lineTo(0.48f, 0.3f)
                    lineTo(0.78f, 0.3f)
                    quadraticTo(0.86f, 0.3f, 0.86f, 0.38f)
                    lineTo(0.86f, 0.74f)
                    quadraticTo(0.86f, 0.82f, 0.78f, 0.82f)
                    lineTo(0.22f, 0.82f)
                    quadraticTo(0.14f, 0.82f, 0.14f, 0.74f)
                    close()
                }
                line(0.5f, 0.71f, 0.5f, 0.45f)
                line(0.39f, 0.55f, 0.5f, 0.44f)
                line(0.61f, 0.55f, 0.5f, 0.44f)
            }

            // An arrow that turns back: going back to how it was.
            Glyph.UNDO -> {
                outline(stroke, color) {
                    moveTo(0.3f, 0.36f)
                    lineTo(0.62f, 0.36f)
                    quadraticTo(0.84f, 0.36f, 0.84f, 0.57f)
                    quadraticTo(0.84f, 0.78f, 0.62f, 0.78f)
                    lineTo(0.38f, 0.78f)
                }
                line(0.3f, 0.36f, 0.44f, 0.22f)
                line(0.3f, 0.36f, 0.44f, 0.5f)
            }

            // A sheet with a folded corner: a file.
            Glyph.FILE -> {
                outline(stroke, color) {
                    moveTo(0.26f, 0.14f)
                    lineTo(0.58f, 0.14f)
                    lineTo(0.76f, 0.32f)
                    lineTo(0.76f, 0.86f)
                    lineTo(0.26f, 0.86f)
                    close()
                }
                outline(stroke, color) {
                    moveTo(0.56f, 0.15f)
                    lineTo(0.56f, 0.34f)
                    lineTo(0.75f, 0.34f)
                }
            }
        }
    }
}

/**
 * An eye, and over it, as far as [struck] goes (0 none, 1 all of it), the line that strikes it
 * through: drawn growing, an eye can be seen closing instead of being swapped for another.
 */
private fun DrawScope.eye(stroke: Float, color: Color, struck: Float) {
    // On a layer of its own, so the line can clear a narrow gap through the eye as it goes.
    drawIntoCanvas { canvas -> canvas.saveLayer(Rect(Offset.Zero, size), Paint()) }
    outline(stroke, color) {
        moveTo(0.12f, 0.5f)
        quadraticTo(0.5f, 0.08f, 0.88f, 0.5f)
        quadraticTo(0.5f, 0.92f, 0.12f, 0.5f)
        close()
    }
    drawCircle(
        color = color,
        radius = size.minDimension * 0.12f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(width = stroke),
    )
    if (struck > 0f) {
        val share = struck.coerceAtMost(1f)
        val start = Offset(size.width * 0.2f, size.height * 0.18f)
        val end = start + (Offset(size.width * 0.8f, size.height * 0.82f) - start) * share
        drawLine(Color.Black, start, end, strokeWidth = stroke * 3f, cap = StrokeCap.Round, blendMode = BlendMode.Clear)
        drawLine(color, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
    drawIntoCanvas { canvas -> canvas.restore() }
}

/** The eye of [Glyph.EYE], struck through as far as [struck] goes, at the size of a glyph. */
@Composable
fun EyeGlyph(struck: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        eye(1.6.dp.toPx(), color, struck())
    }
}

/**
 * Draws the line [shape] traces, given in shares of the glyph's size (0 to 1 both ways), with
 * round ends and corners like every other glyph.
 */
private fun DrawScope.outline(stroke: Float, color: Color, shape: GlyphPath.() -> Unit) {
    val path = Path()
    GlyphPath(path, size.width, size.height).shape()
    drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A [Path] drawn in shares of a [width] by [height] box. */
private class GlyphPath(private val path: Path, private val width: Float, private val height: Float) {
    fun moveTo(x: Float, y: Float) = path.moveTo(x * width, y * height)
    fun lineTo(x: Float, y: Float) = path.lineTo(x * width, y * height)
    fun quadraticTo(cx: Float, cy: Float, x: Float, y: Float) = path.quadraticTo(cx * width, cy * height, x * width, y * height)
    fun close() = path.close()
}

/**
 * A settings screen: back button, title, then [content]. It has no background of its own: all
 * settings lie on one sheet of glass, drawn once behind them (see `kiriSheet`). With
 * [focusFirst], a remote or a keyboard starts on the first setting instead of on nothing.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    focusFirst: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KiriTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Touches on the screen's empty parts stop here instead of reaching what is behind.
            .pointerInput(Unit) { }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        ) {
            val backLabel = stringResource(R.string.action_back)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backLabel },
            ) {
                GlyphIcon(Glyph.BACK, colors.onSurface, Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
            BasicText(text = title, style = KiriType.title.copy(color = colors.onSurface))
        }
        if (focusFirst) {
            val first = remember { FocusRequester() }
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                if (inputMode.inputMode == InputMode.Keyboard) {
                    // Once laid out, as if coming down from the title: the top setting.
                    withFrameNanos { }
                    runCatching { first.requestFocus(FocusDirection.Down) }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(first),
                content = content,
            )
        } else {
            content()
        }
    }
}

/** The name of a block of settings, above it. */
@Composable
fun SectionTitle(text: String) {
    BasicText(
        text = text.uppercase(),
        style = KiriType.section.copy(color = KiriTheme.colors.onSurfaceMuted),
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** Whether what is being drawn sits inside a [SettingsGroup], which then gives it its card. */
private val LocalInGroup = staticCompositionLocalOf { false }

private val GroupShape = RoundedCornerShape(22.dp)

/** Where the faint line between two rows of a group starts, so it lines up with their text. */
private val SeamInset = 18.dp

/**
 * A block of settings that belong together: one card, its rows parted by faint lines, instead
 * of a card for every row. Each child is one row, as wide as the block.
 *
 * The controls of this file know when they are inside one: there they are plain rows, and
 * alone they are a block of their own.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = KiriTheme.colors
    // Where one row ends and the next begins, in pixels from the top.
    var seams by remember { mutableStateOf(IntArray(0)) }
    CompositionLocalProvider(LocalInGroup provides true) {
        Layout(
            content = content,
            modifier = modifier
                .clip(GroupShape)
                .background(colors.card)
                .drawWithContent {
                    drawContent()
                    val inset = SeamInset.toPx()
                    for (seam in seams) {
                        drawLine(
                            color = colors.divider,
                            start = Offset(inset, seam.toFloat()),
                            end = Offset(size.width, seam.toFloat()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                },
        ) { measurables, constraints ->
            val row = Constraints(
                minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
                maxWidth = constraints.maxWidth,
                // A single row fills a block that was asked to be taller, such as one of a pair.
                minHeight = if (measurables.size == 1 && constraints.hasBoundedHeight) constraints.minHeight else 0,
            )
            val placeables = measurables.map { it.measure(row) }
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
            val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width.coerceAtLeast(constraints.minWidth), height) {
                val found = ArrayList<Int>(placeables.size)
                var y = 0
                for (placeable in placeables) {
                    // A row that takes no room, such as one that is hidden, parts nothing.
                    if (placeable.height > 0 && y > 0) found += y
                    placeable.place(0, y)
                    y += placeable.height
                }
                val next = found.toIntArray()
                if (!next.contentEquals(seams)) seams = next
            }
        }
    }
}

/**
 * One row of a block that is too long to be laid out at once, such as every app of the phone in
 * a list that only builds what is on screen: drawn here, a row at a time, it looks the same as a
 * [SettingsGroup]. The [first] row has the round top corners, the [last] the round bottom
 * ones, and every row but the first has the faint line above it, from [seamInset] on.
 */
@Composable
fun Modifier.groupPiece(first: Boolean, last: Boolean, seamInset: Dp = SeamInset): Modifier {
    val colors = KiriTheme.colors
    val corner = 22.dp
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp,
        topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp,
        bottomEnd = if (last) corner else 0.dp,
    )
    return this
        .clip(shape)
        .background(colors.card)
        .drawWithContent {
            drawContent()
            if (!first) {
                drawLine(
                    color = colors.divider,
                    start = Offset(seamInset.toPx(), 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
}

/** [content] as a row of the group it is in, or as a block of its own when it stands alone. */
@Composable
private fun GroupRow(modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    if (LocalInGroup.current) content(modifier) else SettingsGroup(modifier) { content(Modifier) }
}

/** Something the person should know: a short [title] and what it means. */
@Composable
fun InfoCard(title: String, text: String, modifier: Modifier = Modifier) {
    val colors = KiriTheme.colors
    GroupRow(modifier) { row ->
        Column(row.padding(horizontal = 18.dp, vertical = 12.dp)) {
            BasicText(text = title, style = KiriType.body.copy(color = colors.onSurface))
            Spacer(Modifier.height(2.dp))
            BasicText(text = text, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
        }
    }
}

/** A row that leads to another settings screen. */
@Composable
fun SettingsLinkRow(title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KiriTheme.colors
    GroupRow(modifier) { row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = row
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(text = title, style = KiriType.body.copy(color = colors.onSurface))
                Spacer(Modifier.height(2.dp))
                BasicText(text = summary, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
            }
            Spacer(Modifier.width(12.dp))
            GlyphIcon(Glyph.FORWARD, colors.onSurfaceMuted, Modifier.size(18.dp))
        }
    }
}

/** A stepper puts its name beside its buttons only when this much width is left for the name. */
private val StepperNameRoom = 104.dp

/**
 * The name of a stepper and its buttons: side by side where there is room, the buttons at the
 * far end, and otherwise the name above the buttons, which then take the whole width.
 */
private val StepperLayout = MeasurePolicy { measurables, constraints ->
    val name = measurables[0]
    val buttons = measurables[1]
    val start = SeamInset.roundToPx()
    val end = 4.dp.roundToPx()
    val bounded = constraints.hasBoundedWidth
    val buttonsWidth = buttons.minIntrinsicWidth(Constraints.Infinity)
    val beside = !bounded || constraints.maxWidth - start - end - buttonsWidth >= StepperNameRoom.roundToPx()
    if (beside) {
        val placedButtons = buttons.measure(Constraints())
        val nameRoom = if (bounded) {
            (constraints.maxWidth - start - end - placedButtons.width - 8.dp.roundToPx()).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val placedName = name.measure(Constraints(maxWidth = nameRoom))
        val width = if (bounded) constraints.maxWidth else start + placedName.width + placedButtons.width + end
        val height = maxOf(placedName.height + 16.dp.roundToPx(), placedButtons.height, 52.dp.roundToPx())
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placedName.placeRelative(start, (height - placedName.height) / 2)
            placedButtons.placeRelative(width - end - placedButtons.width, (height - placedButtons.height) / 2)
        }
    } else {
        val side = 14.dp.roundToPx()
        val top = 10.dp.roundToPx()
        val inner = (constraints.maxWidth - 2 * end).coerceAtLeast(0)
        val placedName = name.measure(Constraints(maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)))
        val placedButtons = buttons.measure(Constraints(minWidth = inner, maxWidth = inner))
        val height = (top + placedName.height + placedButtons.height + end)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            placedName.placeRelative(side, top)
            // At the bottom, so in a row of blocks the buttons line up whatever the names take.
            placedButtons.placeRelative(end, height - end - placedButtons.height)
        }
    }
}

/**
 * A setting changed a step at a time: an amount with minus and plus, or, when [named], one of a
 * few names gone through with arrows either side. One tap is one step; holding a button keeps
 * stepping. The buttons are only their signs, with no background of their own.
 *
 * The value always has the same room, centred in it: [StepValueRoom], or more if one of
 * [values] (all it can show) is wider. So the buttons never move as it changes, and they line
 * up from row to row. Menus follow the same pattern (`ItemMenu`).
 */
@Composable
fun StepperCard(
    label: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    values: List<String> = emptyList(),
    named: Boolean = false,
    /** Lets a screen put a remote's focus on the first button, for it to start there. */
    firstButton: FocusRequester? = null,
) {
    val colors = KiriTheme.colors
    val valueStyle = KiriType.value.copy(color = colors.onSurface)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val room = remember(values, valueText, measurer, density) {
        val widest = (values + valueText).maxOf { measurer.measure(it, valueStyle, maxLines = 1).size.width }
        maxOf(StepValueRoom, with(density) { widest.toDp() })
    }
    GroupRow(modifier) { row ->
        Layout(
            content = {
                BasicText(text = label, style = KiriType.body.copy(color = colors.onSurface))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StepButton(
                        glyph = if (named) Glyph.BACK else Glyph.MINUS,
                        description = stringResource(if (named) R.string.action_previous else R.string.action_decrease, label),
                        enabled = canDecrease,
                        onStep = onDecrease,
                        modifier = if (firstButton != null) Modifier.focusRequester(firstButton) else Modifier,
                    )
                    BasicText(
                        text = valueText,
                        style = valueStyle,
                        maxLines = 1,
                        modifier = Modifier.width(room),
                    )
                    StepButton(
                        glyph = if (named) Glyph.FORWARD else Glyph.PLUS,
                        description = stringResource(if (named) R.string.action_next else R.string.action_increase, label),
                        enabled = canIncrease,
                        onStep = onIncrease,
                    )
                }
            },
            modifier = row,
            measurePolicy = StepperLayout,
        )
    }
}

/** The room every stepper gives its value, enough for "Padrão", "100%" or "30 min". */
val StepValueRoom = 64.dp

@Composable
private fun StepButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    val stillEnabled by rememberUpdatedState(enabled)
    // True once holding has started stepping, so the click that follows the release is not
    // counted as one more step.
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(HOLD_DELAY_MS)
            repeated = true
            while (stillEnabled) {
                step()
                delay(HOLD_REPEAT_MS)
            }
        } else {
            // Covers a hold that ended without a click (finger dragged away, limit reached).
            delay(100)
            repeated = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (repeated) repeated = false else step()
            }
            .semantics { contentDescription = description },
    ) {
        GlyphIcon(
            glyph = glyph,
            color = if (enabled) colors.onSurface else colors.onSurfaceMuted.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** An on/off setting, with what it means under its name when there is [summary]. The whole row is the touch target. */
@Composable
fun ToggleCard(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val colors = KiriTheme.colors
    GroupRow(modifier) { row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = row
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .heightIn(min = if (summary == null) 52.dp else 56.dp)
                .padding(start = 18.dp, end = 14.dp, top = if (summary == null) 8.dp else 9.dp, bottom = if (summary == null) 8.dp else 9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(text = label, style = KiriType.body.copy(color = colors.onSurface))
                if (summary != null) {
                    BasicText(text = summary, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
                }
            }
            Spacer(Modifier.width(12.dp))

            val thumbOffset = animateDpAsState(if (checked) 18.dp else 0.dp, label = "switch")
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 26.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.accent else colors.control)
                    .padding(4.dp),
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(thumbOffset.value.roundToPx(), 0) }
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (checked) colors.onAccent else colors.onSurfaceMuted),
                )
            }
        }
    }
}

/**
 * A setting picked from a short list. Closed, the row shows the current choice; a tap opens
 * the list right there, and picking an option closes it again. The option in use is the one
 * with the tick.
 */
@Composable
fun ChoiceCard(
    label: String,
    valueText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    GroupRow(modifier) { row ->
        Column(row) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .heightIn(min = 56.dp)
                    .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(text = label, style = KiriType.body.copy(color = colors.onSurface))
                    Spacer(Modifier.height(2.dp))
                    BasicText(text = valueText, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
                }
                Spacer(Modifier.width(12.dp))
                // Points down while closed and up while open.
                GlyphIcon(
                    glyph = Glyph.FORWARD,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = if (expanded) -90f else 90f },
                )
            }
            if (expanded) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                            .heightIn(min = 46.dp)
                            .padding(start = 30.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        BasicText(
                            text = option,
                            style = KiriType.body.copy(
                                color = if (selected) colors.onSurface else colors.onSurfaceMuted,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Spacer(Modifier.width(12.dp))
                            GlyphIcon(Glyph.CHECK, colors.accent, Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
