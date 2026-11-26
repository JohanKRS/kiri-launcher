package io.github.johankrs.kiri.data

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.look.GlassBevel
import io.github.johankrs.kiri.core.look.GlassLevel
import io.github.johankrs.kiri.core.look.GlassShine
import io.github.johankrs.kiri.core.look.IconGround
import io.github.johankrs.kiri.core.look.IconLook
import io.github.johankrs.kiri.core.look.IconPixels
import io.github.johankrs.kiri.core.look.IconSettings
import io.github.johankrs.kiri.core.look.IconShape
import io.github.johankrs.kiri.core.look.PixelBox
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An icon ready to be shown: its picture, and what goes around the picture. The relief of the
 * glass is already in the picture; a plate of glass is drawn live by whoever shows it, because
 * glass shows the wallpaper behind itself.
 */
@Immutable
class IconPicture(
    val bitmap: ImageBitmap,
    /** The picture is a drawing to be put on a plate of glass. */
    val plate: Boolean,
    /** The picture is dressed as a tile of glass, with its relief (see [GlassBevel]). */
    val framed: Boolean,
    val shape: IconShape,
    val glass: GlassStyle,
    /** How tinted the plate of glass is, in percent: the rest is the blurred wallpaper. */
    val glassOpacity: Int,
    /**
     * How large the drawing is in the picture, as a percentage of the icon at its longest:
     * where the zoom of this one app starts from when the person changes it.
     */
    val zoomPercent: Int,
    /** The picture was cut to [shape], so light can cross it as it crosses a tile of glass. */
    val shaped: Boolean,
    /** The light the icon catches while it is scrolled. */
    val shine: GlassShine,
    /**
     * The soft shadow the tile casts, larger than [bitmap] by [shadowPad] of its pixels on
     * every side. Null for an icon that is not a tile of glass.
     */
    val shadow: ImageBitmap? = null,
    val shadowPad: Int = 0,
)

/** The outline of an icon of [size] pixels, for drawing into a bitmap. */
internal fun IconShape.path(size: Float): Path = Path().apply {
    when (this@path) {
        IconShape.CIRCLE -> addOval(0f, 0f, size, size, Path.Direction.CW)

        IconShape.ROUNDED, IconShape.SOFT_SQUARE -> {
            val corner = size * cornerShare
            addRoundRect(0f, 0f, size, size, corner, corner, Path.Direction.CW)
        }

        IconShape.SQUIRCLE -> {
            val points = IconShape.squirclePoints()
            moveTo(points[0] * size, points[1] * size)
            for (index in 1 until points.size / 2) {
                lineTo(points[index * 2] * size, points[index * 2 + 1] * size)
            }
            close()
        }
    }
}

/**
 * Dresses the icon an app came with in the look the person chose.
 *
 * Icons are of two kinds. Modern ones come as a drawing and a background, both larger than what
 * is shown, and the launcher cuts them to its shape: those take any shape. Older ones, and most
 * of those in icon packs, are one finished picture, often with a margin of its own that no
 * two packs agree on. Such a picture is measured by what is really drawn in it and made to
 * fill the shape chosen, edge to edge: what sticks out is cut, and no margin is left. One with
 * a body of its own (a tile, a disc) is laid on a plate of its own colour, so no seam shows
 * where the two outlines differ. Only a thin line drawing is not stretched like that: it
 * stays as it is, or goes on glass at the size all drawings on glass have.
 *
 * The size chosen for one app alone wins over all of this.
 */
internal object IconRenderer {
    /** Icons are looked at in a small copy of this many pixels a side: enough to tell the kind. */
    private const val PROBE = 96

    /** A modern icon is drawn this much larger than what is seen of it (108 units for 72). */
    private const val LAYER_SCALE = 1.5f

