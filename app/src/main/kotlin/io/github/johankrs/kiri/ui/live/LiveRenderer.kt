package io.github.johankrs.kiri.ui.live

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderNode
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Choreographer
import android.view.SurfaceHolder
import io.github.johankrs.kiri.core.look.LiveLook
import io.github.johankrs.kiri.core.look.LiveMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How much wider than the screen a live wallpaper is drawn, to follow the home pages. */
internal const val LIVE_TRAVEL = 0.12f

/** Where the live wallpapers of the app are in their motion, so one that comes back carries on. */
object LiveClock {
    @Volatile
    var seconds: Double = 40.0
}

/**
 * Draws a live wallpaper on a surface of its own, on a thread of its own, once a frame while
 * it is seen: the TV's home screen draws its widgets and banners without waiting for it, and
 * it without waiting for them. It stops drawing the moment it is not seen, and a still one
 * draws once.
 *
 * On the TV the surface is made small ([stretched] false: its size is the scene's) and the
 * screen stretches it. A live wallpaper cannot choose the size of its surface: there the scene
 * is drawn small into a layer of its own and that is stretched ([stretched] true), which costs
 * the same.
 */
class LiveRenderer(name: String, private val stretched: Boolean) {
    private val thread = HandlerThread("kiri-live-$name", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
    private val handler = Handler(thread.looper)
    private val painter = LivePainter()
    private val lock = Any()
    private var choreographer: Choreographer? = null
    private val layer = if (stretched) RenderNode("kiri-live").apply { setUseCompositingLayer(true, Paint(Paint.FILTER_BITMAP_FLAG)) } else null

    // Read on the drawing thread, written from anywhere.
    @Volatile private var holder: SurfaceHolder? = null
    @Volatile private var width = 0
    @Volatile private var height = 0
    @Volatile private var look = LiveLook()
    @Volatile private var seen = false
    @Volatile private var offset = 0.5f
    @Volatile private var released = false

    // Only on the drawing thread.
    private var time = LiveClock.seconds
    private var last = 0L
    private var waiting = false

    private val frame = Choreographer.FrameCallback { nanos ->
        waiting = false
        draw(nanos)
    }

    init {
        handler.post { choreographer = Choreographer.getInstance() }
    }

    /** The surface to draw on, now [width] by [height] pixels. */
    fun attach(holder: SurfaceHolder, width: Int, height: Int) {
        synchronized(lock) {
            this.holder = holder
            this.width = width
            this.height = height
        }
        again()
    }

    /** The surface is going away: once this returns, nothing is drawn on it. */
    fun detach() {
        synchronized(lock) { holder = null }
    }

    fun setLook(next: LiveLook) {
        if (next == look) return
        look = next
        again()
    }

    /** Whether the wallpaper is on screen. Out of sight, it draws nothing at all. */
    fun setSeen(next: Boolean) {
        if (next == seen) return
        seen = next
        if (next) again()
    }

    /** How far along the home pages are, from 0 to 1, for the wallpaper to follow them a little. */
    fun setOffset(next: Float) {
        if (next == offset) return
        offset = next
        if (look.motion == LiveMotion.STILL) again()
    }

    /** Stops for good and gives back what it holds. */
    fun release() {
        released = true
        detach()
        handler.post {
            choreographer?.removeFrameCallback(frame)
            painter.release()
            layer?.discardDisplayList()
            LiveClock.seconds = time
            thread.quitSafely()
        }
    }

    private fun again() {
        handler.post {
            if (released || waiting) return@post
            val next = choreographer ?: return@post
            waiting = true
            next.postFrameCallback(frame)
        }
    }

    private fun draw(nanos: Long) {
        val now = look
        val moving = now.motion != LiveMotion.STILL
        if (last != 0L && moving && seen) time += min(MAX_STEP, (nanos - last) / 1e9) * now.motion.speed
        last = nanos
        LiveClock.seconds = time
        synchronized(lock) {
            val target = holder ?: return
            if (!seen || released || width <= 0 || height <= 0) return
            val canvas = runCatching { target.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { target.lockCanvas() }.getOrNull()
                ?: return
            try {
                paint(canvas, now)
            } finally {
                runCatching { target.unlockCanvasAndPost(canvas) }
            }
        }
        if (moving && seen && !released) again() else last = 0L
    }

    private fun paint(canvas: Canvas, now: LiveLook) {
        val node = layer
        if (node == null || !canvas.isHardwareAccelerated) {
            if (!stretched) {
                painter.draw(canvas, width, height, time, now.scene, now.color)
                return
            }
            // Without a layer to stretch, drawn small and stretched all the same.
            val (w, h, along) = scene(now)
            canvas.save()
            canvas.scale(width / sceneWidth(w), height / h.toFloat())
            canvas.translate(-along, 0f)
            painter.draw(canvas, w, h, time, now.scene, now.color)
            canvas.restore()
            return
        }
        val (w, h, along) = scene(now)
        node.setPosition(0, 0, w, h)
        val recording = node.beginRecording(w, h)
        try {
            painter.draw(recording, w, h, time, now.scene, now.color)
        } finally {
            node.endRecording()
        }
        canvas.save()
        canvas.scale(width / sceneWidth(w), height / h.toFloat())
        canvas.translate(-along, 0f)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    /**
     * The scene's size, a little wider than the screen so it can follow the pages, and how far
     * across it the screen is now.
     */
    private fun scene(now: LiveLook): Triple<Int, Int, Float> {
        val h = max(1, (height * now.scene.share).roundToInt())
        val shown = max(1, (width * now.scene.share).roundToInt())
        val w = (shown * (1f + TRAVEL)).roundToInt()
        return Triple(w, h, offset.coerceIn(0f, 1f) * (w - shown))
    }

    /** How much of the scene's width shows at once. */
    private fun sceneWidth(w: Int): Float = w / (1f + TRAVEL)

    private companion object {
        /** A frame missed for longer than this does not make the scene jump ahead. */
        const val MAX_STEP = 0.1
        const val TRAVEL = LIVE_TRAVEL
    }
}
