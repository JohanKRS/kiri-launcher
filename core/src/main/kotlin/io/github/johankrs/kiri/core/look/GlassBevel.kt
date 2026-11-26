package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How deep a point is inside an icon of this shape [size] pixels wide, in pixels from the
 * nearest edge: positive inside, negative outside, 0 on the edge.
 */
fun IconShape.depth(x: Float, y: Float, size: Float): Float {
    val half = size / 2f
    val across = x - half
    val down = y - half
    return when (this) {
        IconShape.CIRCLE -> half - sqrt(across * across + down * down)

        IconShape.ROUNDED, IconShape.SOFT_SQUARE -> {
            val corner = size * cornerShare
            val qx = abs(across) - (half - corner)
            val qy = abs(down) - (half - corner)
            val outside = sqrt(max(qx, 0f).let { it * it } + max(qy, 0f).let { it * it })
            corner - outside - min(max(qx, qy), 0f)
        }

        // |x|⁴ + |y|⁴ = r⁴. Its level divided by how steeply it changes is the distance near the
        // edge, which is the only place it is needed.
        IconShape.SQUIRCLE -> {
            val x2 = across * across
            val y2 = down * down
            val level = sqrt(sqrt((x2 * x2 + y2 * y2).toDouble())).toFloat()
            if (level < 1e-3f) {
                half
            } else {
                val steep = sqrt(x2 * x2 * x2 + y2 * y2 * y2) / (level * level * level)
                (half - level) / steep.coerceAtLeast(0.5f)
            }
        }
    }
}

/**
 * The relief of a tile of glass in the shape of an icon, worked out pixel by pixel, once, when
 * the icon is made.
 *
 * A tile of glass is not flat to its edge: the edge is rounded over, like a bevel. Where the
 * bevel faces the light it shines, brightest at the corner that turns towards the light, and
 * where it faces away it is in shade; just inside that far edge runs a thin brighter line,
 * where the light that went through the glass comes out again. Under the bevel the picture is
 * bent a little, as thick glass bends what lies under it. Under the tile lies a soft shadow.
 * All of it is meant to be felt more than seen.
 *
 * Pictures are one ARGB number per pixel, not premultiplied, row after row, as Android's
 * `Bitmap.getPixels` gives them.
 */
object GlassBevel {
    /**
     * How the glass is lit. [rimTop] and [rimBottom] are how bright the thin line at its very
     * edge is at the top and at the bottom, from 0 to 1, in [rimColor].
     */
    data class Light(
        val rimTop: Float,
        val rimBottom: Float,
        val rimColor: Int = WHITE,
    )

    /**
     * How the glass of [style] is lit: its thin edge is brighter on the clearer glasses. With a
     * [rimTint] the edge is of that colour, for a picture so light that a bright edge would be
     * lost on it.
     */
    fun lightOf(style: GlassStyle, rimTint: Int? = null): Light = when {
        rimTint != null -> Light(rimTop = 0.34f, rimBottom = 0.55f, rimColor = rimTint)
        style == GlassStyle.CRISTAL -> Light(rimTop = 0.55f, rimBottom = 0.24f)
        style == GlassStyle.NEVOA -> Light(rimTop = 0.70f, rimBottom = 0.34f)
        else -> Light(rimTop = 0.36f, rimBottom = 0.14f)
    }

