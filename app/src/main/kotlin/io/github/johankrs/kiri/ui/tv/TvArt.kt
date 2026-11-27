package io.github.johankrs.kiri.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.tv.BesideDrawing
import io.github.johankrs.kiri.core.tv.Console
import io.github.johankrs.kiri.core.tv.ConsoleStyle
import io.github.johankrs.kiri.core.tv.TvColor
import io.github.johankrs.kiri.core.tv.TvConsoles
import io.github.johankrs.kiri.core.tv.TvGlyph
import io.github.johankrs.kiri.core.tv.TvLook
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.data.TvPicture
import io.github.johankrs.kiri.ui.live.LiveCanvas
import io.github.johankrs.kiri.ui.live.LiveSurface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The sizes of the TV's screens. Everything there is sized in the units of a screen 960 wide
 * (see `TvArrange`), so on a TV whose screen is wider in dp it is all drawn larger, the same.
 */
@Immutable
internal class TvUnits(val unit: Dp, private val density: Density) {
    fun dp(value: Float): Dp = unit * value
    fun px(value: Float): Float = with(density) { (unit * value).toPx() }

    /** A size of letters that is as many units, whatever size of letters the person chose for the system. */
    fun sp(value: Float): TextUnit = with(density) { (unit * value).toSp() }
}

internal val LocalTvUnits = staticCompositionLocalOf { TvUnits(1.dp, Density(1f)) }

