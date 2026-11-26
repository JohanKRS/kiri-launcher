package io.github.johankrs.kiri.core.look

/**
 * Blurs a picture held as one ARGB number per pixel, row after row.
 *
 * Kiri uses it on its own small copy of the wallpaper, so that glass can look frosted on any
 * device: not every device lets an app blur what is behind its window. Three passes of a plain
 * average are, to the eye, the soft blur of a lens.
 */
object BoxBlur {
    /**
     * Blurs [pixels] in place. [radius] is how many pixels on each side are averaged in one
     * pass; zero or less changes nothing. The picture is taken as opaque: what was see-through
     * comes out solid.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        require(width >= 0 && height >= 0 && pixels.size >= width * height) { "The picture is smaller than its size" }
        if (radius <= 0 || width == 0 || height == 0) return
        val scratch = IntArray(width * height)
        repeat(passes) {
            average(pixels, scratch, count = width, lines = height, step = 1, lineStep = width, radius = radius)
            average(scratch, pixels, count = height, lines = width, step = width, lineStep = 1, radius = radius)
        }
    }

    /**
     * One pass along one direction: every pixel becomes the average of itself and [radius]
     * neighbours on each side. Past an edge, the pixel on the edge is repeated.
     *
     * A line has [count] pixels, [step] apart; there are [lines] of them, [lineStep] apart.
     */
    private fun average(
        from: IntArray,
        into: IntArray,
        count: Int,
        lines: Int,
        step: Int,
        lineStep: Int,
        radius: Int,
    ) {
        val reach = radius.coerceAtMost(count)
        val window = 2 * reach + 1
        val last = count - 1
        // A fixed-point reciprocal floors 0..(255 * window) exactly only while
        // 255 * window * (window - 1) < 2^24, which holds for any window of up to 256 pixels.
        // Kiri blurs a small copy of the wallpaper, where the window stays far under that.
        val useFastDiv = window <= MAX_FAST_WINDOW
        val inv = if (useFastDiv) ((1L shl 24) + window - 1) / window else 0L
        val midStart = reach
        val midEnd = count - reach - 1
        val reachStep = reach * step
        val enterStep = (reach + 1) * step

        for (line in 0 until lines) {
            val start = line * lineStep
            val firstPixel = from[start]
            val lastPixel = from[start + last * step]
            var red = (firstPixel shr 16 and 0xFF) * (reach + 1)
            var green = (firstPixel shr 8 and 0xFF) * (reach + 1)
            var blue = (firstPixel and 0xFF) * (reach + 1)
            for (offset in 1..reach) {
                val pixel = from[start + offset.coerceAtMost(last) * step]
                red += pixel shr 16 and 0xFF
                green += pixel shr 8 and 0xFF
                blue += pixel and 0xFF
            }

            if (midStart < midEnd && useFastDiv) {
                var outPos = start
                // Left edge: leaving pixel is always firstPixel.
                for (index in 0 until midStart) {
                    val r = (red * inv ushr 24).toInt()
                    val g = (green * inv ushr 24).toInt()
                    val b = (blue * inv ushr 24).toInt()
                    into[outPos] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val entering = from[outPos + enterStep]
                    red += (entering shr 16 and 0xFF) - (firstPixel shr 16 and 0xFF)
                    green += (entering shr 8 and 0xFF) - (firstPixel shr 8 and 0xFF)
                    blue += (entering and 0xFF) - (firstPixel and 0xFF)
                    outPos += step
                }
                // Middle: neither entering nor leaving needs bounds checks.
                for (index in midStart until midEnd) {
                    val r = (red * inv ushr 24).toInt()
                    val g = (green * inv ushr 24).toInt()
                    val b = (blue * inv ushr 24).toInt()
                    into[outPos] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val entering = from[outPos + enterStep]
                    val leaving = from[outPos - reachStep]
                    red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                    green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                    blue += (entering and 0xFF) - (leaving and 0xFF)
                    outPos += step
                }
                // Right edge: entering pixel is always lastPixel.
                for (index in midEnd until count) {
                    val r = (red * inv ushr 24).toInt()
                    val g = (green * inv ushr 24).toInt()
                    val b = (blue * inv ushr 24).toInt()
                    into[outPos] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val leaving = from[start + (index - reach).coerceAtLeast(0) * step]
                    red += (lastPixel shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                    green += (lastPixel shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                    blue += (lastPixel and 0xFF) - (leaving and 0xFF)
                    outPos += step
                }
            } else {
                for (index in 0 until count) {
                    into[start + index * step] =
                        (0xFF shl 24) or (red / window shl 16) or (green / window shl 8) or (blue / window)
                    val entering = from[start + (index + reach + 1).coerceAtMost(last) * step]
                    val leaving = from[start + (index - reach).coerceAtLeast(0) * step]
                    red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                    green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                    blue += (entering and 0xFF) - (leaving and 0xFF)
                }
            }
        }
    }

    /** The widest window [average] divides by with a reciprocal, which is exact up to it. */
    private const val MAX_FAST_WINDOW = 256
}