    /**
     * How much there is of each part of the tile: [depth] for the shade, the edge and the
     * bending, [light] for the light on the edge, [width] for how far in the rounded edge
     * reaches (a share of the tile) and [shadow] for the shadow. 1 is the usual.
     */
    data class Tile(
        val depth: Float = 1f,
        val light: Float = 1f,
        val width: Float = BEVEL_SHARE,
        val shadow: Float = 1f,
    ) {
        companion object {
            /** The tile [look] asks for. */
            fun of(look: IconLook): Tile = Tile(
                depth = when (look.relief) {
                    GlassLevel.OFF -> 0f
                    GlassLevel.LIGHT -> 0.6f
                    GlassLevel.MEDIUM -> 1f
                    GlassLevel.STRONG -> 1.45f
                },
                light = when (look.edgeLight) {
                    GlassLevel.OFF -> 0f
                    GlassLevel.LIGHT -> 0.45f
                    GlassLevel.MEDIUM -> 1f
                    GlassLevel.STRONG -> 1.4f
                },
                width = BEVEL_SHARE + (look.edgeWidth.coerceIn(IconLook.EDGE_WIDTH) - IconLook.DEFAULT_EDGE_WIDTH) * WIDTH_STEP,
                // The first shadow was too dark: it is the strong one now.
                shadow = when (look.shadow) {
                    GlassLevel.OFF -> 0f
                    GlassLevel.LIGHT -> 0.42f
                    GlassLevel.MEDIUM -> 0.67f
                    GlassLevel.STRONG -> 1f
                },
            )
        }
    }

    /**
     * Precomputed geometry and lighting for every bevel pixel of an icon of one `(size, shape, tile)`.
     * Shared across every icon in a grid so rendering 100+ icons skips all SDF/normal/trig math.
     */
    private class BevelField(
        val count: Int,
        val indices: IntArray,
        val cover: FloatArray,
        val pullX: FloatArray,
        val pullY: FloatArray,
        val shine: FloatArray,
        val shaded: FloatArray,
        val rimScale: FloatArray,
        val topFactor: FloatArray,
    )

    /** Precomputed glass pixels for `under == true` (icons sitting on a glass plate). */
    private class PlateOverlay(val indices: IntArray, val glass: IntArray)

    private data class FieldKey(val size: Int, val shape: IconShape, val tile: Tile)
    private data class OverlayKey(val field: FieldKey, val light: Light)

    private val fields = LinkedHashMap<FieldKey, BevelField>(8, 0.75f, true)
    private val overlays = LinkedHashMap<OverlayKey, PlateOverlay>(12, 0.75f, true)

    private const val MAX_FIELDS = 6
    private const val MAX_OVERLAYS = 8

    /** Drops cached bevel fields and overlays, for low-memory situations. */
    fun clearCaches() {
        synchronized(fields) { fields.clear() }
        synchronized(overlays) { overlays.clear() }
    }

    private fun fieldOf(size: Int, shape: IconShape, tile: Tile): BevelField {
        val key = FieldKey(size, shape, tile)
        synchronized(fields) {
            fields[key]?.let { return it }
        }
        val built = buildField(size, shape, tile)
        synchronized(fields) {
            fields[key]?.let { return it }
            if (fields.size >= MAX_FIELDS) fields.remove(fields.keys.first())
            fields[key] = built
        }
        return built
    }

    private fun plateOverlayOf(size: Int, shape: IconShape, tile: Tile, light: Light): PlateOverlay {
        val fieldKey = FieldKey(size, shape, tile)
        val key = OverlayKey(fieldKey, light)
        synchronized(overlays) {
            overlays[key]?.let { return it }
        }
        val field = fieldOf(size, shape, tile)
        val n = field.count
        val glass = IntArray(n)
        val rimBottom = light.rimBottom
        val rimDiff = light.rimTop - rimBottom
        val rimColor = light.rimColor
        for (k in 0 until n) {
            val cover = field.cover[k]
            var g = 0
            val s = field.shaded[k] * cover
            if (s > 0f) g = over(g, withAlpha(SHADE_COLOR, s))
            val sh = field.shine[k].coerceIn(0f, MOST) * cover
            if (sh > 0f) g = over(g, withAlpha(WHITE, sh))
            val rs = field.rimScale[k]
            if (rs > 0f) {
                val edge = ((rimBottom + rimDiff * field.topFactor[k]) * rs).coerceIn(0f, 1f) * cover
                if (edge > 0f) g = over(g, withAlpha(rimColor, edge))
            }
            glass[k] = g
        }
        val built = PlateOverlay(field.indices, glass)
        synchronized(overlays) {
            overlays[key]?.let { return it }
            if (overlays.size >= MAX_OVERLAYS) overlays.remove(overlays.keys.first())
            overlays[key] = built
        }
        return built
    }

