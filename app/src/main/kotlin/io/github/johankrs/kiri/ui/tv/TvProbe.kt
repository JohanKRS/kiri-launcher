package io.github.johankrs.kiri.ui.tv

import android.app.Activity
import android.content.Intent
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MEDIÇÃO, NÃO MESCLAR. Turns parts of the TV's home screen off, or back to how they were before
 * the work on speed, and measures how long the frames take while the focus moves, to find out
 * what a TV's graphics card spends its time on. Started from a computer:
 *
 *     adb shell am start -n io.github.johankrs.kiri.debug/io.github.johankrs.kiri.MainActivity --es kiri.probe sweep
 *
 * and read with `adb logcat -s KiriProbe`. `sweep` measures every arrangement of [SWEEP] in turn;
 * a list such as `new,still` measures only that one.
 */
object TvProbe {
    private const val TAG = "KiriProbe"

    /** The parts turned off or put back as they were; nothing in normal use. */
    var parts: Set<String> by mutableStateOf(emptySet())
        internal set

    fun off(part: String): Boolean = part in parts

    /** Everything as it was before: see-through window, a blur and a cut for every shadow, nothing kept. */
    internal val OLD = setOf("window", "layers", "gpublur", "fullshade")

    private val SWEEP = listOf(
        "old", "new",
        "old,still", "new,still",
        "new,nowall", "new,noshadow", "new,nonames", "new,notextshadow",
        "new,nowidgets", "new,nofaces", "new,notopbar",
        "new,gpublur", "new,nolayers", "new,window",
        "new,still,nowidgets,nonames,notopbar",
        "new", "old",
    )

    private var job: Job? = null
    private val scope = MainScope()