    /**
     * Buffers one icon is dressed in, lent out for that and then kept for the next one, so that
     * no icon allocates temporary bitmaps and arrays of its own.
     */
    private class Scratch {
        val probeBitmap: Bitmap = Bitmap.createBitmap(PROBE, PROBE, Bitmap.Config.ARGB_8888)
        val probeCanvas: Canvas = Canvas(probeBitmap)
        val probePixels: IntArray = IntArray(PROBE * PROBE)
        var srcPixels: IntArray = IntArray(0)
        var dstPixels: IntArray = IntArray(0)

        fun ensurePixelBuffers(total: Int) {
            if (srcPixels.size < total) srcPixels = IntArray(total)
            if (dstPixels.size < total) dstPixels = IntArray(total)
        }
    }

    /**
     * The buffers no icon is using now: never more than icons are dressed at once, and let go
     * of when memory runs short ([clearCaches]).
     */
    private val spare = ArrayDeque<Scratch>()

    private fun borrow(): Scratch = synchronized(spare) { spare.removeLastOrNull() } ?: Scratch()

    private fun giveBack(buffers: Scratch) {
        val kept = synchronized(spare) { spare.size < MAX_SPARE_SCRATCH && spare.add(buffers) }
        if (!kept) buffers.probeBitmap.recycle()
    }

    /**
     * [appArtPercent] and [appGround] are what was chosen for this app alone: the size of its
     * drawing and what it sits on. Null follows [look].
     */
    fun render(drawable: Drawable, size: Int, look: IconLook, appArtPercent: Int?, appGround: IconGround?): IconPicture {
        val buffers = borrow()
        try {
            return if (drawable is AdaptiveIconDrawable) {
                buffers.modern(drawable, size, look, appArtPercent, appGround)
            } else {
                buffers.finished(drawable, size, look, appArtPercent, appGround)
            }
        } finally {
            giveBack(buffers)
        }
    }

    private fun Scratch.modern(
        icon: AdaptiveIconDrawable,
        size: Int,
        look: IconLook,
        appArtPercent: Int?,
        appGround: IconGround?,
    ): IconPicture {
        val foreground: Drawable? = icon.foreground
        val background: Drawable? = icon.background
        val art = foreground?.let { layer ->
            IconPixels.artBounds(probe { drawLayer(it, layer, PROBE) }, PROBE, PROBE)
        }
        val ownBackground = background != null &&
            IconPixels.coverage(probe { drawLayer(it, background, PROBE) }, PROBE, PROBE) >= 0.5f
        // A drawing that reaches every edge is a whole picture: nothing to lift off and resize.
        val wholePicture = art != null && IconPixels.fillsPicture(art, PROBE, PROBE)
        val wantsGlass = IconSettings.wantsGlass(look.glassUse, appGround, ownBackground)
        // Glass asked for this very app is given even to a whole picture: its background goes.
        val onGlass = wantsGlass && foreground != null && art != null && (!wholePicture || appGround == IconGround.GLASS)
        val artPercent = appArtPercent ?: if (onGlass) look.artPercent else null

        val bitmap = cutTo(look.shape, size) { canvas ->
            if (!onGlass && background != null) drawLayer(canvas, background, size)
            if (foreground != null) {
                val saved = canvas.save()
                if (artPercent != null && art != null && (onGlass || !wholePicture)) canvas.fit(art, size, artPercent)
                drawLayer(canvas, foreground, size)
                canvas.restoreToCount(saved)
            }
        }
        return finish(
            bitmap = bitmap,
            look = look,
            plate = onGlass,
            framed = look.relief != GlassLevel.OFF && (onGlass || ownBackground || wholePicture),
            shaped = true,
            zoomPercent = artPercent ?: natural(art),
        )
    }