    private fun buildField(size: Int, shape: IconShape, tile: Tile): BevelField {
        val side = size.toFloat()
        val bevel = max(MIN_BEVEL, side * tile.width)
        val depthCap = min(1f, tile.depth)
        val pullScale = BEND * bevel * depthCap
        val bevelMax = bevel + 1f
        val invBevel = 1f / bevel
        val invThickBevel = 1f / (bevel * 2.2f)

        // Two-pass or growing primitive arrays: estimate ~45% bevel pixels to avoid resizing.
        var cap = max(64, (size * size) / 2)
        var indices = IntArray(cap)
        var coverArr = FloatArray(cap)
        var pullXArr = FloatArray(cap)
        var pullYArr = FloatArray(cap)
        var shineArr = FloatArray(cap)
        var shadedArr = FloatArray(cap)
        var rimScaleArr = FloatArray(cap)
        var topFactorArr = FloatArray(cap)
        var count = 0

        for (y in 0 until size) {
            val py = y + 0.5f
            val rowOffset = y * size
            for (x in 0 until size) {
                val px = x + 0.5f
                val inside = shape.depth(px, py, side)
                if (inside <= -0.5f || inside >= bevelMax) continue

                var nx = shape.depth(px - STEP, py, side) - shape.depth(px + STEP, py, side)
                var ny = shape.depth(px, py - STEP, side) - shape.depth(px, py + STEP, side)
                val length = sqrt(nx * nx + ny * ny)
                if (length < 1e-6f) continue
                nx /= length
                ny /= length

                val slope = (1f - inside * invBevel).coerceIn(0f, 1f)
                val rounded = slope * slope
                val facing = nx * LIGHT_X + ny * LIGHT_Y
                val toCorner = nx * GLINT_X + ny * GLINT_Y
                val toward = max(0f, facing)
                val away = max(0f, -facing)

                var shine = HIGHLIGHT * rounded * toward * toward +
                    cornerGlint(toCorner) * slope * rounded
                if (inside in 0.8f..2.6f) {
                    val line = 1f - abs(inside - 1.7f) / 0.9f
                    shine += INNER_LINE * line * toward + EXIT_LINE * line * away * away * away
                }
                val awayShade = if (away > 0f) SHADE * rounded * away.pow(1.3f) else 0f
                val thick = (1f - inside * invThickBevel).coerceIn(0f, 1f)
                val shade = awayShade + THICKNESS * thick * thick * max(0f, ny)

                if (count == cap) {
                    cap *= 2
                    indices = indices.copyOf(cap)
                    coverArr = coverArr.copyOf(cap)
                    pullXArr = pullXArr.copyOf(cap)
                    pullYArr = pullYArr.copyOf(cap)
                    shineArr = shineArr.copyOf(cap)
                    shadedArr = shadedArr.copyOf(cap)
                    rimScaleArr = rimScaleArr.copyOf(cap)
                    topFactorArr = topFactorArr.copyOf(cap)
                }

                val pull = pullScale * rounded
                indices[count] = rowOffset + x
                coverArr[count] = (inside + 0.5f).coerceIn(0f, 1f)
                pullXArr[count] = px - nx * pull
                pullYArr[count] = py - ny * pull
                shineArr[count] = shine * tile.light
                shadedArr[count] = (shade * tile.depth).coerceIn(0f, MOST)
                rimScaleArr[count] = if (inside < RIM_WIDTH) (RIM_WIDTH - inside).coerceIn(0f, 1f) * depthCap else 0f
                topFactorArr[count] = (1f - ny) * 0.5f
                count++
            }
        }
        return BevelField(
            count = count,
            indices = indices.copyOf(count),
            cover = coverArr.copyOf(count),
            pullX = pullXArr.copyOf(count),
            pullY = pullYArr.copyOf(count),
            shine = shineArr.copyOf(count),
            shaded = shadedArr.copyOf(count),
            rimScale = rimScaleArr.copyOf(count),
            topFactor = topFactorArr.copyOf(count),
        )
    }