/** Figtree in every weight the TV's banners use: their big names are heavier than anything else in Kiri. */
@OptIn(ExperimentalTextApi::class)
internal val TvFont: FontFamily = FontFamily(
    listOf(400, 500, 600, 750, 800).map { weight ->
        Font(
            resId = R.font.figtree,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

// ---------- lines ----------

private val parsedPaths = HashMap<String, Path>()

/** [data], SVG path data, as a path. Parsed once: the drawings are drawn over and over. */
private fun pathOf(data: String): Path = parsedPaths.getOrPut(data) { PathParser().parsePathString(data).toPath() }

/**
 * Draws the path [data], given on a box [box] units wide, into the box at [left], [top] that is
 * [width] wide, with round ends and corners. [stroke] is the width of the line in units of [box].
 */
internal fun DrawScope.drawLines(
    data: String,
    box: Float,
    left: Float,
    top: Float,
    width: Float,
    color: Color,
    stroke: Float,
    alpha: Float = 1f,
) {
    val k = width / box
    translate(left, top) {
        scale(k, k, pivot = Offset.Zero) {
            drawPath(
                path = pathOf(data),
                color = color,
                alpha = alpha,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** A line drawing of 24 by 24, such as those of the menus. */
@Composable
internal fun TvLineIcon(data: String, color: Color, modifier: Modifier = Modifier, stroke: Float = 1.6f) {
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        drawLines(data, 24f, (size.width - side) / 2f, (size.height - side) / 2f, side, color, stroke)
    }
}

// ---------- banners ----------

/** What one banner shows, with every name already in the person's language. */
@Immutable
internal sealed interface BannerArt {
    /** The banner the app came with; Kiri's when it has none. */
    data class AppOwn(val app: AppEntry) : BannerArt

    /** Kiri's for an app: its icon and its name, on glass. */
    data class AppGlass(val app: AppEntry) : BannerArt

    /** A drawing on a color, with the name and, under it, what the input is. */
    data class Drawn(val glyph: TvGlyph, val color: TvColor, val name: String, val port: String) : BannerArt

    /** A ready-made banner of a console or computer. */
    data class ConsoleArt(val console: Console, val style: ConsoleStyle) : BannerArt

    data class AllApps(val label: String) : BannerArt
}

/** How a banner is cut: its corners at the size of a banner of the home screen. */
internal fun bannerShape(width: Dp): RoundedCornerShape = RoundedCornerShape(width * (12f / 148f))

/**
 * Draws what this holds once, into a picture the graphics card keeps, and from then on only lays
 * that picture down, until something in it changes: moving it or fading it draws nothing again.
 * A TV's graphics card is slow, and it otherwise drew every banner, every widget and the whole
 * wallpaper again at every frame of every move of the focus.
 *
 * Off ([on] false) for what is being shown larger than it is, as a banner with the focus: a kept
 * picture would be stretched, and blur. That one is drawn as it is shown, every time.
 */
internal fun Modifier.kept(on: Boolean = true): Modifier = graphicsLayer {
    compositingStrategy = if (on && !TvProbe.off("layers")) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}

/**
 * The shade that keeps what is written on the wallpaper readable: darker along the top and the
 * bottom, [strength] of the usual. Drawn only where there is any, not over the whole screen.
 */
internal fun DrawScope.wallpaperShade(strength: Float = 1f) {
    val top = size.height * 0.26f
    val bottom = size.height * 0.7f
    drawRect(
        Brush.verticalGradient(0f to Color(0x47040A0E).copy(alpha = 0.28f * strength), 1f to Color(0x00040A0E), startY = 0f, endY = top),
        size = Size(size.width, top),
    )
    drawRect(
        Brush.verticalGradient(0f to Color(0x00040A0E), 1f to Color(0x59040A0E).copy(alpha = 0.35f * strength), startY = bottom, endY = size.height),
        topLeft = Offset(0f, bottom),
        size = Size(size.width, size.height - bottom),
    )
}

/**
 * One banner, [width] wide and 16:9, cut to its rounded corners with the thin bright edge of
 * glass. What goes around it (the light of the focus, a shadow) is the caller's.
 */
@Composable
internal fun TvBannerFace(
    art: BannerArt,
    width: Dp,
    repository: AppRepository?,
    modifier: Modifier = Modifier,
    corner: Dp = width * (12f / 148f),
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(width, width * (9f / 16f))
            .clip(shape)
            .drawWithContent {
                drawContent()
                // The edge: brighter along the top, where the light comes from.
                val line = 1.dp.toPx()
                val radius = corner.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(line / 2f, line / 2f),
                    size = Size(size.width - line, size.height - line),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    style = Stroke(line),
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.22f),
                        1f to Color.White.copy(alpha = 0f),
                    ),
                    start = Offset(radius, line),
                    end = Offset(size.width - radius, line),
                    strokeWidth = line,
                )
            },
    ) {
        when (art) {
            is BannerArt.AppOwn -> AppOwnFace(art.app, width, repository)
            is BannerArt.AppGlass -> AppGlassFace(art.app, width, repository)
            is BannerArt.Drawn -> DrawnFace(art, width)
            is BannerArt.ConsoleArt -> ConsoleFace(art.console, art.style, width)
            is BannerArt.AllApps -> AllAppsFace(art.label, width)
        }
    }
}

/** A size of letters that is [percent] of a banner [width] wide, as the preview gave them in `cqw`. */
@Composable
private fun cqw(width: Dp, percent: Float): TextUnit = with(LocalDensity.current) { (width * (percent / 100f)).toSp() }

/**
 * [base], or smaller when a word of [text] would not fit in [room] on its own: a name breaks
 * between its words, never inside one.
 */
@Composable
private fun wordsFit(text: String, room: Dp, base: TextUnit, style: TextStyle): TextUnit {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current
    return remember(text, room, base, style, density) {
        val roomPx = with(density) { room.toPx() }
        val widest = text.split(' ', '\n').filter { it.isNotEmpty() }.maxOfOrNull { word ->
            measurer.measure(word, style.copy(fontSize = base), maxLines = 1, softWrap = false).size.width
        } ?: 0
        if (widest <= roomPx || widest == 0) base else base * (roomPx / widest * 0.97f)
    }
}

@Composable
private fun AppOwnFace(app: AppEntry, width: Dp, repository: AppRepository?) {
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }.toInt().coerceAtLeast(1)
    // Read at the size of the largest the banner gets, with the focus, so it stays sharp.
    val asked = (widthPx * 1.16f).toInt()
    val banner by produceState(repository?.cachedBanner(app, asked), app.key, asked, repository) {
        if (value == null && repository != null) value = repository.loadBanner(app, asked)
    }
    val known = banner
    when {
        // Not read yet: only the glass, so nothing is seen changing from one banner to another.
        known == null -> Box(Modifier.fillMaxSize().background(GlassFill))
        known.picture == null -> AppGlassFace(app, width, repository)
        else -> Image(
            bitmap = known.picture,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private val GlassFill = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.06f)))

@Composable
private fun AppGlassFace(app: AppEntry, width: Dp, repository: AppRepository?) {
    val density = LocalDensity.current
    val iconSize = width * 0.24f
    val iconPx = with(density) { iconSize.toPx() }.toInt().coerceAtLeast(1)
    val settings = repository?.iconSettings
    val icon by produceState(
        initialValue = settings?.let { repository.cachedIcon(app, iconPx, it) },
        app.key,
        iconPx,
        settings,
    ) {
        if (settings != null && repository != null) value = repository.loadIcon(app, iconPx, settings)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(width * 0.07f),
        modifier = Modifier
            .fillMaxSize()
            .background(GlassFill)
            .padding(horizontal = width * 0.09f),
    ) {
        Box(Modifier.size(iconSize)) {
            icon?.let { picture ->
                Image(bitmap = picture.bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        val name = app.label
        val size = when {
            name.length <= 12 -> 10.5f
            name.length <= 22 -> 9f
            else -> 7.6f
        }
        val style = TextStyle(fontFamily = TvFont, fontWeight = FontWeight(600), lineHeight = 1.08.em, color = Color.White)
        BasicText(
            text = name,
            maxLines = 3,
            style = style.copy(fontSize = wordsFit(name, width * 0.51f, cqw(width, size), style)),
        )
    }
}

/** A color of a banner Kiri draws, at [alpha]. */
private fun TvColor.color(alpha: Float = 1f): Color = Color(0xFF000000.toInt() or rgb).copy(alpha = alpha)

@Composable
private fun DrawnFace(art: BannerArt.Drawn, width: Dp) {
    val ground = remember(art.color) {
        Brush.linearGradient(listOf(art.color.color(0.92f), art.color.color(0.62f)))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(width * 0.07f),
        modifier = Modifier
            .fillMaxSize()
            .background(ground)
            .padding(horizontal = width * 0.09f),
    ) {
        TvLineIcon(glyphLines(art.glyph), Color.White, Modifier.size(width * 0.26f), stroke = 1.4f)
        Column {
            val style = TextStyle(fontFamily = TvFont, fontWeight = FontWeight(600), lineHeight = 1.08.em, color = Color.White)
            val base = cqw(width, if (art.name.length <= 12) 10.5f else 8.6f)
            BasicText(
                text = art.name,
                maxLines = 2,
                style = style.copy(fontSize = wordsFit(art.name, width * 0.49f, base, style)),
            )
            if (art.port.isNotEmpty() && art.port != art.name) {
                Spacer(Modifier.height(width * 0.016f))
                BasicText(
                    text = art.port,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = TvFont,
                        fontSize = cqw(width, 6.6f),
                        letterSpacing = 0.02.em,
                        color = Color.White.copy(alpha = 0.78f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun AllAppsFace(label: String, width: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(width * 0.03f, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x750E2027)),
    ) {
        TvLineIcon(TvIcons.GRID, Color.White, Modifier.size(width * 0.2f), stroke = 1.5f)
        BasicText(
            text = label,
            maxLines = 1,
            style = TextStyle(fontFamily = TvFont, fontWeight = FontWeight(500), fontSize = cqw(width, 9f), color = Color.White),
        )
    }
}

// ---------- the ready-made banners of consoles ----------

private fun rgb(value: Int, alpha: Float = 1f): Color = Color(0xFF000000.toInt() or value).copy(alpha = alpha)

/** The small line above the big name and the big name, in the person's language. */
@Composable
internal fun Console.brandText(): String =
    if (brandLine == TvConsoles.WORD_COMPUTER) stringResource(R.string.tv_console_computer) else brandLine

@Composable
internal fun Console.nameText(): String =
    nameLine.replace(TvConsoles.WORD_CLASSIC, stringResource(R.string.tv_console_classic))

@Composable
internal fun Console.fullText(): String =
    fullName.replace(TvConsoles.WORD_CLASSIC, stringResource(R.string.tv_console_classic))

/** Which drawings stand wide beside the name: they leave it less room. */
private fun Console.beside(): BesideDrawing = when {
    !consoleLines(id, drawing).own -> BesideDrawing.GENERIC
    id == "sw2" || id == "xss" -> BesideDrawing.WIDE
    else -> BesideDrawing.TALL
}

@Composable
internal fun ConsoleFace(console: Console, style: ConsoleStyle, width: Dp) {
    val brand = console.brandText()
    val name = console.nameText()
    val ground = rgb(console.ground)
    val deep = rgb(console.groundDeep)
    val light = rgb(console.light)
    val second = rgb(console.lightSecond)
    val dark = console.lightGround && (style == ConsoleStyle.COR || style == ConsoleStyle.VIDRO || style == ConsoleStyle.FAIXAS)
    val text = if (dark) Color(0xFF15191F) else Color.White
    val lines = remember(console.id) { consoleLines(console.id, console.drawing) }
    val long = style.isLong(name)
    val beside = if (style == ConsoleStyle.NOITE && !long) console.beside() else BesideDrawing.GENERIC
    val nameSize = cqw(width, style.nameSize(name, beside))
    val brandStyle = TextStyle(
        fontFamily = TvFont,
        fontWeight = FontWeight(600),
        fontSize = cqw(width, 5.2f),
        letterSpacing = 0.16.em,
        lineHeight = 1.em,
        color = text.copy(alpha = 0.8f),
    )
    val nameStyle = TextStyle(
        fontFamily = TvFont,
        fontWeight = FontWeight(750),
        fontSize = nameSize,
        letterSpacing = (-0.02).em,
        lineHeight = 0.96.em,
        color = text,
    )
    val gap = width * 0.018f

    when (style) {
        ConsoleStyle.NOITE -> Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val base = cssLinear(140f, size, 0f to Color(0xFF05070B), 1f to lerp(Color(0xFF070A10), light, 0.16f))
                    val bar = Brush.horizontalGradient(listOf(light, second))
                    onDrawBehind {
                        drawRect(base)
                        ellipseGlow(size.width * 0.78f, size.height * 0.52f, size.width * 0.52f, size.height * 0.92f, light.copy(alpha = 0.46f), 0.72f)
                        val drawing = noiteDrawingBox(lines.own, console.id, long, size)
                        drawConsole(lines, drawing.first, drawing.second, drawing.third, Color.White, light, second, glow = light)
                        drawRect(bar, topLeft = Offset(0f, size.height - size.width * 0.011f), size = Size(size.width, size.width * 0.011f))
                    }
                },
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = width * 0.08f),
            ) {
                BasicText(brand.uppercase(), maxLines = 1, style = brandStyle.copy(color = lerp(Color.White, light, 0.6f)))
                Spacer(Modifier.height(gap))
                BasicText(name, maxLines = 1, softWrap = false, style = nameStyle)
            }
        }

        ConsoleStyle.COR -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val base = cssLinear(135f, size, 0f to ground, 1f to deep)
                    onDrawBehind {
                        drawRect(base)
                        ellipseGlow(size.width, 0f, size.width * 0.9f, size.height * 1.3f, Color.White.copy(alpha = 0.16f), 0.6f)
                        val w = size.width
                        if (lines.own) {
                            val dw = w * 0.58f
                            val dh = w * 0.435f
                            // Out past the right and the bottom edges, as the preview has it.
                            val left = w + w * 0.12f - dw
                            val top = size.height + w * 0.16f - dh
                            rotate(-10f, pivot = Offset(left + dw / 2f, top + dh / 2f)) {
                                drawConsole(lines, left, top, dw, text, text, text, alpha = 0.12f)
                            }
                        } else {
                            val dw = w * 0.64f
                            val left = w + w * 0.09f - dw
                            val top = size.height + w * 0.15f - dw
                            rotate(-12f, pivot = Offset(left + dw / 2f, top + dw / 2f)) {
                                drawConsole(lines, left, top, dw, text, text, text, alpha = 0.12f)
                            }
                        }
                    }
                },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(brand.uppercase(), maxLines = 1, style = brandStyle)
                Spacer(Modifier.height(gap))
                BasicText(name, maxLines = 1, softWrap = false, style = nameStyle)
                Spacer(Modifier.height(width * 0.032f))
                Box(
                    Modifier
                        .size(width * 0.12f, width * 0.013f)
                        .background(second, RoundedCornerShape(width * 0.01f)),
                )
            }
        }

        ConsoleStyle.VIDRO -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val base = cssLinear(135f, size, 0f to ground, 1f to deep)
                    onDrawBehind {
                        drawRect(base)
                        ellipseGlow(size.width * 0.16f, size.height * 0.2f, size.width * 0.46f, size.height * 0.78f, light, 0.72f)
                        ellipseGlow(size.width * 0.88f, size.height * 0.86f, size.width * 0.52f, size.height * 0.88f, second, 0.72f)
                    }
                },
        ) {
            val plate = RoundedCornerShape(width * 0.05f)
            val (top, bottom) = if (console.lightGround) 0.62f to 0.3f else 0.3f to 0.08f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(min = width * 0.5f)
                    .clip(plate)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))))
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.36f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(width.toPx() * 0.05f),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                    .padding(start = width * 0.07f, end = width * 0.07f, top = width * 0.034f, bottom = width * 0.04f),
            ) {
                BasicText(brand.uppercase(), maxLines = 1, style = brandStyle)
                Spacer(Modifier.height(gap))
                BasicText(name, maxLines = 1, softWrap = false, style = nameStyle)
            }
        }

        ConsoleStyle.RETRO -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val sky = Brush.verticalGradient(
                        0f to Color(0xFF0A0518),
                        0.6f to lerp(Color(0xFF0A0518), light, 0.42f),
                        0.6f to Color(0xFF07030F),
                        1f to Color(0xFF07030F),
                    )
                    val w = size.width
                    val sunSide = w * 0.36f
                    val sunTop = w * 0.05f
                    val sun = Brush.verticalGradient(listOf(second, light), startY = sunTop, endY = sunTop + sunSide)
                    val grid = light.copy(alpha = 0.75f)
                    onDrawBehind {
                        drawRect(sky)
                        // A sun cut by bars, as low as the grid.
                        val bands = floatArrayOf(0f, 0.5f, 0.55f, 0.65f, 0.71f, 0.79f, 0.86f, 1f)
                        for (b in 0 until bands.size step 2) {
                            clipRect(top = sunTop + sunSide * bands[b], bottom = sunTop + sunSide * bands[b + 1]) {
                                drawCircle(sun, radius = sunSide / 2f, center = Offset(w / 2f, sunTop + sunSide / 2f), alpha = 0.85f)
                            }
                        }
                        // The floor: a grid going into the distance under the horizon.
                        val horizon = size.height * 0.6f
                        clipRect(top = horizon) {
                            drawRect(Color(0xFF07030F), topLeft = Offset(0f, horizon))
                            val line = 1.dp.toPx()
                            for (k in 1..7) {
                                val y = horizon + (size.height - horizon) * (k / 7f).pow(1.7f)
                                drawLine(grid, Offset(0f, y), Offset(w, y), line)
                            }
                            val step = w * 0.06f
                            var x = -w * 1.4f
                            while (x < w * 2.4f) {
                                drawLine(grid, Offset(w / 2f + (x - w / 2f) * 0.32f, horizon), Offset(w / 2f + (x - w / 2f) * 1.5f, size.height), line)
                                x += step
                            }
                        }
                    }
                }
                .drawWithContent {
                    drawContent()
                    // Scanlines, over the name too.
                    val step = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(0f, y), size = Size(size.width, 1.dp.toPx()))
                        y += step
                    }
                },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    brand.uppercase(),
                    maxLines = 1,
                    style = brandStyle.copy(letterSpacing = 0.3.em, color = Color.White),
                    modifier = Modifier
                        .background(Color(0x9E07030F), RoundedCornerShape(width * 0.012f))
                        .padding(start = width * 0.018f, end = width * 0.014f, top = width * 0.009f, bottom = width * 0.009f),
                )
                Spacer(Modifier.height(gap))
                val shadow = with(LocalDensity.current) { (width * 0.007f).toPx() }
                BasicText(
                    name.uppercase(),
                    maxLines = 1,
                    softWrap = false,
                    style = nameStyle.copy(
                        fontWeight = FontWeight(800),
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 0.01.em,
                        shadow = Shadow(light, Offset(shadow, shadow), 0f),
                    ),
                )
            }
        }

        ConsoleStyle.FAIXAS -> Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val base = cssLinear(135f, size, 0f to ground, 1f to deep)
                    val clear = Color.Transparent
                    val third = text.copy(alpha = 0.5f)
                    val stripes = cssLinear(
                        68f,
                        size,
                        0f to clear, 0.58f to clear,
                        0.58f to light, 0.66f to light,
                        0.66f to clear, 0.69f to clear,
                        0.69f to second, 0.75f to second,
                        0.75f to clear, 0.78f to clear,
                        0.78f to third, 0.8f to third,
                        0.8f to clear, 1f to clear,
                    )
                    onDrawBehind {
                        drawRect(base)
                        drawRect(stripes)
                    }
                },
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = width * 0.07f, bottom = width * 0.08f),
            ) {
                BasicText(brand.uppercase(), maxLines = 1, style = brandStyle)
                Spacer(Modifier.height(gap))
                BasicText(
                    name,
                    maxLines = 1,
                    softWrap = false,
                    style = nameStyle.copy(fontWeight = FontWeight(800), fontStyle = FontStyle.Italic),
                )
            }
        }
    }
}