    private fun Scratch.finished(
        icon: Drawable,
        size: Int,
        look: IconLook,
        appArtPercent: Int?,
        appGround: IconGround?,
    ): IconPicture {
        val pixels = probe { canvas ->
            icon.setBounds(0, 0, PROBE, PROBE)
            icon.draw(canvas)
        }
        val art = IconPixels.artBounds(pixels, PROBE, PROBE)
        // The solid part alone: many finished icons carry a soft shadow around themselves.
        val body = IconPixels.bodyBounds(pixels, PROBE, PROBE)
        val ownBackground = body != null && IconPixels.isSolidBody(pixels, PROBE, body)
        // A picture with substance fills the shape; a few thin lines do not.
        val fills = ownBackground || (art != null && IconPixels.isPicture(pixels, PROBE, art))
        val wantsGlass = IconSettings.wantsGlass(look.glassUse, appGround, ownBackground)
        val onGlass = wantsGlass && art != null
        // An icon with a body of its own takes the shape of all the others too: a plate in the
        // colour of its rim fills the shape, and the body is laid on it as large as it goes.
        // What the two outlines do not share is then of one colour, and no seam shows.
        val plateColor = if (!onGlass && ownBackground) IconPixels.rimColor(pixels, PROBE, body) else null
        // The body without its shadow when it has one, and otherwise all that is drawn.
        val fitted: PixelBox? = if (ownBackground) body else art
        val percent = appArtPercent ?: when {
            // Glass asked for this very app is meant to be seen: there the drawing is small.
            fills && appGround != IconGround.GLASS -> IconLook.FILL_PERCENT
            onGlass -> look.artPercent
            else -> null
        }

        val draw: (Canvas) -> Unit = { canvas ->
            if (plateColor != null) canvas.drawColor(plateColor)
            val saved = canvas.save()
            if (percent != null && fitted != null) canvas.fit(fitted, size, percent)
            icon.setBounds(0, 0, size, size)
            icon.draw(canvas)
            canvas.restoreToCount(saved)
        }
        val shaped = onGlass || fills || percent != null
        val bitmap = if (shaped) cutTo(look.shape, size, draw) else picture(size, draw).asImageBitmap()
        return finish(
            bitmap = bitmap,
            look = look,
            plate = onGlass,
            framed = look.relief != GlassLevel.OFF && (onGlass || fills),
            shaped = shaped,
            zoomPercent = percent ?: natural(fitted),
        )
    }

    /**
     * The icon ready to be shown. A [framed] one is made a tile of glass here, once: its relief
     * goes into the picture, over it, or under the drawing when it lies on a [plate]. A picture
     * so light all around that a bright edge would be lost on it gets an edge in a colour of
     * its own drawing instead, like the rim of glass bending the colour under it.
     */
    private fun Scratch.finish(
        bitmap: ImageBitmap,
        look: IconLook,
        plate: Boolean,
        framed: Boolean,
        shaped: Boolean,
        zoomPercent: Int,
    ): IconPicture {
        var shadow: ImageBitmap? = null
        var pad = 0
        if (framed && shaped) {
            val android = bitmap.asAndroidBitmap()
            val side = android.width
            if (side == android.height && android.isMutable && side > 0) {
                val total = side * side
                val useScratch = side <= MAX_SCRATCH_SIDE
                val pixels = if (useScratch) {
                    ensurePixelBuffers(total)
                    srcPixels
                } else {
                    IntArray(total)
                }
                val into = if (useScratch) dstPixels else null
                android.getPixels(pixels, 0, side, 0, 0, side, side)
                val rimTint = if (plate) null else IconPixels.lightRimTint(pixels, side, side)
                val dressed = GlassBevel.dress(
                    pixels = pixels,
                    size = side,
                    shape = look.shape,
                    light = GlassBevel.lightOf(look.glass, rimTint),
                    bend = !plate,
                    under = plate,
                    tile = GlassBevel.Tile.of(look),
                    into = into,
                )
                android.setPixels(dressed, 0, side, 0, 0, side, side)
                if (look.shadow != GlassLevel.OFF) {
                    pad = shadowPad(side)
                    shadow = shadowOf(look.shape, look.shadow, side)
                }
            }
        }
        return IconPicture(
            bitmap = bitmap,
            plate = plate,
            framed = framed,
            shape = look.shape,
            glass = look.glass,
            glassOpacity = look.glassOpacity,
            zoomPercent = zoomPercent,
            shaped = shaped,
            shine = look.shine,
            shadow = shadow,
            shadowPad = pad,
        )
    }