    /**
     * [pixels], a picture [size] pixels wide already cut to [shape], dressed as a tile of glass.
     *
     * With [bend] the picture is bent under the bevel: for a picture that fills the tile. With
     * [under] the light of the glass goes under the picture instead of over it: for a drawing
     * that lies on a plate of glass, which the light must not cover. [tile] says how much of
     * each part there is. When [into] is provided (at least `size * size` entries and distinct
     * from [pixels]), the result is written there to avoid allocating a new array per icon.
     */
    @JvmOverloads
    fun dress(
        pixels: IntArray,
        size: Int,
        shape: IconShape,
        light: Light,
        bend: Boolean,
        under: Boolean,
        tile: Tile = Tile(),
        into: IntArray? = null,
    ): IntArray {
        val total = size * size
        require(pixels.size >= total) { "The picture is smaller than its size" }
        val out = if (into != null && into !== pixels && into.size >= total) {
            System.arraycopy(pixels, 0, into, 0, total)
            into
        } else {
            pixels.copyOf(total)
        }
        if (size < MIN_SIZE || tile.depth <= 0f) return out

        if (under) {
            val overlay = plateOverlayOf(size, shape, tile, light)
            val indices = overlay.indices
            val glass = overlay.glass
            for (k in indices.indices) {
                val idx = indices[k]
                val fg = pixels[idx]
                out[idx] = if (fg ushr 24 == 0) glass[k] else over(glass[k], fg)
            }
            return out
        }

        val field = fieldOf(size, shape, tile)
        val n = field.count
        val indices = field.indices
        val pullX = field.pullX
        val pullY = field.pullY
        val shine = field.shine
        val shaded = field.shaded
        val rimScale = field.rimScale
        val topFactor = field.topFactor
        val rimBottom = light.rimBottom
        val rimDiff = light.rimTop - rimBottom
        val rimColor = light.rimColor
        val whiteRim = rimColor == WHITE

        for (k in 0 until n) {
            val idx = indices[k]
            val pixel = pixels[idx]
            val alpha = pixel ushr 24
            if (alpha == 0) continue
            var colour = if (bend) {
                bent(pixels, size, size, pullX[k], pullY[k], alpha)
            } else {
                pixel
            }
            val sh = shaded[k]
            if (sh > 0f) colour = tint(colour, SHADE_COLOR, sh)
            // Light on glass takes the colour of what is under it, and a dark picture
            // gives back less of it: the light is a paler shade of the picture, and
            // weaker the darker the picture is.
            val lift = DARK_LIFT + (1f - DARK_LIFT) * lightness(colour)
            val shineAmount = (shine[k] * lift).coerceIn(0f, MOST)
            if (shineAmount > 0f) colour = tint(colour, paler(colour), shineAmount)
            val rs = rimScale[k]
            if (rs > 0f) {
                val edge = (rimBottom + rimDiff * topFactor[k]) * rs
                val rimLift = if (whiteRim) lift else 1f
                val rimColour = if (whiteRim) paler(colour) else rimColor
                colour = tint(colour, rimColour, (edge * rimLift).coerceIn(0f, 1f))
            }
            out[idx] = colour
        }
        return out
    }

