package io.github.johankrs.kiri.core.look

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * The shapes the live wallpapers are made of, worked out once: the mist, the threads, the
 * outline of the flower cluster, the hills and where the flowers fall. Drawing them is the
 * app's part; here is only the arithmetic, the same on every device.
 */
object LiveShapes {
    /** 0 below [edge0], 1 above [edge1], and a soft S between them. */
    fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Soft noise on a square of [size] by [size], one value from 0 to 1 per point, row after
     * row. It repeats without a seam both ways, so it can be laid side by side forever.
     */
    fun noise(size: Int, seed: Int): FloatArray {
        require(size > 0) { "The noise needs a size" }
        val random = Random(seed)
        val out = FloatArray(size * size)
        var amplitude = 1f
        for (cells in NOISE_CELLS) {
            val grid = FloatArray(cells * cells) { random.nextFloat() }
            for (y in 0 until size) {
                val fy = y.toFloat() / size * cells
                val iy = floor(fy).toInt()
                val sy = ease(fy - iy)
                val y0 = iy % cells
                val y1 = (iy + 1) % cells
                for (x in 0 until size) {
                    val fx = x.toFloat() / size * cells
                    val ix = floor(fx).toInt()
                    val sx = ease(fx - ix)
                    val x0 = ix % cells
                    val x1 = (ix + 1) % cells
                    val top = lerp(grid[y0 * cells + x0], grid[y0 * cells + x1], sx)
                    val bottom = lerp(grid[y1 * cells + x0], grid[y1 * cells + x1], sx)
                    out[y * size + x] += amplitude * lerp(top, bottom, sy)
                }
            }
            amplitude *= 0.55f
        }
        stretch(out)
        return out
    }

