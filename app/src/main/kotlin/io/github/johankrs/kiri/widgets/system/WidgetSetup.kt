package io.github.johankrs.kiri.widgets.system

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Adds a widget of another app, which can take up to three steps: Android gives the new copy a
 * number, the person may have to allow Kiri to show widgets, and the widget may have a setup
 * screen of its own to go through. Each of the last two leaves Kiri for a moment.
 */
@Stable
class WidgetSetup internal constructor(
    /** Starts adding a new copy of a widget. */
    val add: (AppWidgetProviderInfo) -> Unit,
    /** Opens the setup screen of a copy that is already placed. */
    val adjust: (appWidgetId: Int) -> Unit,
    /**
     * Adds a new copy of a widget to take the place of the copy [old], which Android forgot (as
     * after a restore on a new device). The same steps as [add].
     */
    val placeAgain: (old: Int, info: AppWidgetProviderInfo) -> Unit,
)

/**
 * [onReady] is told once a new copy is ready to be given a place: its number, what it is, and
 * the copy it takes the place of when it was added by `placeAgain` (null otherwise).
 * A copy that was given up on the way, at any step, is handed back to Android. So are, once
 * when the launcher starts, the copies that are neither among the [placed] ones nor being
 * added: what an adding that was cut short left behind. [placed] answers null when it cannot
 * tell, and then nothing is handed back.
 */
@Composable
fun rememberWidgetSetup(
    system: SystemWidgets,
    placed: suspend () -> Set<Int>?,
    onReady: (appWidgetId: Int, info: AppWidgetProviderInfo, replacing: Int?) -> Unit,
): WidgetSetup {
    val activity = LocalActivity.current
    val ready by rememberUpdatedState(onReady)
    // The copy being added, by its number. Saved, because both steps that leave Kiri may end
    // with the launcher having been rebuilt in the meantime.
    val adding = rememberSaveable { mutableIntStateOf(NONE) }
    // The copy Android forgot that the one being added takes the place of, if any.
    val replacing = rememberSaveable { mutableIntStateOf(NONE) }
    // When that one was started, by the clock that counts since the device was turned on.
    val addingSince = remember { longArrayOf(0L) }

    val steps = remember(system, activity) {
        object {
            fun giveUp(id: Int) {
                adding.intValue = NONE
                replacing.intValue = NONE
                system.delete(id)
            }

            fun finish(id: Int) {
                adding.intValue = NONE
                val old = replacing.intValue.takeIf { it != NONE }
                replacing.intValue = NONE
                val info = system.info(id)
                if (info != null) ready(id, info, old) else system.delete(id)
            }

            fun afterBind(id: Int) {
                val info = system.info(id)
                when {
                    info == null -> giveUp(id)
                    !system.needsSetup(info) -> finish(id)
                    activity == null || !system.setUp(activity, id) -> giveUp(id)
                    // Otherwise the setup screen is open: how it ends arrives further down.
                }
            }
        }
    }

    val askToBind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = adding.intValue
        if (id != NONE) {
            if (result.resultCode == Activity.RESULT_OK) steps.afterBind(id) else steps.giveUp(id)
        }
    }

    val result = system.setupResult
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        system.setupResult = null
        // A result with nothing being added is from adjusting a placed widget: nothing to do.
        val id = adding.intValue
        if (id != NONE) {
            if (result.done) steps.finish(id) else steps.giveUp(id)
        }
    }

    LaunchedEffect(system) {
        if (!system.available) return@LaunchedEffect
        val inUse = placed() ?: return@LaunchedEffect
        system.tidyOnce(inUse + adding.intValue)
    }

    return remember(system, activity, steps) {
        fun start(info: AppWidgetProviderInfo, old: Int) {
            // One at a time: a second tap right after the first changes nothing. A tap long
            // after it means the first never came back from where it went, so it is given up.
            val before = adding.intValue
            if (before != NONE) {
                if (SystemClock.uptimeMillis() - addingSince[0] < DOUBLE_TAP_MS) return
                steps.giveUp(before)
            }
            val id = system.allocate() ?: return
            adding.intValue = id
            replacing.intValue = old
            addingSince[0] = SystemClock.uptimeMillis()
            if (system.bind(id, info)) {
                steps.afterBind(id)
            } else if (runCatching { askToBind.launch(system.bindRequest(id, info)) }.isFailure) {
                steps.giveUp(id)
            }
        }
        WidgetSetup(
            add = { info -> start(info, NONE) },
            adjust = { id -> if (activity != null) system.setUp(activity, id) },
            placeAgain = { old, info -> start(info, old) },
        )
    }
}

/** No copy is being added. Android never hands out this number. */
private const val NONE = -1

/** Taps on a widget to add that come closer together than this count as one. */
private const val DOUBLE_TAP_MS = 1500L