    /**
     * The rounded edge of a pane of glass of any size, with corners of [corner] pixels and the
     * edge reaching [bevel] pixels in, lit as the icons are. It comes as a square [Pane] to be
     * laid over the pane: its four quarters at the four corners, its middle row and column
     * stretched along the sides, where the edge is the same all along.
     */
    fun pane(corner: Float, bevel: Float, light: Light, tile: Tile = Tile()): Pane {
        val quarter = ceil(max(corner, bevel)).toInt() + 2
        val size = quarter * 2 + 1
        val side = size.toFloat()
        val clear = IntArray(size * size)
        if (tile.depth <= 0f) return Pane(clear, size)
        val half = side * 0.5f
        val radius = corner.coerceIn(0f, half)
        val inner = half - radius
        val pixels = paint(
            pixels = clear,
            width = size,
            height = size,
            depthAt = { x, y -> squareRoundedDepth(x, y, half, radius, inner) },
            bevel = max(MIN_BEVEL, bevel),
            light = light,
            bend = false,
            under = true,
            tile = tile,
        )
        return Pane(pixels, size)
    }

    /**
     * The soft shadow a pane of glass with corners of [corner] pixels casts, as dark as
     * [amount] says, for an icon of [scale] pixels: the shadow of a pane falls as far and as
     * softly as the shadow of an icon. Laid out like [pane], with [Pane.pad] pixels more on every
     * side, to be drawn [Pane.pad] pixels up and to the left of the pane.
     */
    fun paneShadow(corner: Float, scale: Float, amount: Float): Pane {
        val quarter = ceil(corner).toInt() + 2
        val size = quarter * 2 + 1
        val side = size.toFloat()
        val drop = scale * SHADOW_DROP
        val soft = scale * SHADOW_SOFT
        val pad = ceil(drop + soft).toInt() + 1
        val half = side * 0.5f
        val radius = corner.coerceIn(0f, half)
        val inner = half - radius
        val pixels = cast(size, size, pad, { x, y -> squareRoundedDepth(x, y, half, radius, inner) }, drop, soft, amount)
        return Pane(pixels, size + 2 * pad, pad)
    }

    /**
     * A square picture of the edge of a pane, [size] pixels a side and [pad] more around it,
     * one ARGB number per pixel. See [pane].
     */
    class Pane(val pixels: IntArray, val size: Int, val pad: Int = 0) {
        /** The middle row and column, which are stretched along the sides. */
        val middle: Int get() = size / 2
    }