/** Where the drawing stands beside the name in Noite: left, top and width, on a banner of [size]. */
private fun noiteDrawingBox(own: Boolean, id: String, long: Boolean, size: Size): Triple<Float, Float, Float> {
    val w = size.width
    val (right, width, height) = when {
        own && (id == "sw2" || id == "xss") -> Triple(0.05f, 0.32f, 0.24f)
        own && long -> Triple(0.03f, 0.32f, 0.24f)
        own -> Triple(0.04f, 0.42f, 0.315f)
        long -> Triple(0.05f, 0.24f, 0.24f)
        else -> Triple(0.06f, 0.34f, 0.34f)
    }
    return Triple(w - (right + width) * w, size.height / 2f - height * w / 2f, width * w)
}

/**
 * Draws the lines of a console into the box at [left], [top], [width] wide: its own drawing on
 * a 64 by 48 box, or its kind's on a 48 by 48 one. A [glow] lights the lines from behind.
 */
private fun DrawScope.drawConsole(
    lines: ConsoleLines,
    left: Float,
    top: Float,
    width: Float,
    color: Color,
    accent: Color,
    accentSecond: Color,
    alpha: Float = 1f,
    glow: Color? = null,
) {
    val box = if (lines.own) 64f else 48f
    if (glow != null) {
        for (stroke in lines.strokes) {
            if (stroke.kind == StrokeKind.DOT || stroke.kind == StrokeKind.ACCENT_DOT) continue
            drawLines(stroke.path, box, left, top, width, glow, 4.2f, alpha = 0.16f)
            drawLines(stroke.path, box, left, top, width, glow, 2.6f, alpha = 0.26f)
        }
    }
    for (stroke in lines.strokes) {
        val (tint, widthInBox) = when (stroke.kind) {
            StrokeKind.LINE -> color to 1.5f
            StrokeKind.DETAIL -> color to 1.05f
            StrokeKind.DOT -> color to 1.15f
            StrokeKind.ACCENT_DOT -> accent to 1f
            StrokeKind.ACCENT -> accent to 1.2f
            StrokeKind.ACCENT_SECOND -> accentSecond to 1.2f
        }
        drawLines(stroke.path, box, left, top, width, tint, widthInBox, alpha)
    }
}

