package io.github.johankrs.kiri.ui.settings

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.johankrs.kiri.KiriAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What a screen shows of Kiri's accessibility service. */
data class ServiceSeen(
    /**
     * Android runs it, or did a moment ago: Android may stop it and start it again at once
     * (seen on a TCL TV, Android 14, just after it was turned on), and the screen does not
     * flicker for that.
     */
    val running: Boolean,
    /** It has not run for a while now, past the moment Android takes to start it. */
    val stopped: Boolean,
)

/**
 * Follows whether Android runs Kiri's accessibility service while the screen is in sight, asking
 * Android itself (as its accessibility settings count it) and not only what this copy of Kiri saw.
 * [keys] start the wait over, such as Kiri coming back from Android's settings.
 */
@Composable
fun rememberServiceSeen(vararg keys: Any?): ServiceSeen {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // When it was last seen running, across the waits started over.
    val lastRunning = remember { longArrayOf(NEVER) }
    var seen by remember {
        mutableStateOf(ServiceSeen(running = KiriAccessibilityService.isRunning(context), stopped = false))
    }
    LaunchedEffect(lifecycle, *keys) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val since = SystemClock.uptimeMillis()
            while (true) {
                val running = withContext(Dispatchers.Default) { KiriAccessibilityService.isRunning(context) }
                val now = SystemClock.uptimeMillis()
                if (running) lastRunning[0] = now
                val quietFrom = maxOf(since, lastRunning[0])
                seen = ServiceSeen(
                    running = running || (lastRunning[0] != NEVER && now - lastRunning[0] < GRACE_MS),
                    stopped = !running && now - quietFrom >= GRACE_MS,
                )
                delay(LOOK_MS)
            }
        }
    }
    return seen
}

/** How long Android may take to start the service, or to start it again. */
private const val GRACE_MS = 3_000L

/** How often the screen asks Android while in sight. */
private const val LOOK_MS = 500L

private const val NEVER = Long.MIN_VALUE