    /** The light and shade of a tile whose outline is [depthAt] (see [IconShape.depth]). */
    private inline fun paint(
        pixels: IntArray,
        width: Int,
        height: Int,
        depthAt: (Float, Float) -> Float,
        bevel: Float,
        light: Light,
        bend: Boolean,
        under: Boolean,
        tile: Tile,
    ): IntArray {
        val out = pixels.copyOf(width * height)
        val bevelMax = bevel + 1f
        val invBevel = 1f / bevel
        val invThickBevel = 1f / (bevel * 2.2f)
        val depthCap = min(1f, tile.depth)
        for (y in 0 until height) {
            val py = y + 0.5f
            val rowOffset = y * width
            for (x in 0 until width) {
                val px = x + 0.5f
                val inside = depthAt(px, py)
                // Outside the tile, and deep inside it past the bevel, nothing changes.
                if (inside <= -0.5f || inside >= bevelMax) continue
                val index = rowOffset + x
                val cover = (inside + 0.5f).coerceIn(0f, 1f)

                // Which way the surface faces there, outwards.
                var nx = depthAt(px - STEP, py) - depthAt(px + STEP, py)
                var ny = depthAt(px, py - STEP) - depthAt(px, py + STEP)
                val length = sqrt(nx * nx + ny * ny)
                if (length < 1e-6f) continue
                nx /= length
                ny /= length

                // 1 at the edge, 0 where the bevel has turned flat.
                val slope = (1f - inside * invBevel).coerceIn(0f, 1f)
                val rounded = slope * slope
                val facing = nx * LIGHT_X + ny * LIGHT_Y
                val toCorner = nx * GLINT_X + ny * GLINT_Y
                val toward = max(0f, facing)
                val away = max(0f, -facing)

                var shine = HIGHLIGHT * rounded * toward * toward +
                    cornerGlint(toCorner) * slope * rounded
                // The thin line just inside the top edge, and the light coming out at the bottom.
                if (inside in 0.8f..2.6f) {
                    val line = 1f - abs(inside - 1.7f) / 0.9f
                    shine += INNER_LINE * line * toward + EXIT_LINE * line * away * away * away
                }
                val awayShade = if (away > 0f) SHADE * rounded * away.pow(1.3f) else 0f
                val thick = (1f - inside * invThickBevel).coerceIn(0f, 1f)
                val shade = awayShade + THICKNESS * thick * thick * max(0f, ny)
                val rim = if (inside < RIM_WIDTH) {
                    (light.rimBottom + (light.rimTop - light.rimBottom) * (1f - ny) * 0.5f) *
                        (RIM_WIDTH - inside).coerceIn(0f, 1f)
                } else {
                    0f
                }

                shine *= tile.light
                val shaded = (shade * tile.depth).coerceIn(0f, MOST)
                val edge = rim * depthCap

                if (under) {
                    var glass = 0
                    val s = shaded * cover
                    if (s > 0f) glass = over(glass, withAlpha(SHADE_COLOR, s))
                    val sh = shine.coerceIn(0f, MOST) * cover
                    if (sh > 0f) glass = over(glass, withAlpha(WHITE, sh))
                    val e = edge.coerceIn(0f, 1f) * cover
                    if (e > 0f) glass = over(glass, withAlpha(light.rimColor, e))
                    val fg = pixels[index]
                    out[index] = if (fg ushr 24 == 0) glass else over(glass, fg)
                } else {
                    val alpha = pixels[index] ushr 24
                    if (alpha == 0) continue
                    var colour = if (bend) {
                        val pull = BEND * bevel * rounded * depthCap
                        bent(pixels, width, height, px - nx * pull, py - ny * pull, alpha)
                    } else {
                        pixels[index]
                    }
                    colour = tint(colour, SHADE_COLOR, shaded)
                    // Light on glass takes the colour of what is under it, and a dark picture
                    // gives back less of it: the light is a paler shade of the picture, and
                    // weaker the darker the picture is.
                    val lift = DARK_LIFT + (1f - DARK_LIFT) * lightness(colour)
                    colour = tint(colour, paler(colour), (shine * lift).coerceIn(0f, MOST))
                    val rimLift = if (light.rimColor == WHITE) lift else 1f
                    val rimColour = if (light.rimColor == WHITE) paler(colour) else light.rimColor
                    colour = tint(colour, rimColour, (edge * rimLift).coerceIn(0f, 1f))
                    out[index] = colour
                }
            }
        }
        return out
    }

    /** Exact `GLINT * max(0, x)^12 + BACK_GLINT * max(0, -x)^12` without `Math.pow`. */
    private fun cornerGlint(toCorner: Float): Float {
        if (toCorner == 0f) return 0f
        val t2 = toCorner * toCorner
        val t4 = t2 * t2
        val t12 = t4 * t4 * t4
        return if (toCorner > 0f) GLINT * t12 else BACK_GLINT * t12
    }

    /**
     * The soft shadow a tile of [shape], [size] pixels wide, casts below itself, as a picture
     * [pad] pixels larger on every side, to be drawn with its corner at (-[pad], -[pad]), as
     * dark as [amount] says (see [Tile.shadow]). The shadow is not under the tile itself, so a tile that lets light
     * through stays clear.
     */
    fun shadow(size: Int, shape: IconShape, pad: Int, amount: Float = 1f): IntArray {
        if (size < MIN_SIZE) return IntArray((size + 2 * pad) * (size + 2 * pad))
        val side = size.toFloat()
        return cast(size, size, pad, { x, y -> shape.depth(x, y, side) }, side * SHADOW_DROP, side * SHADOW_SOFT, amount)
    }