// ---------- gradients as the preview wrote them ----------

/**
 * A linear gradient as CSS draws one at [angle] degrees over [size]: 0 goes up, 90 to the
 * right, and it reaches exactly the corners.
 */
internal fun cssLinear(angle: Float, size: Size, vararg stops: Pair<Float, Color>): Brush {
    val radians = Math.toRadians(angle.toDouble())
    val sin = sin(radians).toFloat()
    val cos = cos(radians).toFloat()
    val length = abs(size.width * sin) + abs(size.height * cos)
    val center = Offset(size.width / 2f, size.height / 2f)
    val along = Offset(sin, -cos) * (length / 2f)
    return Brush.linearGradient(*stops, start = center - along, end = center + along)
}

/**
 * A soft light, as a CSS radial gradient makes it: [color] at the middle ([cx], [cy]) fading
 * out at [stop] of an ellipse of radii [rx] and [ry].
 */
internal fun DrawScope.ellipseGlow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, stop: Float) {
    if (rx <= 0f || ry <= 0f) return
    val brush = Brush.radialGradient(
        0f to color,
        stop to color.copy(alpha = 0f),
        1f to color.copy(alpha = 0f),
        center = Offset(cx, cy),
        radius = rx,
    )
    val k = ry / rx
    scale(1f, k, pivot = Offset(cx, cy)) {
        drawRect(brush, topLeft = Offset(0f, cy - cy / k), size = Size(size.width, size.height / k))
    }
}

