package io.github.johankrs.kiri.ui.theme

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.look.GlassBevel
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colours of one glass look. One recipe for everything that sits over the wallpaper:
 * panels, widgets and the edge of icons.
 */
@Immutable
data class GlassSpec(
    /** The pane itself, already at the chosen opacity. */
    val fill: Color,
    /** The outline of the pane. */
    val edge: Color,
    /** The light caught by the top of the outline. */
    val highlight: Color,
    /** Text and icons on the pane. */
    val content: Color,
    val contentMuted: Color,
    /** Hairlines between rows. */
    val divider: Color,
)

/** The colours of this style with the pane at [opacityPercent]. */
fun GlassStyle.spec(opacityPercent: Int = defaultOpacityPercent): GlassSpec {
    val alpha = opacityPercent.coerceIn(0, 100) / 100f
    return when (this) {
        GlassStyle.FUME -> GlassSpec(
            fill = Color(0xFF0E2027).copy(alpha = alpha),
            edge = Color.White.copy(alpha = 0.22f),
            highlight = Color.White.copy(alpha = 0.40f),
            content = Color.White,
            contentMuted = Color.White.copy(alpha = 0.80f),
            divider = Color.White.copy(alpha = 0.16f),
        )

        GlassStyle.CRISTAL -> GlassSpec(
            fill = Color(0xFF122C34).copy(alpha = alpha),
            edge = Color.White.copy(alpha = 0.50f),
            highlight = Color.White.copy(alpha = 0.85f),
            content = Color.White,
            contentMuted = Color.White.copy(alpha = 0.86f),
            divider = Color.White.copy(alpha = 0.24f),
        )

        GlassStyle.NEVOA -> GlassSpec(
            fill = Color(0xFFF0F6F5).copy(alpha = alpha),
            edge = Color.White.copy(alpha = 0.70f),
            highlight = Color.White.copy(alpha = 0.95f),
            content = Color(0xFF10262D),
            contentMuted = Color(0xFF33494F),
            divider = Color(0xFF10262D).copy(alpha = 0.14f),
        )
    }
}

/**
 * The tile of glass the icons are, which panels, widgets and menus are made of too: how deep
 * its rounded edge is, how lit, how wide, and how dark its shadow. Null, or with no depth, a
 * pane has only its thin outline.
 */
val LocalGlassTile = compositionLocalOf<GlassBevel.Tile?> { null }

/**
 * Turns this element into a pane of glass: clipped to [shape], frosted, tinted, and outlined.
 * With [edge] off it is a flat pane with no outline.
 *
 * Frosted means it shows the blurred wallpaper behind itself, where there is a [LocalBackdrop].
 * Where there is a [LocalGlassTile] the outline is the rounded edge of a tile of glass, lit
 * and shaded as the icons are, and the pane casts the same soft shadow.
 *
 * [vivid] glass, for dark styles, takes on the colour of the wallpaper: the wallpaper behind it
 * is made more colourful and darker, and only a little tint goes over it, such as
 * [VIVID_GLASS_OPACITY]. Darkening instead of covering keeps white text readable even on a
 * nearly white wallpaper. Where Kiri has no picture of the wallpaper there is nothing to darken,
 * so the pane is tinted as solidly as [VIVID_ALONE_OPACITY] instead.
 */
@Composable
fun Modifier.kiriGlass(
    style: GlassStyle,
    shape: Shape,
    opacityPercent: Int = style.defaultOpacityPercent,
    edge: Boolean = true,
    vivid: Boolean = false,
): Modifier {
    val tile = LocalGlassTile.current?.takeIf { edge && it.depth > 0f && shape is CornerBasedShape }
    val percent = if (vivid && LocalBackdrop.current?.image == null) maxOf(opacityPercent, VIVID_ALONE_OPACITY) else opacityPercent
    val fill = style.spec(percent).fill
    return if (tile != null) {
        this
            .glassShadow(shape, tile)
            .clip(shape)
            .backdrop(vivid = vivid)
            .background(fill)
            .glassRelief(shape, style, tile)
    } else {
        this
            .clip(shape)
            .backdrop(vivid = vivid)
            .background(fill)
            .then(if (edge) Modifier.kiriGlassEdge(style, shape) else Modifier)
    }
}

/**
 * The pane of [kiriGlass] alone, without what goes around it: clipped to [shape], frosted and
 * tinted. With [glassShadowOf] and [glassTrim] around it, it is the same glass, in parts that
 * can be faded one by one, as a folder does while its outline turns from one into another.
 */
@Composable
fun Modifier.glassBody(style: GlassStyle, shape: Shape, opacityPercent: Int, vivid: Boolean = false): Modifier {
    val percent = if (vivid && LocalBackdrop.current?.image == null) maxOf(opacityPercent, VIVID_ALONE_OPACITY) else opacityPercent
    return clip(shape).backdrop(vivid = vivid).background(style.spec(percent).fill)
}

