package io.github.johankrs.kiri.ui.live

import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.johankrs.kiri.core.look.LiveLook
import io.github.johankrs.kiri.core.look.LiveMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A live wallpaper filling the screen behind everything, on a surface of its own (see
 * [LiveRenderer]). It draws only while [running] and the screen is in front; the rest of the
 * time it holds its last picture and costs nothing.
 */
@Composable
fun LiveSurface(look: LiveLook, running: Boolean, modifier: Modifier = Modifier) {
    val renderer = remember { LiveRenderer("tv", stretched = false) }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.setFormat(PixelFormat.OPAQUE)
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            renderer.attach(holder, width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            renderer.detach()
                        }
                    },
                )
                // The scene is drawn small and the screen stretches it: soft scenes lose nothing.
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> (view as SurfaceView).fitTo(view.tag as? LiveLook) }
            }
        },
        update = { view ->
            if (view.tag != look) {
                view.tag = look
                view.fitTo(look)
            }
            renderer.setLook(look)
            renderer.setSeen(running && resumed)
        },
        modifier = modifier.fillMaxSize(),
    )
}

/** Makes the surface as small as [look]'s scene is drawn. */
private fun SurfaceView.fitTo(look: LiveLook?) {
    if (look == null || width <= 0 || height <= 0) return
    holder.setFixedSize(
        max(1, (width * look.scene.share).roundToInt()),
        max(1, (height * look.scene.share).roundToInt()),
    )
}

/**
 * A live wallpaper drawn in place, for the settings' previews: small, inside rounded corners,
 * under whatever is laid over it. Drawn as small as on the device and stretched, like there.
 */
@Composable
fun LiveCanvas(look: LiveLook, modifier: Modifier = Modifier) {
    val painter = remember { LivePainter() }
    DisposableEffect(painter) { onDispose { painter.release() } }
    var time by remember { mutableDoubleStateOf(LiveClock.seconds) }
    LaunchedEffect(look.motion) {
        if (look.motion == LiveMotion.STILL) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (last != 0L) time += min(0.1, (nanos - last) / 1e9) * look.motion.speed
                last = nanos
            }
        }
    }
    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                val now = time
                val w = max(1, (size.width * look.scene.share).roundToInt())
                val h = max(1, (size.height * look.scene.share).roundToInt())
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(size.width / w, size.height / h)
                    painter.draw(native, w, h, now, look.scene, look.color)
                    native.restore()
                }
            },
    )
}