// ---------- wallpapers ----------

/**
 * The color of what is written straight on the TV's wallpaper (the clock at the top, the gear,
 * the names under the banners): white, and dark over a pale live wallpaper.
 */
internal val LocalTvInk = staticCompositionLocalOf { Color.White }

/** The ink for [look]'s wallpaper. */
internal fun tvInk(look: TvLook): Color = if (look.live?.color?.light == true) Color(0xFF1E1833) else Color.White

/**
 * What is behind the TV's home screen. A live wallpaper is drawn on a surface of its own while
 * the home screen is [running] (see [LiveSurface]), or in place for a [small] preview; a still
 * one as before. A light shade over it keeps what is written on it readable.
 */
@Composable
internal fun TvBackdrop(look: TvLook, running: Boolean, small: Boolean = false) {
    val live = look.live
    if (live == null) {
        TvWallpaperArt(look.wallpaper)
        return
    }
    if (small) LiveCanvas(live) else LiveSurface(live, running)
    // Over a pale one, the shade is lighter: the writing on it turns dark instead.
    val strength = if (live.color.light) 0.3f else 1f
    Box(
        Modifier.fillMaxSize().drawBehind {
            if (TvProbe.off("fullshade")) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0x47040A0E).copy(alpha = 0.28f * strength),
                        0.26f to Color(0x00040A0E),
                        0.7f to Color(0x00040A0E),
                        1f to Color(0x59040A0E).copy(alpha = 0.35f * strength),
                    ),
                )
            } else {
                wallpaperShade(strength)
            }
        },
    )
}