/**
 * The soft shadow [kiriGlass] casts for [shape], if any, faded by [fade] (read while drawing).
 * It goes before the pane, as in [kiriGlass].
 */
@Composable
fun Modifier.glassShadowOf(shape: Shape, fade: () -> Float = { 1f }): Modifier {
    val tile = LocalGlassTile.current?.takeIf { it.depth > 0f && shape is CornerBasedShape } ?: return this
    return graphicsLayer {
        alpha = fade()
        // Faded op by op, not through a picture of the bounds: the shadow is outside them.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }.glassShadow(shape, tile)
}

/**
 * The edge [kiriGlass] gives [shape]: the lit, rounded edge of a tile of glass where there is a
 * [LocalGlassTile] and the shape has corners, otherwise the thin outline. It is kept inside
 * [clip], the outline the pane really has at the moment; a thin outline follows [clip] itself.
 * Faded by [fade], read while drawing.
 */
@Composable
fun Modifier.glassTrim(style: GlassStyle, shape: Shape, clip: Shape = shape, fade: () -> Float = { 1f }): Modifier {
    val tile = LocalGlassTile.current?.takeIf { it.depth > 0f && shape is CornerBasedShape }
    val faded = graphicsLayer {
        alpha = fade()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
    return if (tile != null) {
        faded.clip(clip).glassRelief(shape, style, tile)
    } else {
        faded.kiriGlassEdge(style, clip)
    }
}

/** How much tint goes over vivid glass (see [kiriGlass]): menus and folders use it. */
const val VIVID_GLASS_OPACITY = 30

/** How much tint vivid glass gets where Kiri has no picture of the wallpaper to darken. */
const val VIVID_ALONE_OPACITY = 86

/**
 * The same glass as [kiriGlass] for something small that is drawn many times, such as the plate
 * under an icon: what is on it is not clipped, which costs less.
 */
fun Modifier.kiriGlassPlate(
    style: GlassStyle,
    shape: Shape,
    opacityPercent: Int = style.defaultOpacityPercent,
    edge: Boolean = true,
): Modifier = this
    .backdrop(shape)
    .background(style.spec(opacityPercent).fill, shape)
    .then(if (edge) Modifier.kiriGlassEdge(style, shape) else Modifier)

/**
 * Only the outline of the glass, brighter at the top where it catches the light. Goes over any
 * shape, such as an icon, without filling it.
 *
 * With a [tint] the outline is of that colour instead of bright: for what is itself so light
 * that a bright line would be lost on it. It then looks like the rim of glass bending the
 * colour that lies under it.
 */
fun Modifier.kiriGlassEdge(style: GlassStyle, shape: Shape, width: Dp = 1.dp, tint: Color? = null): Modifier {
    val spec = style.spec()
    val brush = if (tint != null) {
        Brush.verticalGradient(0f to tint.copy(alpha = 0.34f), 0.6f to tint.copy(alpha = 0.46f), 1f to tint.copy(alpha = 0.58f))
    } else {
        Brush.verticalGradient(0f to spec.highlight, 0.4f to spec.edge, 1f to spec.edge)
    }
    return border(width = width, brush = brush, shape = shape)
}

/** A glass panel in the chosen [style]. */
@Composable
fun KiriGlassPanel(
    style: GlassStyle,
    modifier: Modifier = Modifier,
    opacityPercent: Int = style.defaultOpacityPercent,
    shape: Shape = RoundedCornerShape(24.dp),
    edge: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.kiriGlass(style, shape, opacityPercent, edge), content = content)
}

/** An icon is about this large: the edge of a pane is as wide as an icon's, its shadow as soft. */
private val IconScale = 56.dp

/** The rounded edge of a tile of glass, along the outline of a pane of any size. */
private fun Modifier.glassRelief(shape: Shape, style: GlassStyle, tile: GlassBevel.Tile): Modifier = drawWithCache {
    val corner = (shape as CornerBasedShape).topStart.toPx(size, this).coerceAtMost(size.minDimension / 2f)
    val bevel = tile.width * IconScale.toPx()
    val pane = GlassPanes.edge(corner, bevel, style, tile)
    val width = size.width.roundToInt()
    val height = size.height.roundToInt()
    onDrawBehind { drawNine(pane.image, pane.quarter, 0, 0, width, height) }
}

/** The soft shadow a tile of glass casts, under a pane of any size. Only below it, never under it. */
private fun Modifier.glassShadow(shape: Shape, tile: GlassBevel.Tile): Modifier =
    if (tile.shadow <= 0f) {
        this
    } else {
        drawWithCache {
            val corner = (shape as CornerBasedShape).topStart.toPx(size, this).coerceAtMost(size.minDimension / 2f)
            val shadow = GlassPanes.shadow(corner, IconScale.toPx(), tile.shadow)
            val width = size.width.roundToInt()
            val height = size.height.roundToInt()
            val pad = shadow.pad
            onDrawBehind { drawNine(shadow.image, shadow.quarter, -pad, -pad, width + 2 * pad, height + 2 * pad) }
        }
    }

/**
 * Draws [image], a square made by [GlassBevel.pane], over a rectangle: its four quarters, each
 * [quarter] pixels a side, at the four corners, and its middle row and column stretched along
 * the sides. A rectangle smaller than two quarters takes only the outer part of each.
 */
private fun DrawScope.drawNine(image: ImageBitmap, quarter: Int, left: Int, top: Int, width: Int, height: Int) {
    val whole = image.width
    val across = min(quarter, width / 2)
    val down = min(quarter, height / 2)
    val right = whole - across
    val bottom = whole - down
    val middleWidth = width - 2 * across
    val middleHeight = height - 2 * down
    fun piece(sx: Int, sy: Int, sw: Int, sh: Int, dx: Int, dy: Int, dw: Int, dh: Int) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return
        drawImage(
            image = image,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset(left + dx, top + dy),
            dstSize = IntSize(dw, dh),
            filterQuality = FilterQuality.None,
        )
    }
    piece(0, 0, across, down, 0, 0, across, down)
    piece(right, 0, across, down, width - across, 0, across, down)
    piece(0, bottom, across, down, 0, height - down, across, down)
    piece(right, bottom, across, down, width - across, height - down, across, down)
    piece(quarter, 0, 1, down, across, 0, middleWidth, down)
    piece(quarter, bottom, 1, down, across, height - down, middleWidth, down)
    piece(0, quarter, across, 1, 0, down, across, middleHeight)
    piece(right, quarter, across, 1, width - across, down, across, middleHeight)
}