    /** The shadow of an outline [depthAt] of [width] by [height], [drop] pixels below it, [soft] pixels soft. */
    private inline fun cast(
        width: Int,
        height: Int,
        pad: Int,
        depthAt: (Float, Float) -> Float,
        drop: Float,
        soft: Float,
        amount: Float,
    ): IntArray {
        val wide = width + 2 * pad
        val high = height + 2 * pad
        val out = IntArray(wide * high)
        if (amount <= 0f) return out
        val darkest = (SHADOW * amount).coerceAtMost(MOST)
        for (y in 0 until high) {
            val py = y - pad + 0.5f
            val rowOffset = y * wide
            for (x in 0 until wide) {
                val px = x - pad + 0.5f
                val underDepth = depthAt(px, py)
                // Fully under the tile: shadow is masked out completely.
                if (underDepth >= 0.5f) continue
                val cast = smooth(-soft, soft, depthAt(px, py - drop))
                if (cast <= 0f) continue
                val under = (underDepth + 0.5f).coerceIn(0f, 1f)
                out[rowOffset + x] = withAlpha(SHADOW_COLOR, darkest * cast * (1f - under))
            }
        }
        return out
    }

    /** Fast SDF for a square of `2 * half` with corner [radius] and `inner = half - radius`. */
    private fun squareRoundedDepth(x: Float, y: Float, half: Float, radius: Float, inner: Float): Float {
        val qx = abs(x - half) - inner
        val qy = abs(y - half) - inner
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        val outside = if (ox > 0f && oy > 0f) sqrt(ox * ox + oy * oy) else max(ox, oy)
        return radius - outside - min(max(qx, qy), 0f)
    }

    /** The picture at ([x], [y]), between pixels, with the alpha of the pixel it lands on. */
    private fun bent(pixels: IntArray, width: Int, height: Int, x: Float, y: Float, alpha: Int): Int {
        val fx = (x - 0.5f).coerceIn(0f, width - 1f)
        val fy = (y - 0.5f).coerceIn(0f, height - 1f)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val ax = fx - x0
        val ay = fy - y0
        val row0 = y0 * width
        val row1 = y1 * width
        val p00 = pixels[row0 + x0]
        val p10 = pixels[row0 + x1]
        val p01 = pixels[row1 + x0]
        val p11 = pixels[row1 + x1]

        val w00 = (1f - ax) * (1f - ay) * (p00 ushr 24)
        val w10 = ax * (1f - ay) * (p10 ushr 24)
        val w01 = (1f - ax) * ay * (p01 ushr 24)
        val w11 = ax * ay * (p11 ushr 24)
        val weight = w00 + w10 + w01 + w11
        if (weight <= 0f) return pixels[y.toInt().coerceIn(0, height - 1) * width + x.toInt().coerceIn(0, width - 1)]
        val inv = 1f / weight
        val red = ((p00 shr 16 and 0xFF) * w00 + (p10 shr 16 and 0xFF) * w10 +
            (p01 shr 16 and 0xFF) * w01 + (p11 shr 16 and 0xFF) * w11) * inv
        val green = ((p00 shr 8 and 0xFF) * w00 + (p10 shr 8 and 0xFF) * w10 +
            (p01 shr 8 and 0xFF) * w01 + (p11 shr 8 and 0xFF) * w11) * inv
        val blue = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 +
            (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11) * inv
        return (alpha shl 24) or
            (red.toInt().coerceIn(0, 255) shl 16) or
            (green.toInt().coerceIn(0, 255) shl 8) or
            blue.toInt().coerceIn(0, 255)
    }

    /** How light [pixel] looks, from 0 (black) to 1 (white). */
    private fun lightness(pixel: Int): Float =
        ((pixel shr 16 and 0xFF) * 0.2126f + (pixel shr 8 and 0xFF) * 0.7152f + (pixel and 0xFF) * 0.0722f) * (1f / 255f)

    /** Light as it comes off a picture of [pixel]'s colour: mostly white, a little of the colour. */
    private fun paler(pixel: Int): Int =
        tint(pixel or 0xFF000000.toInt(), WHITE, PALER_DARK + (PALER_LIGHT - PALER_DARK) * lightness(pixel))