/**
 * The wallpapers Kiri draws for the TV, or the picture the person chose, and the light shade
 * over them that keeps text readable.
 */
@Composable
internal fun TvWallpaperArt(wallpaper: io.github.johankrs.kiri.core.tv.TvWallpaper, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val own = if (wallpaper == io.github.johankrs.kiri.core.tv.TvWallpaper.OWN) {
        val picture = remember(context) { TvPicture.of(context) }
        val version = picture.version
        produceState<ImageBitmap?>(null, version) { value = picture.load() }.value
    } else {
        null
    }
    // The TV's own wallpaper shows through the window: only the shade is drawn over it.
    val drawn = wallpaper != io.github.johankrs.kiri.core.tv.TvWallpaper.SYSTEM
    Box(
        modifier
            .fillMaxSize()
            // Still: drawn once, then only laid down at each frame.
            .then(if (drawn) Modifier.kept() else Modifier)
            .drawWithCache {
                val w = size.width
                val h = size.height
                onDrawBehind {
                    when (wallpaper) {
                        io.github.johankrs.kiri.core.tv.TvWallpaper.AURORA -> {
                            drawRect(cssLinear(135f, size, 0f to Color(0xFF2B2A48), 0.48f to Color(0xFF22507F), 1f to Color(0xFF22495C)))
                            ellipseGlow(w, 0f, w * 0.7f, h * 0.8f, Color(0xFF3F9DB2), 0.62f)
                            ellipseGlow(0f, h, w * 0.8f, h * 0.9f, Color(0xFF7A52C4), 0.6f)
                        }
                        io.github.johankrs.kiri.core.tv.TvWallpaper.OCEANO -> {
                            drawRect(cssLinear(160f, size, 0f to Color(0xFF0D2A45), 0.55f to Color(0xFF0F4F6F), 1f to Color(0xFF0A2233)))
                            ellipseGlow(w * 0.1f, h * 0.9f, w * 0.6f, h * 0.7f, Color(0xFF12365E), 0.7f)
                            ellipseGlow(w * 0.8f, h * 0.2f, w * 0.7f, h * 0.7f, Color(0xFF2FA3C2), 0.6f)
                        }
                        io.github.johankrs.kiri.core.tv.TvWallpaper.GRAFITE -> {
                            drawRect(cssLinear(150f, size, 0f to Color(0xFF1C1F24), 0.5f to Color(0xFF2A2D33), 1f to Color(0xFF15171A)))
                            ellipseGlow(w * 0.75f, h * 0.25f, w * 0.6f, h * 0.6f, Color(0xFF4A4540), 0.65f)
                        }
                        io.github.johankrs.kiri.core.tv.TvWallpaper.POR_DO_SOL -> {
                            drawRect(cssLinear(140f, size, 0f to Color(0xFF3A1F4F), 0.55f to Color(0xFFA44A5A), 1f to Color(0xFFE0864A)))
                            ellipseGlow(0f, h, w * 0.7f, h * 0.8f, Color(0xFF6C2D7A), 0.65f)
                            ellipseGlow(w * 0.85f, h * 0.15f, w * 0.7f, h * 0.8f, Color(0xFFF2A35A), 0.55f)
                        }
                        // The TV's own shows through the window; a TV without one is black.
                        io.github.johankrs.kiri.core.tv.TvWallpaper.SYSTEM -> Unit
                        // Drawn by TvBackdrop on a surface of their own.
                        io.github.johankrs.kiri.core.tv.TvWallpaper.LIVE_AURORA,
                        io.github.johankrs.kiri.core.tv.TvWallpaper.LIVE_BRUMA,
                        io.github.johankrs.kiri.core.tv.TvWallpaper.LIVE_FIOS,
                        io.github.johankrs.kiri.core.tv.TvWallpaper.LIVE_PASTILHAS,
                        io.github.johankrs.kiri.core.tv.TvWallpaper.LIVE_SERRA,
                        io.github.johankrs.kiri.core.tv.TvWallpaper.LIVE_FLORES,
                        -> Unit
                        io.github.johankrs.kiri.core.tv.TvWallpaper.OWN -> if (own != null) {
                            // The whole screen covered, the middle of the picture kept.
                            val scale = maxOf(w / own.width, h / own.height)
                            val sw = (w / scale).toInt().coerceIn(1, own.width)
                            val sh = (h / scale).toInt().coerceIn(1, own.height)
                            drawImage(
                                own,
                                srcOffset = IntOffset((own.width - sw) / 2, (own.height - sh) / 2),
                                srcSize = IntSize(sw, sh),
                                dstSize = IntSize(w.toInt(), h.toInt()),
                                filterQuality = FilterQuality.Medium,
                            )
                            drawRect(Color(0x1F000000))
                        } else {
                            drawRect(cssLinear(150f, size, 0f to Color(0xFF1A1D22), 1f to Color(0xFF111316)))
                        }
                    }
                    if (drawn) drawRect(STILL_SHADE) else wallpaperShade()
                }
            },
    )
}

