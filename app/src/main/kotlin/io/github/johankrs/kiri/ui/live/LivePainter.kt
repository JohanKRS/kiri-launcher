package io.github.johankrs.kiri.ui.live

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import io.github.johankrs.kiri.core.look.LiveColor
import io.github.johankrs.kiri.core.look.LiveScene
import io.github.johankrs.kiri.core.look.LiveShapes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws Kiri's live wallpapers on any canvas: the TV's own surface, the live wallpaper's, a
 * preview in the settings, or a still picture of one for glass to blur.
 *
 * Every scene is a handful of soft lights, a few bands of mist (one small picture of noise,
 * laid side by side) and, for some, a picture made once and kept: the tiles, the hills, the
 * flowers. Nothing is allocated while drawing but the moving bands of light of one scene.
 *
 * One painter is for one thread: each surface has its own.
 */
class LivePainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val picture = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    // What was made for the scene, color and size last drawn; all of it goes when one changes.
    private var made = ""
    private val shaders = HashMap<String, Shader>()
    private val bitmaps = HashMap<String, Bitmap>()
    private var rows: List<LiveShapes.ThreadRow> = emptyList()
    private var cluster: LiveShapes.Cluster? = null
    private val clusterPath = Path()

    // Where the threads of one frame go before they are drawn, sorted by how they look.
    private var lines = FloatArray(0)
    private var sorted = FloatArray(0)
    private var keys = IntArray(0)
    private val counts = IntArray(LINE_KEYS + 1)

    /**
     * Draws [scene] in [color] over all of a [width] by [height] canvas, as it is [time]
     * seconds into its motion.
     */
    fun draw(canvas: Canvas, width: Int, height: Int, time: Double, scene: LiveScene, color: LiveColor) {
        if (width <= 0 || height <= 0) return
        val key = "${scene.key}/${color.key}/$width/$height"
        if (key != made) {
            release()
            made = key
        }
        val w = width.toFloat()
        val h = height.toFloat()
        when (scene) {
            LiveScene.AURORA -> aurora(canvas, w, h, time, color)
            LiveScene.BRUMA -> bruma(canvas, w, h, time, color)
            LiveScene.FIOS -> fios(canvas, w, h, time, color)
            LiveScene.PASTILHAS -> pastilhas(canvas, w, h, time, color)
            LiveScene.SERRA -> serra(canvas, w, h, time, color)
            LiveScene.FLORES -> flores(canvas, w, h, time, color)
        }
    }

    /** Lets go of every picture made for the last scene. */
    fun release() {
        for (bitmap in bitmaps.values) bitmap.recycle()
        bitmaps.clear()
        shaders.clear()
        rows = emptyList()
        cluster = null
        made = ""
    }

    // ---------- the scenes ----------

    private fun aurora(canvas: Canvas, w: Float, h: Float, time: Double, c: LiveColor) {
        val m = max(w, h)
        val k = if (c.light) 0.75f else 1f
        base(canvas, w, h, c, 0.5f, 0.45f)
        glow(canvas, w * (0.2f + 0.14f * s(time * 0.07)), h * (0.78f + 0.1f * co(time * 0.05)), m * 0.75f, hue(c.top, -48f), 0.5f * k)
        glow(canvas, w * (0.8f + 0.12f * co(time * 0.06)), h * (0.2f + 0.12f * s(time * 0.08)), m * 0.7f, c.top, 0.48f * k)
        glow(canvas, w * (0.55f + 0.2f * s(time * 0.045 + 1)), h * (0.5f + 0.15f * co(time * 0.055 + 2)), m * 0.55f, hue(c.top, 42f), 0.3f * k)
        glow(canvas, w * (0.35f + 0.18f * co(time * 0.035 + 3)), h * (0.28f + 0.1f * s(time * 0.065)), m * 0.5f, c.mist, if (c.light) 0.2f else 0.1f)
    }

    private fun bruma(canvas: Canvas, w: Float, h: Float, time: Double, c: LiveColor) {
        val wide = w >= h
        val m = max(w, h)
        base(canvas, w, h, c, 0.5f, 0.3f)
        glow(canvas, w * (if (wide) 0.72f else 0.62f) + s(time * 0.045) * w * 0.04f, h * (if (wide) 0.24f else 0.2f), m * 0.55f, c.top, if (c.light) 0.4f else 0.3f)
        glow(canvas, w * 0.16f + co(time * 0.038) * w * 0.03f, h * 0.92f, m * 0.5f, if (c.light) c.top else c.ground, if (c.light) 0.12f else 0.55f)
        val all = if (c.light) 0.5f else 0.46f
        for ((i, layer) in BRUMA_LAYERS.withIndex()) {
            val shader = fogShader(i, 0.35f, 0.85f, FADE_DOWN, c.fog)
            val top = h * layer.top + s(time * 0.06 + i * 2) * h * 0.015f
            fog(canvas, shader, w, -(time * layer.speed * w), top, h * layer.height, layer.stretch, layer.alpha * all)
        }
    }

    private fun fios(canvas: Canvas, w: Float, h: Float, time: Double, c: LiveColor) {
        if (rows.isEmpty()) rows = LiveShapes.threadRows(w, h)
        val cl = cluster ?: LiveShapes.cluster(w, h).also { made ->
            cluster = made
            clusterPath.reset()
            val outline = made.outline()
            clusterPath.moveTo(outline[0], outline[1])
            for (i in 2 until outline.size step 2) clusterPath.lineTo(outline[i], outline[i + 1])
            clusterPath.close()
            clusterPath.addRect(made.centerX - made.size * 0.022f, made.top + made.size * 0.95f, made.centerX + made.size * 0.022f, made.top + made.size * 1.15f, Path.Direction.CW)
        }
        val unit = min(w, h) / 1080f
        base(canvas, w, h, c, 0.5f, 0.3f)
        glow(canvas, cl.centerX, cl.top + cl.size * 0.5f, cl.size * 1.1f, c.top, if (c.light) 0.26f else 0.22f)

        // Everywhere: thin and faint far away, thicker and brighter near, paler low down.
        line.shader = shaders.getOrPut("threads") {
            LinearGradient(0f, 0f, 0f, h, intArrayOf(c.top, c.top, c.mist), floatArrayOf(0f, 0.1f, 0.95f), Shader.TileMode.CLAMP)
        }
        threads(canvas, w, h, time, inside = false, unit = unit, cl = cl)
        // Inside the cluster: closer threads, bright, that draw the flower as they pass.
        line.shader = shaders.getOrPut("flower") {
            LinearGradient(0f, cl.top, 0f, cl.top + cl.size, c.top, mix(c.top, c.mist, 0.75f), Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.clipPath(clusterPath)
        threads(canvas, w, h, time, inside = true, unit = unit, cl = cl)
        canvas.restore()
        line.shader = null
    }

    /**
     * One frame of threads. They are gathered first and drawn a few at a time: all the ones of
     * the same thickness and about the same brightness together.
     */
    private fun threads(canvas: Canvas, w: Float, h: Float, time: Double, inside: Boolean, unit: Float, cl: LiveShapes.Cluster) {
        val breath = 0.8f + 0.2f * s(time * 0.5)
        val brightest = if (inside) 0.78f else 0.35f
        val drift = ((time * 0.22) % TAU).toFloat()
        var n = 0
        for (row in rows) {
            if (inside && (row.y < cl.top - 4f || row.y > cl.bottom + 4f)) continue
            val list = if (inside) row.dense else row.threads
            val speed = if (inside) 0.009 else 0.006 + row.depth * 0.0065
            val off = ((time * speed * w) % row.period).toFloat()
            val strength = if (inside) {
                0.78f * breath
            } else {
                (0.15f + row.depth * 0.1f) * (0.35f + 0.65f * LiveShapes.smooth(0.22f, 0.92f, row.y / h))
            }
            ensureLines(n + list.size)
            var i = 0
            while (i < list.size) {
                var x = list[i] - off
                val length = list[i + 1]
                if (x + length < 0f) x += row.period
                for (copy in 0..1) {
                    val xx = if (copy == 0) x else x + row.period
                    if (xx > w || xx + length < 0f) continue
                    val wave = 0.5f + 0.5f * sin((xx / w) * 5.2f - drift + row.phase)
                    val alpha = strength * (if (inside) 0.7f + 0.3f * wave else 0.35f + 0.65f * wave)
                    val level = ((alpha / brightest) * LEVELS).toInt().coerceIn(0, LEVELS - 1)
                    val o = n * 4
                    lines[o] = xx
                    lines[o + 1] = row.y
                    lines[o + 2] = xx + length
                    lines[o + 3] = row.y
                    keys[n] = (if (inside) 0 else row.depth) * LEVELS + level
                    n++
                }
                i += 2
            }
        }
        if (n == 0) return
        // Sorted by how they look, so each look is one call.
        counts.fill(0)
        for (k in 0 until n) counts[keys[k] + 1]++
        for (k in 1..LINE_KEYS) counts[k] += counts[k - 1]
        if (sorted.size < n * 4) sorted = FloatArray(lines.size)
        val at = counts.copyOf()
        for (k in 0 until n) {
            val to = at[keys[k]]++ * 4
            System.arraycopy(lines, k * 4, sorted, to, 4)
        }
        for (key in 0 until LINE_KEYS) {
            val from = counts[key]
            val count = counts[key + 1] - from
            if (count == 0) continue
            val depth = key / LEVELS
            val level = key % LEVELS
            line.strokeWidth = max(0.8f, (if (inside) 7f else 4.4f + depth * 2.2f) * unit)
            line.alpha = (((level + 0.5f) / LEVELS) * brightest * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawLines(sorted, from * 4, count * 4, line)
        }
        line.alpha = 255
    }

    private fun ensureLines(segments: Int) {
        val needed = segments * 2 + 16
        if (keys.size < needed) {
            keys = keys.copyOf(needed * 2)
            lines = lines.copyOf(needed * 8)
            sorted = FloatArray(needed * 8)
        }
    }

    private fun pastilhas(canvas: Canvas, w: Float, h: Float, time: Double, c: LiveColor) {
        val tiles = bitmaps.getOrPut("tiles") { tiles(w, h, c) }
        base(canvas, w, h, c, 0.42f, 0.25f)
        glow(canvas, w * 0.85f, h * 0.1f, max(w, h) * 0.5f, c.top, if (c.light) 0.25f else 0.18f)
        picture.alpha = ((if (c.light) 0.14f else 0.045f) * 255).roundToInt()
        canvas.drawBitmap(tiles, 0f, 0f, picture)
        picture.alpha = 255
        val tilesShader = shaders.getOrPut("tilesShader") { BitmapShader(tiles, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val angle = 20f * PI.toFloat() / 180f
        val dx = cos(angle)
        val dy = sin(angle)
        val across = w * dx + h * dy
        for (band in LIGHT_BANDS) {
            val p = ((((time + band.offset) / band.period) % 1.0).toFloat()) * 1.9f - 0.45f
            val middle = p * across
            val reach = band.width * across
            if (middle + reach < 0f || middle - reach > across) continue
            val x0 = dx * (middle - reach)
            val y0 = dy * (middle - reach)
            val x1 = dx * (middle + reach)
            val y1 = dy * (middle + reach)
            val light = if (c.light) Color.WHITE else c.mist
            fill.shader = LinearGradient(
                x0, y0, x1, y1,
                intArrayOf(alpha(light, 0f), alpha(light, (if (c.light) 0.25f else 0.09f) * band.alpha), alpha(light, 0f)),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, fill)
            // The tiles show only where the light is.
            val mask = LinearGradient(x0, y0, x1, y1, intArrayOf(0, Color.BLACK, 0), null, Shader.TileMode.CLAMP)
            fill.shader = ComposeShader(tilesShader, mask, PorterDuff.Mode.DST_IN)
            fill.alpha = (band.alpha * 255).roundToInt()
            canvas.drawRect(0f, 0f, w, h, fill)
            fill.alpha = 255
        }
        fill.shader = null
    }

    /** The wall of glass tiles, made once: a faint fill and a lit edge, brighter at the top left. */
    private fun tiles(w: Float, h: Float, c: LiveColor): Bitmap {
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val into = Canvas(bitmap)
        val unit = min(w, h) / 1080f
        val side = 232f * unit
        val gap = 18f * unit
        val corner = side * 0.27f
        val columns = (w / (side + gap)).toInt() + 2
        val lines = (h / (side + gap)).toInt() + 2
        val left = (w - (columns * (side + gap) - gap)) / 2f
        val top = (h - (lines * (side + gap) - gap)) / 2f - side * 0.18f
        val face = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(0.8f, 2.4f * unit)
        }
        for (j in 0 until lines) {
            for (i in 0 until columns) {
                val x = left + i * (side + gap)
                val y = top + j * (side + gap)
                face.shader = LinearGradient(x, y, x + side, y + side, intArrayOf(alpha(c.mist, 0.22f), alpha(c.mist, 0.05f), alpha(c.mist, 0.12f)), null, Shader.TileMode.CLAMP)
                edge.shader = LinearGradient(x, y, x + side, y + side, intArrayOf(alpha(c.mist, 1f), alpha(c.mist, 0.35f), alpha(c.mist, 0.7f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                box.set(x, y, x + side, y + side)
                into.drawRoundRect(box, corner, corner, face)
                into.drawRoundRect(box, corner, corner, edge)
            }
        }
        return bitmap
    }

    private fun serra(canvas: Canvas, w: Float, h: Float, time: Double, c: LiveColor) {
        val wide = w >= h
        val short = min(w, h)
        val bases = if (wide) SERRA_BASES_WIDE else SERRA_BASES_TALL
        fill.shader = shaders.getOrPut("sky") {
            LinearGradient(0f, 0f, 0f, h, intArrayOf(c.deep, c.ground, mix(c.ground, c.mist, 0.18f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        val mx = w * (if (wide) 0.7f else 0.68f)
        val my = h * (if (wide) 0.26f else 0.3f)
        glow(canvas, mx, my, short * 0.42f, c.top, if (c.light) 0.35f else 0.28f)
        glow(canvas, mx, my, short * 0.06f, if (c.light) c.top else c.mist, if (c.light) 0.6f else 0.95f)
        for (i in bases.indices) {
            val height = SERRA_HEIGHTS[i] * short * (if (wide) 1f else 0.9f)
            val ridge = bitmaps.getOrPut("ridge$i") { ridge(w, h, c, i, h * bases[i], height) }
            canvas.drawBitmap(ridge, 0f, 0f, picture)
            if (i == bases.lastIndex) continue
            // Mist in the valley in front of this hill, behind the next.
            val middle = h * bases[i] - height * 0.05f
            val reach = height * 0.95f
            val shader = fogShader(i % NOISE_COUNT, 0.3f, 0.8f, FADE_BAND, c.fog)
            fog(canvas, shader, w, -(time * (0.012 + i * 0.008) * w), middle - reach, reach * 2f, 0.6f * short / reach, if (c.light) 0.5f else 0.42f)
        }
    }

    /** One hill, made once: its skyline over [base], up to [height] above it, filled down to the bottom. */
    private fun ridge(w: Float, h: Float, c: LiveColor, index: Int, base: Float, height: Float): Bitmap {
        val far = mix(c.ground, c.mist, if (c.light) 0.35f else 0.3f)
        val near = if (c.light) mix(c.top, 0xFF2A2046.toInt(), 0.35f) else mix(c.deep, Color.BLACK, 0.35f)
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val points = (w / 2f).toInt().coerceAtLeast(2) + 2
        val heights = LiveShapes.ridge(points, 100 + index * 17)
        val path = Path().apply {
            moveTo(0f, h)
            for (k in 0 until points) {
                lineTo(k * w / (points - 1), base - height * (0.15f + 0.85f * heights[k].pow(1.35f)))
            }
            lineTo(w, h)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mix(far, near, index / 3f) }
        Canvas(bitmap).drawPath(path, paint)
        return bitmap
    }

    private fun flores(canvas: Canvas, w: Float, h: Float, time: Double, c: LiveColor) {
        val wide = w >= h
        val unit = min(w, h) / 1080f
        val m = max(w, h)
        base(canvas, w, h, c, 0.55f, 0.25f)
        glow(canvas, w * 0.78f, h * 0.12f, m * 0.5f, c.top, if (c.light) 0.3f else 0.22f)
        glow(canvas, w * 0.3f, h * 1.05f, m * 0.55f, c.mist, if (c.light) 0.12f else 0.1f)
        for (petal in PETALS) {
            val near = petal.near
            val size = (46f + 175f * near.pow(1.7f)) * unit * (if (wide) 1f else 1.15f)
            val span = h + 2f * size
            val y = (((petal.y * span) + time * (0.012 + 0.02 * near) * h) % span).toFloat() - size
            val x = petal.x * w + s(time * (0.3 + 0.25 * near) + petal.phase) * w * 0.022f * (0.5f + near)
            val soft = when {
                near > 0.8f -> 2
                near < 0.25f -> 1
                else -> 0
            }
            val flower = bitmaps.getOrPut("flower$soft") { flower(c, soft) }
            val turn = ((petal.phase + time * (0.12 + 0.18 * near) * petal.turn) % (2 * PI)).toFloat()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(turn * 180f / PI.toFloat())
            picture.alpha = ((if (near > 0.8f) 0.6f else 0.42f + 0.5f * near) * 255).roundToInt().coerceIn(0, 255)
            box.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
            canvas.drawBitmap(flower, null, box, picture)
            canvas.restore()
        }
        picture.alpha = 255
    }

    /**
     * One paulownia flower seen from the front, made once: two small lobes above, three large
     * ones below, a pale throat with a few dots. [soft] 1 is slightly out of focus, 2 very.
     */
    private fun flower(c: LiveColor, soft: Int): Bitmap {
        val size = FLOWER_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val into = Canvas(bitmap)
        into.translate(size / 2f, size / 2f)
        val blur = when (soft) {
            1 -> 1.5f
            2 -> 6f
            else -> 0f
        }
        val pale = mix(c.mist, Color.WHITE, 0.4f)
        val deep = if (c.light) mix(c.top, 0xFF2A2046.toInt(), 0.25f) else mix(c.top, c.ground, 0.35f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            shader = RadialGradient(0f, 4f, 48f, intArrayOf(pale, mix(c.top, pale, 0.35f), c.top, deep), floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP)
        }
        for (lobe in LOBES) {
            val a = lobe[0] * PI.toFloat() / 180f
            into.drawCircle(cos(a) * lobe[1], sin(a) * lobe[1] + 4f, lobe[2], paint)
        }
        paint.shader = null
        paint.color = alpha(mix(pale, 0xFFF6E7A8.toInt(), 0.45f), 0.95f)
        box.set(-7f, -4f, 7f, 16f)
        into.drawOval(box, paint)
        paint.color = alpha(deep, 0.55f)
        for (dot in DOTS) into.drawCircle(dot[0], dot[1], 1.3f, paint)
        return bitmap
    }

    // ---------- what every scene is made of ----------

    /** The background: the color's ground in a soft circle round ([cx], [cy]), deep at the edges. */
    private fun base(canvas: Canvas, w: Float, h: Float, c: LiveColor, cx: Float, cy: Float) {
        fill.shader = shaders.getOrPut("base") {
            RadialGradient(w * cx, h * cy, hypot(w, h) * 0.62f, c.ground, c.deep, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    /**
     * A soft round light of [color] at ([x], [y]), [radius] across, [strength] at its middle.
     * The same small gradient serves every size: the canvas is moved and stretched instead.
     */
    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, strength: Float) {
        if (radius <= 0f || strength <= 0f) return
        val a = strength.coerceIn(0f, 1f)
        val shader = shaders.getOrPut("glow/${color and 0xFFFFFF}/${(a * 255).roundToInt()}") {
            RadialGradient(
                0f, 0f, 1f,
                intArrayOf(alpha(color, a), alpha(color, a * 0.45f), alpha(color, a * 0.12f), alpha(color, 0f)),
                floatArrayOf(0f, 0.4f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        fill.shader = shader
        canvas.drawRect(-1f, -1f, 1f, 1f, fill)
        canvas.restore()
        fill.shader = null
    }

    /** A band of mist of [color], from [noiseAt]'s noise, faded at its edges as [fade] says. */
    private fun fogShader(noiseAt: Int, low: Float, high: Float, fade: Int, color: Int): Shader =
        shaders.getOrPut("fog/$noiseAt/$low/$high/$fade") {
            val alphas = LiveShapes.fogBand(NOISES[noiseAt], BAND_W, BAND_H, low, high) { along ->
                when (fade) {
                    FADE_DOWN -> LiveShapes.smooth(0f, 0.6f, along)
                    else -> if (along < 0.55f) LiveShapes.smooth(0f, 0.55f, along) else 1f - LiveShapes.smooth(0.55f, 1f, along)
                }
            }
            val rgb = color and 0xFFFFFF
            val pixels = IntArray(alphas.size) { (alphas[it] shl 24) or rgb }
            val bitmap = Bitmap.createBitmap(pixels, BAND_W, BAND_H, Bitmap.Config.ARGB_8888)
            bitmaps["fog/$noiseAt/$low/$high/$fade"] = bitmap
            BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        }

    /**
     * Lays a band of mist across the screen: its top at [top], [height] tall, stretched
     * [stretch] times wider than tall, moved [shift] to the side.
     */
    private fun fog(canvas: Canvas, shader: Shader, w: Float, shift: Double, top: Float, height: Float, stretch: Float, strength: Float) {
        if (height <= 0f) return
        val sy = height / BAND_H
        val sx = sy * stretch
        val tile = BAND_W * sx
        var ox = (shift % tile).toFloat()
        if (ox > 0f) ox -= tile
        canvas.save()
        canvas.translate(ox, top)
        canvas.scale(sx, sy)
        fill.shader = shader
        fill.alpha = (strength * 255).roundToInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, (w - ox) / sx, BAND_H.toFloat(), fill)
        canvas.restore()
        fill.shader = null
        fill.alpha = 255
    }

    private class Layer(val top: Float, val height: Float, val stretch: Float, val speed: Double, val alpha: Float)

    private class Band(val period: Double, val offset: Double, val width: Float, val alpha: Float)

    private companion object {
        const val TAU = LiveShapes.TAU
        const val LEVELS = 12
        const val LINE_KEYS = 3 * LEVELS
        const val BAND_W = 128
        const val BAND_H = 64
        const val NOISE_COUNT = 3
        const val FADE_DOWN = 0
        const val FADE_BAND = 1
        const val FLOWER_SIZE = 160

        /** Three noises, made once for the whole app: every band of mist is one of them. */
        val NOISES: List<FloatArray> by lazy { listOf(7, 23, 51).map { LiveShapes.noise(BAND_W, it) } }

        val BRUMA_LAYERS = listOf(
            Layer(top = -0.3f, height = 1.35f, stretch = 2.3f, speed = 0.011, alpha = 0.9f),
            Layer(top = 0.25f, height = 0.8f, stretch = 2.1f, speed = 0.019, alpha = 0.75f),
            Layer(top = 0.5f, height = 0.58f, stretch = 1.9f, speed = 0.03, alpha = 0.6f),
        )
        val LIGHT_BANDS = listOf(Band(12.0, 0.0, 0.15f, 1f), Band(19.0, 8.0, 0.07f, 0.8f))
        val SERRA_BASES_WIDE = floatArrayOf(0.5f, 0.6f, 0.71f, 0.83f)
        val SERRA_BASES_TALL = floatArrayOf(0.56f, 0.65f, 0.74f, 0.85f)
        val SERRA_HEIGHTS = floatArrayOf(0.2f, 0.17f, 0.14f, 0.12f)
        val PETALS = LiveShapes.petals()

        /** Angle, distance from the middle and radius of the five lobes of a flower. */
        val LOBES = arrayOf(
            floatArrayOf(-126f, 18f, 17f),
            floatArrayOf(-54f, 18f, 17f),
            floatArrayOf(18f, 22f, 22f),
            floatArrayOf(90f, 22f, 22f),
            floatArrayOf(162f, 22f, 22f),
        )
        val DOTS = arrayOf(floatArrayOf(-3f, 10f), floatArrayOf(3f, 12f), floatArrayOf(0f, 16f), floatArrayOf(-5f, 15f), floatArrayOf(5f, 7f))

        fun s(radians: Double): Float = sin(radians).toFloat()

        fun co(radians: Double): Float = cos(radians).toFloat()

        fun alpha(color: Int, amount: Float): Int = ((amount.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0xFFFFFF)

        fun mix(a: Int, b: Int, f: Float): Int {
            fun channel(shift: Int): Int {
                val x = a shr shift and 0xFF
                val y = b shr shift and 0xFF
                return (x + (y - x) * f).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        /** [color] turned round the color wheel by [degrees]. */
        fun hue(color: Int, degrees: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
            return Color.HSVToColor(hsv)
        }
    }
}