    // Bounded cache: one shadow for every icon of a shape, darkness and size.
    private val shadows = LruCache<Int, ImageBitmap>(MAX_SHADOW_ENTRIES)

    /** Releases cached shadow bitmaps and bevel geometry under memory pressure. */
    fun clearCaches() {
        shadows.evictAll()
        val idle = synchronized(spare) { spare.toList().also { spare.clear() } }
        idle.forEach { it.probeBitmap.recycle() }
        GlassBevel.clearCaches()
    }

    private fun shadowPad(side: Int): Int = ceil(side * SHADOW_PAD).toInt()

    private fun shadowOf(shape: IconShape, level: GlassLevel, side: Int): ImageBitmap {
        val key = (shape.ordinal * 10 + level.ordinal) * 100_000 + side
        shadows.get(key)?.let { return it }
        val pad = shadowPad(side)
        val whole = side + 2 * pad
        val pixels = GlassBevel.shadow(side, shape, pad, GlassBevel.Tile.of(IconLook(shadow = level)).shadow)
        val created = Bitmap.createBitmap(pixels, whole, whole, Bitmap.Config.ARGB_8888).asImageBitmap()
        return synchronized(shadows) {
            shadows.get(key) ?: created.also { shadows.put(key, it) }
        }
    }

    /** How far the shadow of a tile reaches past it, as a share of the tile. */
    private const val SHADOW_PAD = 0.14f
    private const val MAX_SHADOW_ENTRIES = 16
    private const val MAX_SCRATCH_SIDE = 320
    private const val MAX_SPARE_SCRATCH = 4

    /** How large the drawing found in [art] is as it came, as a percentage of the icon. */
    private fun natural(art: PixelBox?): Int =
        if (art == null) IconLook.FILL_PERCENT else max(art.width, art.height) * 100 / PROBE

    /** Draws one layer of a modern icon so that its middle part fills [size] pixels. */
    private fun drawLayer(canvas: Canvas, layer: Drawable, size: Int) {
        val full = (size * LAYER_SCALE).roundToInt()
        val outside = (full - size) / 2
        layer.setBounds(-outside, -outside, full - outside, full - outside)
        layer.draw(canvas)
    }

    /**
     * Makes what is drawn next land so that the drawing found in [art] (measured in the small
     * copy) is [percent] of the icon at its longest, in the middle.
     */
    private fun Canvas.fit(art: PixelBox, size: Int, percent: Int) {
        val toSize = size / PROBE.toFloat()
        val longest = max(art.width, art.height) * toSize
        if (longest <= 0f) return
        val zoom = size * percent / 100f / longest
        translate(size / 2f, size / 2f)
        scale(zoom, zoom)
        translate(-art.centerX * toSize, -art.centerY * toSize)
    }

    private fun picture(size: Int, draw: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        return bitmap
    }

    /** What [draw] draws, cut to [shape] with a smooth edge. */
    private fun cutTo(shape: IconShape, size: Int, draw: (Canvas) -> Unit): ImageBitmap {
        val whole = picture(size, draw)
        val cut = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(whole, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(cut).drawPath(shape.path(size.toFloat()), paint)
        whole.recycle()
        return cut.asImageBitmap()
    }

    /**
     * The pixels of a small picture drawn by [draw], to look at. They are in these buffers, so
     * they are only good until the next probe.
     */
    private fun Scratch.probe(draw: (Canvas) -> Unit): IntArray {
        probeCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        draw(probeCanvas)
        probeBitmap.getPixels(probePixels, 0, PROBE, 0, 0, PROBE, PROBE)
        return probePixels
    }
}