    /** [pixel] with [colour] laid over it at [share], its alpha kept. */
    private fun tint(pixel: Int, colour: Int, share: Float): Int {
        if (share <= 0f) return pixel
        val keep = 1f - share
        val red = (pixel shr 16 and 0xFF) * keep + (colour shr 16 and 0xFF) * share
        val green = (pixel shr 8 and 0xFF) * keep + (colour shr 8 and 0xFF) * share
        val blue = (pixel and 0xFF) * keep + (colour and 0xFF) * share
        return (pixel and 0xFF000000.toInt()) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
    }

    /** [top] laid over [bottom], both with their own alpha. */
    internal fun over(bottom: Int, top: Int): Int {
        val above = (top ushr 24) * (1f / 255f)
        if (above <= 0f) return bottom
        if (above >= 1f) return top
        val below = (bottom ushr 24) * (1f / 255f) * (1f - above)
        val alpha = above + below
        if (alpha <= 0f) return 0
        val inv = 1f / alpha
        val r = (((top shr 16 and 0xFF) * above + (bottom shr 16 and 0xFF) * below) * inv).toInt().coerceIn(0, 255)
        val g = (((top shr 8 and 0xFF) * above + (bottom shr 8 and 0xFF) * below) * inv).toInt().coerceIn(0, 255)
        val b = (((top and 0xFF) * above + (bottom and 0xFF) * below) * inv).toInt().coerceIn(0, 255)
        return ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (colour and 0xFFFFFF)

    private fun smooth(from: Float, to: Float, value: Float): Float {
        val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    const val WHITE = 0xFFFFFFFF.toInt()
    private const val SHADE_COLOR = 0xFF0A191E.toInt()
    private const val SHADOW_COLOR = 0xFF081418.toInt()

    /** Smaller than this, a tile is too small to show any of it. */
    private const val MIN_SIZE = 8

    /** How far the bevel reaches in from the edge, as a share of the tile, and at the least. */
    private const val BEVEL_SHARE = 0.095f

    /** How much further in each level of the width of the edge reaches. */
    private const val WIDTH_STEP = 0.035f
    private const val MIN_BEVEL = 3f

    /** How far apart the two points are that tell which way the surface faces, in pixels. */
    private const val STEP = 0.75f

    /** Where the light comes from: above, a little from the left. */
    private const val LIGHT_X = -0.45f
    private const val LIGHT_Y = -0.893f

    /** The corner that catches the light: the upper left one. */
    private const val GLINT_X = -0.7071f
    private const val GLINT_Y = -0.7071f
    private const val GLINT_SHARPNESS = 12f

    private const val HIGHLIGHT = 0.34f
    private const val GLINT = 0.30f
    private const val BACK_GLINT = 0.16f
    private const val INNER_LINE = 0.22f
    private const val EXIT_LINE = 0.16f
    private const val SHADE = 0.22f

    /** The glass is thick: its lower part lies in a little shade. */
    private const val THICKNESS = 0.06f

    /** How much of the light a black picture still gives back, against a white one. */
    private const val DARK_LIFT = 0.3f

    /** How close to white the light is that comes off a black picture, and off a white one. */
    private const val PALER_DARK = 0.4f
    private const val PALER_LIGHT = 0.75f

    /** The most any light or shade covers the picture. */
    private const val MOST = 0.8f

    /** How wide the thin line at the very edge is, in pixels. */
    private const val RIM_WIDTH = 1.3f

    /** How far the picture under the bevel is pulled in at the very edge, as a share of the bevel. */
    private const val BEND = 0.55f

    /** The shadow: how far below the tile, how soft, and how dark at most, as shares of the tile. */
    private const val SHADOW_DROP = 0.045f
    private const val SHADOW_SOFT = 0.065f
    private const val SHADOW = 0.24f
}