    /** Starts what [intent] asks for, if it asks: see the top of this file. [home] is as if Home were pressed. */
    fun handle(activity: Activity, intent: Intent?, home: () -> Unit) {
        val spec = intent?.getStringExtra("kiri.probe") ?: return
        intent.removeExtra("kiri.probe")
        job?.cancel()
        job = scope.launch {
            val out = StringBuilder()
            fun say(line: String) {
                Log.i(TAG, line)
                out.append(line).append('\n')
            }
            say("start $spec")
            deviceInfo(activity).forEach { say(it) }
            val recorder = FrameRecorder(activity.window)
            val list = when (spec) {
                "sweep" -> SWEEP
                "info" -> emptyList()
                else -> listOf(spec)
            }
            try {
                for (config in list) {
                    parts = expand(config)
                    home()
                    delay(1000)
                    say("applied $config")
                    delay(1800)
                    say("measuring $config")
                    val sideways = recorder.measure(5_000L) { keys(activity, listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT), 5_000L) }
                    say(String.format(Locale.ROOT, "%-40s lados  %s", config, sideways))
                    home()
                    delay(900)
                    val down = recorder.measure(3_000L) { keys(activity, listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP), 3_000L) }
                    say(String.format(Locale.ROOT, "%-40s descer %s", config, down))
                }
            } finally {
                parts = emptySet()
                home()
                recorder.release()
                say("done")
                runCatching { File(activity.getExternalFilesDir(null), "probe.txt").writeText(out.toString()) }
            }
        }
    }

    private fun expand(config: String): Set<String> = config.split(',').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { part ->
        when (part) {
            "old" -> OLD
            "new" -> emptySet()
            else -> setOf(if (part.startsWith("no")) part.removePrefix("no") else part)
        }
    }.toSet()

    private suspend fun keys(activity: Activity, pattern: List<Int>, millis: Long) {
        val end = SystemClock.uptimeMillis() + millis
        var i = 0
        while (SystemClock.uptimeMillis() < end) {
            val code = pattern[i++ % pattern.size]
            val now = SystemClock.uptimeMillis()
            activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            activity.dispatchKeyEvent(KeyEvent(now, now + 40, KeyEvent.ACTION_UP, code, 0))
            delay(350)
        }
    }

    private fun deviceInfo(activity: Activity): List<String> {
        val lines = ArrayList<String>()
        lines += "device ${Build.MANUFACTURER} ${Build.MODEL} hw=${Build.HARDWARE} board=${Build.BOARD} sdk=${Build.VERSION.SDK_INT}" +
            if (Build.VERSION.SDK_INT >= 31) " soc=${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else ""
        val display = activity.display
        if (display != null) {
            val mode = display.mode
            lines += "display mode ${mode.physicalWidth}x${mode.physicalHeight}@${mode.refreshRate} id=${mode.modeId} " +
                "modes=${display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate.roundToInt()}" }}"
        }
        val view = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        lines += "window ${view.width}x${view.height}px density=${metrics.densityDpi} (${metrics.density}) format=${activity.window.attributes.format}"
        lines += gl()
        return lines
    }

    /** What the graphics card is, and whether it can redraw only the part of the screen that changed. */
    private fun gl(): List<String> = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val eglExtensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS).orEmpty()
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE),
            0, configs, 0, 1, count, 0,
        )
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
        val glVersion = GLES20.glGetString(GLES20.GL_VERSION)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        val wanted = listOf("EGL_KHR_partial_update", "EGL_EXT_buffer_age", "EGL_KHR_swap_buffers_with_damage", "EGL_EXT_swap_buffers_with_damage")
        listOf(
            "gpu $renderer | $glVersion",
            "egl " + wanted.joinToString { "${it.removePrefix("EGL_")}=${if (eglExtensions.contains(it)) "sim" else "não"}" },
        )
    }.getOrElse { listOf("gpu ? ${it.message}") }

    /** The times of the frames the window draws, while asked. */
    private class FrameRecorder(private val window: Window) {
        private val thread = HandlerThread("kiri-probe").apply { start() }
        private val rows = ArrayList<LongArray>()

        @Volatile private var recording = false

        private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (!recording) return@OnFrameMetricsAvailableListener
            val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1L
            val row = longArrayOf(
                metrics.getMetric(FrameMetrics.TOTAL_DURATION),
                gpu,
                metrics.getMetric(FrameMetrics.DRAW_DURATION),
                metrics.getMetric(FrameMetrics.SYNC_DURATION),
                metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
                metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
            )
            synchronized(rows) { rows += row }
        }

        init {
            window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        }

        suspend fun measure(millis: Long, drive: suspend () -> Unit): String {
            synchronized(rows) { rows.clear() }
            val start = System.nanoTime()
            recording = true
            drive()
            delay(600)
            recording = false
            delay(300)
            val taken = synchronized(rows) { rows.filter { it[6] >= start && it[6] < start + millis * 1_000_000L } }
            if (taken.isEmpty()) return "sem quadros"
            fun ms(column: Int, q: Double): String {
                val values = taken.map { it[column] }.filter { it >= 0 }.sorted()
                if (values.isEmpty()) return "-"
                return String.format(Locale.ROOT, "%.1f", values[((values.size - 1) * q).roundToInt()] / 1e6)
            }
            val fps = taken.size / (millis / 1000.0)
            val slow = taken.count { it[0] > 17_000_000L }
            return String.format(
                Locale.ROOT,
                "%3d quadros %5.1f q/s lentos %3d | gpu %s/%s | total %s/%s | draw %s sync %s issue %s swap %s",
                taken.size, fps, slow, ms(1, 0.5), ms(1, 0.9), ms(0, 0.5), ms(0, 0.9), ms(2, 0.5), ms(3, 0.5), ms(4, 0.5), ms(5, 0.5),
            )
        }

        fun release() {
            window.removeOnFrameMetricsAvailableListener(listener)
            thread.quitSafely()
        }
    }
}

/** The shadow as PR #24 drew it: blurred by the graphics card at every frame. MEDIÇÃO, NÃO MESCLAR. */
internal class GpuSoftShadow(blur: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(((blur - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
    }

    fun draw(scope: DrawScope, alpha: Float, drop: Float, corner: Float) {
        if (alpha <= 0f) return
        paint.color = android.graphics.Color.argb((alpha.coerceAtMost(1f) * 255f).roundToInt(), 0, 0, 0)
        val width = scope.size.width
        val height = scope.size.height
        scope.drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, drop, width, height + drop, corner, corner, paint) }
    }
}