/**
 * The pictures of the edges and shadows of panes, made once for each size of corner, look and
 * tile: kept in a bounded LRU so stepping settings never wipes hot menu/widget entries.
 */
internal object GlassPanes {
    class Piece(val image: ImageBitmap, val quarter: Int, val pad: Int)

    private data class EdgeKey(val corner: Int, val bevel: Int, val style: GlassStyle, val tile: GlassBevel.Tile)
    private data class ShadowKey(val corner: Int, val scale: Int, val amount: Float)

    private val edges = LruCache<EdgeKey, Piece>(MOST)
    private val shadows = LruCache<ShadowKey, Piece>(MOST)

    fun edge(corner: Float, bevel: Float, style: GlassStyle, tile: GlassBevel.Tile): Piece {
        val key = EdgeKey(corner.roundToInt(), bevel.roundToInt(), style, tile)
        edges.get(key)?.let { return it }
        val pane = GlassBevel.pane(key.corner.toFloat(), key.bevel.toFloat(), GlassBevel.lightOf(style), tile)
        val built = Piece(picture(pane), pane.middle, 0)
        return synchronized(edges) {
            edges.get(key) ?: built.also { edges.put(key, it) }
        }
    }

    fun shadow(corner: Float, scale: Float, amount: Float): Piece {
        val key = ShadowKey(corner.roundToInt(), scale.roundToInt(), amount)
        shadows.get(key)?.let { return it }
        val pane = GlassBevel.paneShadow(key.corner.toFloat(), key.scale.toFloat(), amount)
        val built = Piece(picture(pane), pane.middle, pane.pad)
        return synchronized(shadows) {
            shadows.get(key) ?: built.also { shadows.put(key, it) }
        }
    }

    /**
     * Pre-renders common pane corners (menu 24 dp, panels 28 dp) off the UI thread so the very
     * first long-press or widget open has zero main-thread bevel generation stall.
     */
    suspend fun prewarm(density: Float, tile: GlassBevel.Tile) {
        if (tile.depth <= 0f || density <= 0f) return
        withContext(Dispatchers.Default) {
            val scalePx = IconScale.value * density
            val bevelPx = tile.width * scalePx
            for (cornerDp in listOf(24f, 28f)) {
                val cornerPx = cornerDp * density
                edge(cornerPx, bevelPx, GlassStyle.FUME, tile)
                if (tile.shadow > 0f) shadow(cornerPx, scalePx, tile.shadow)
            }
        }
    }

    fun clear() {
        edges.evictAll()
        shadows.evictAll()
    }

    private fun picture(pane: GlassBevel.Pane): ImageBitmap =
        android.graphics.Bitmap.createBitmap(pane.pixels, pane.size, pane.size, android.graphics.Bitmap.Config.ARGB_8888)
            .asImageBitmap()

    private const val MOST = 24
}