    /**
     * A band of mist [width] wide and [height] tall, as how opaque each point is (0 to 255),
     * row after row: [noise] (a square [width] wide) turned into wisps between [low] and
     * [high], and faded at the band's edges by [fade], which is told how far down the band a
     * row is (0 at its top, 1 at its bottom). It repeats without a seam across.
     */
    fun fogBand(noise: FloatArray, width: Int, height: Int, low: Float, high: Float, fade: (Float) -> Float): IntArray {
        require(noise.size >= width * height) { "The noise is smaller than the band" }
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val edge = fade(y / (height - 1f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            for (x in 0 until width) {
                out[y * width + x] = (smooth(low, high, noise[y * width + x]) * edge * 255f + 0.5f).toInt()
            }
        }
        return out
    }

    /** One row of threads: where it is, how deep, and its pieces as start and length, in pairs. */
    class ThreadRow(
        val y: Float,
        /** 0 is far (thin, faint, slow), 2 is near. */
        val depth: Int,
        /** The row's threads over one [period], as start and length, one after the other. */
        val threads: FloatArray,
        /** Closer threads, for where the row crosses the flower. */
        val dense: FloatArray,
        /** How long the row is before it repeats. */
        val period: Float,
        /** Where the row's light wave starts, in radians. */
        val phase: Float,
    )

    /**
     * The rows of threads for a screen of [width] by [height]. Spacing and lengths follow the
     * shorter side, so a TV and a phone get the same look.
     */
    fun threadRows(width: Float, height: Float, seed: Int = 11): List<ThreadRow> {
        val unit = minOf(width, height) / 1080f
        val gap = 30f * unit
        if (gap <= 0f) return emptyList()
        val random = Random(seed)
        val period = width * 1.6f
        val rows = ArrayList<ThreadRow>()
        var y = gap * 0.7f
        while (y < height) {
            val depth = if (random.nextFloat() < 0.34f) 0 else if (random.nextFloat() < 0.5f) 1 else 2
            val threads = ArrayList<Float>()
            var x = random.nextFloat() * 60f * unit
            while (x < period) {
                val length = (if (random.nextFloat() < 0.2f) 60f + random.nextFloat() * 120f else 4f + random.nextFloat() * 50f) * unit
                if (x + length > period) break
                threads += x
                threads += length
                x += length + (18f + random.nextFloat() * 100f) * unit * (1f + (2 - depth) * 0.45f)
            }
            val dense = ArrayList<Float>()
            x = random.nextFloat() * 20f * unit
            while (x < period) {
                val length = (if (random.nextFloat() < 0.3f) 40f + random.nextFloat() * 70f else 2f + random.nextFloat() * 30f) * unit
                if (x + length > period) break
                dense += x
                dense += length
                x += length + (14f + random.nextFloat() * 22f) * unit
            }
            rows += ThreadRow(y, depth, threads.toFloatArray(), dense.toFloatArray(), period, random.nextFloat() * TAU)
            y += gap
        }
        return rows
    }

    /**
     * How wide the paulownia cluster is, from 0 to 1 of its widest, [along] its height from
     * its top (0) to its bottom (1): narrow at the top, widest low down, then closing towards
     * the stem.
     */
    fun clusterWidth(along: Float): Float = if (along < 0.72f) {
        0.32f + 0.68f * (along.coerceAtLeast(0f) / 0.72f).pow(0.8f)
    } else {
        max(0.05f, 1f - ((along - 0.72f) / 0.26f).coerceAtMost(1f).pow(1.1f))
    }

    /** Where the cluster sits on a screen of [width] by [height]: its middle, top and height. */
    class Cluster(val centerX: Float, val top: Float, val size: Float) {
        /** Half its widest width. */
        val halfWidth: Float get() = size * 0.44f
        val bottom: Float get() = top + size * 1.15f

        /** Its outline as x and y, one after the other, down the right side and up the left. */
        fun outline(steps: Int = 36): FloatArray {
            val points = FloatArray((steps + 1) * 4)
            for (i in 0..steps) {
                val along = i / steps.toFloat()
                points[i * 2] = centerX + halfWidth * clusterWidth(along)
                points[i * 2 + 1] = top + along * size
                val j = (steps + 1) * 2 + i * 2
                val back = 1f - along
                points[j] = centerX - halfWidth * clusterWidth(back)
                points[j + 1] = top + back * size
            }
            return points
        }
    }

    /** The cluster on a screen: to the right of a wide one, which leaves the left to widgets; centred and high on a tall one. */
    fun cluster(width: Float, height: Float): Cluster = if (width >= height) {
        Cluster(centerX = width * 0.72f, top = height * 0.08f, size = height * 0.5f)
    } else {
        Cluster(centerX = width * 0.5f, top = height * 0.1f, size = height * 0.3f)
    }

    /**
     * The skyline of a hill, [points] heights from 0 (its lowest) to 1 (its highest), left to
     * right: a few large swells with smaller ones on them.
     */
    fun ridge(points: Int, seed: Int): FloatArray {
        val count = points.coerceAtLeast(2)
        val random = Random(seed)
        val heights = FloatArray(count)
        for ((cells, amplitude) in RIDGE_OCTAVES) {
            val grid = FloatArray(cells + 2) { random.nextFloat() }
            for (k in 0 until count) {
                val f = k.toFloat() / count * cells
                val i = floor(f).toInt()
                heights[k] += amplitude * lerp(grid[i], grid[i + 1], ease(f - i))
            }
        }
        stretch(heights)
        return heights
    }

    /** One flower falling: where it starts across and down (0 to 1), how near (0 far, 1 near), and how it turns. */
    class Petal(val x: Float, val y: Float, val near: Float, val phase: Float, val turn: Int)

    /** The flowers that fall, farthest first, so nearer ones are drawn over them. */
    fun petals(count: Int = 18, seed: Int = 5): List<Petal> {
        val random = Random(seed)
        return List(count) {
            Petal(
                x = random.nextFloat(),
                y = random.nextFloat(),
                near = random.nextFloat(),
                phase = random.nextFloat() * TAU,
                turn = if (random.nextBoolean()) 1 else -1,
            )
        }.sortedBy { it.near }
    }

    const val TAU = (2.0 * Math.PI).toFloat()

    private val NOISE_CELLS = intArrayOf(4, 8, 16, 32)
    private val RIDGE_OCTAVES = listOf(3 to 1f, 7 to 0.5f, 15 to 0.25f, 31 to 0.12f)

    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Spreads [values] to fill 0 to 1 exactly. */
    private fun stretch(values: FloatArray) {
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (v in values) {
            low = minOf(low, v)
            high = maxOf(high, v)
        }
        val range = (high - low).takeIf { it > 0f } ?: 1f
        for (i in values.indices) values[i] = (values[i] - low) / range
    }
}