/** The shade over a still wallpaper, whole: it is drawn once, with the wallpaper. */
private val STILL_SHADE = Brush.verticalGradient(
    0f to Color(0x47040A0E),
    0.26f to Color(0x00040A0E),
    0.7f to Color(0x00040A0E),
    1f to Color(0x59040A0E),
)

/** The look of glass over the TV's screen: menus, the editor, "All apps". */
internal fun Modifier.tvGlass(shape: RoundedCornerShape, strong: Boolean = false): Modifier = this
    .graphicsLayer {
        this.shape = shape
        clip = true
    }
    // Solid: without a blur behind it, what is under a panel would show through as clutter.
    .background(if (strong) PanelStrong else PanelFill)
    .drawBehind {
        val line = 1.dp.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        if (outline is androidx.compose.ui.graphics.Outline.Rounded) {
            val path = Path().apply { addRoundRect(outline.roundRect) }
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.4f),
                    0.3f to Color.White.copy(alpha = 0.2f),
                    1f to Color.White.copy(alpha = 0.14f),
                ),
                style = Stroke(line * 2f),
            )
        }
    }

private val PanelFill = Brush.verticalGradient(listOf(Color(0xFF1C313A), Color(0xFF15262E)))
private val PanelStrong = Brush.verticalGradient(listOf(Color(0xFF1A2E37), Color(0xFF13232A)))

/** An offset for something drawn [x], [y] units from the top left of the TV's screen. */
internal fun Modifier.tvAt(units: TvUnits, x: Float, y: Float): Modifier = offset(units.dp(x), units.dp(y))
