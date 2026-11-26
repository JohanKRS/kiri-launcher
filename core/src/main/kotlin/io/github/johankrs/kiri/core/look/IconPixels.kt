package io.github.johankrs.kiri.core.look

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A box of pixels: [left] and [top] are inside it, [right] and [bottom] just outside. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Looks at the picture of an icon (one ARGB number per pixel, row after row) to tell what kind
 * of icon it is, since icons do not say so themselves.
 */
object IconPixels {
    /** Fainter than this, a pixel does not count as part of the drawing. */
    private const val FAINT = 24

    /** At least this solid, a pixel counts as covering what is behind it. */
    private const val SOLID = 128

    /** The box around everything that is drawn, or null when the picture is empty. */
    fun artBounds(pixels: IntArray, width: Int, height: Int): PixelBox? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 > FAINT) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else PixelBox(left, top, right + 1, bottom + 1)
    }

    /**
     * The box around the solid part of the drawing: its body, without the soft shadow many
     * icons carry around themselves. Null when nothing in the picture is solid.
     */
    fun bodyBounds(pixels: IntArray, width: Int, height: Int): PixelBox? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 >= BODY) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else PixelBox(left, top, right + 1, bottom + 1)
    }

    /**
     * The colour of the body near its rim, as a solid ARGB number: what a plate behind the icon
     * is painted with, so that the icon can take another outline without a seam showing.
     *
     * It looks at points on a ring just inside [box] and takes the middle value of each colour,
     * so a drawing that touches the ring here and there does not change the answer. Null when
     * too little of the ring is solid to tell.
     */
    fun rimColor(pixels: IntArray, width: Int, box: PixelBox): Int? {
        val reds = ArrayList<Int>(RIM_POINTS)
        val greens = ArrayList<Int>(RIM_POINTS)
        val blues = ArrayList<Int>(RIM_POINTS)
        for (index in 0 until RIM_POINTS) {
            val angle = index.toDouble() / RIM_POINTS * 2 * PI
            val x = (box.centerX + cos(angle) * box.width / 2 * RIM_SHARE).toInt().coerceIn(box.left, box.right - 1)
            val y = (box.centerY + sin(angle) * box.height / 2 * RIM_SHARE).toInt().coerceIn(box.top, box.bottom - 1)
            val pixel = pixels[y * width + x]
            if (pixel ushr 24 >= BODY) {
                reds += pixel shr 16 and 0xFF
                greens += pixel shr 8 and 0xFF
                blues += pixel and 0xFF
            }
        }
        if (reds.size < RIM_POINTS / 2) return null
        reds.sort()
        greens.sort()
        blues.sort()
        val middle = reds.size / 2
        return (0xFF shl 24) or (reds[middle] shl 16) or (greens[middle] shl 8) or blues[middle]
    }

    /** How much of the whole picture is solid, from 0 to 1. */
    fun coverage(pixels: IntArray, width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 0f
        var solid = 0
        for (index in 0 until width * height) {
            if (pixels[index] ushr 24 >= SOLID) solid++
        }
        return solid.toFloat() / (width * height)
    }

    /**
     * Whether the drawing is a solid body, like a filled tile or disc, and so has a background
     * of its own. A drawing with gaps (a line drawing, a logo with holes, an odd outline) does
     * not: it is what can be put on glass.
     *
     * It looks at a disc in the middle of [box], [DISC_SHARE] of its shorter side across: a
     * solid body covers nearly all of it.
     */
    fun isSolidBody(pixels: IntArray, width: Int, box: PixelBox): Boolean {
        val radius = min(box.width, box.height) * DISC_SHARE / 2f
        if (radius < 1f) return false
        var inside = 0
        var solid = 0
        for (y in box.top until box.bottom) {
            for (x in box.left until box.right) {
                val across = x + 0.5f - box.centerX
                val down = y + 0.5f - box.centerY
                if (across * across + down * down <= radius * radius) {
                    inside++
                    if (pixels[y * width + x] ushr 24 >= SOLID) solid++
                }
            }
        }
        return inside > 0 && solid.toFloat() / inside >= SOLID_SHARE
    }

    /**
     * Whether what is drawn in [box] is a picture with substance, like a badge, a tile or an
     * object, and not a few thin lines: solid pixels take a fair share of the box. A picture
     * is made to fill the shape of an icon; a line drawing would only look heavy that large.
     */
    fun isPicture(pixels: IntArray, width: Int, box: PixelBox): Boolean {
        val area = box.width * box.height
        if (area <= 0) return false
        var solid = 0
        for (y in box.top until box.bottom) {
            for (x in box.left until box.right) {
                if (pixels[y * width + x] ushr 24 >= SOLID) solid++
            }
        }
        return solid.toFloat() / area >= PICTURE_SHARE
    }

    /**
     * The colour for the edge of an icon whose own background is light all around, as a solid
     * ARGB number, or null when it is not light there.
     *
     * The edge of every icon is a bright line. On a white icon that line cannot be told from
     * the icon, which then looks larger than its neighbours. Tinted with the colour the icon
     * itself is drawn in, the line shows again, like the rim of glass bending what is behind
     * it. An icon with no colour of its own gets a quiet grey.
     *
     * [pixels] is the icon as it is shown, [width] by [height].
     */
    fun lightRimTint(pixels: IntArray, width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0) return null
        val reach = min(width, height) / 2f * LIGHT_RING
        var light = 0
        for (index in 0 until RIM_POINTS) {
            val angle = index.toDouble() / RIM_POINTS * 2 * PI
            val x = (width / 2f + cos(angle) * reach).toInt().coerceIn(0, width - 1)
            val y = (height / 2f + sin(angle) * reach).toInt().coerceIn(0, height - 1)
            val pixel = pixels[y * width + x]
            val least = minOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)
            if (pixel ushr 24 >= BODY && least >= LIGHT) light++
        }
        if (light < RIM_POINTS * LIGHT_SHARE) return null

        // The colour of the drawing: what is not white or grey, the vivid parts counting most.
        var red = 0L
        var green = 0L
        var blue = 0L
        var weight = 0L
        val step = (min(width, height) / TINT_SAMPLES).coerceAtLeast(1)
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
                if (pixel ushr 24 < BODY) continue
                val r = pixel shr 16 and 0xFF
                val g = pixel shr 8 and 0xFF
                val b = pixel and 0xFF
                val vivid = maxOf(r, g, b) - minOf(r, g, b)
                if (vivid < VIVID) continue
                red += r.toLong() * vivid
                green += g.toLong() * vivid
                blue += b.toLong() * vivid
                weight += vivid
            }
        }
        if (weight == 0L) return NEUTRAL_RIM
        return (0xFF shl 24) or ((red / weight).toInt() shl 16) or ((green / weight).toInt() shl 8) or (blue / weight).toInt()
    }

    /** Whether the drawing in [box] reaches every edge of a picture of this size. */
    fun fillsPicture(box: PixelBox, width: Int, height: Int): Boolean =
        box.width >= width * FULL_SHARE && box.height >= height * FULL_SHARE

    /** This solid, a pixel is part of the body of an icon and not of its shadow. */
    private const val BODY = 200
    private const val RIM_POINTS = 48
    private const val RIM_SHARE = 0.84
    private const val DISC_SHARE = 0.6f
    private const val SOLID_SHARE = 0.9f
    private const val FULL_SHARE = 0.95f
    private const val PICTURE_SHARE = 0.36f

    /** How far from the middle the rim is looked at: inside every shape an icon can have. */
    private const val LIGHT_RING = 0.86f

    /** This light in every colour, a pixel is white enough to lose a bright line. */
    private const val LIGHT = 224
    private const val LIGHT_SHARE = 0.8f
    private const val VIVID = 48
    private const val TINT_SAMPLES = 48
    private const val NEUTRAL_RIM = 0xFF6B7780.toInt()
}
