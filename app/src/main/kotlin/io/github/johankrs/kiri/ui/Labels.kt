package io.github.johankrs.kiri.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.gesture.Gesture
import io.github.johankrs.kiri.core.gesture.GestureMap
import io.github.johankrs.kiri.core.gesture.LauncherAction

/** The names people see for gestures and actions, kept together so every screen agrees. */
@Composable
fun gestureLabel(gesture: Gesture): String = stringResource(
    when (gesture) {
        Gesture.SWIPE_UP -> R.string.gesture_swipe_up
        Gesture.SWIPE_DOWN -> R.string.gesture_swipe_down
        Gesture.DOUBLE_TAP -> R.string.gesture_double_tap
        Gesture.LONG_PRESS -> R.string.gesture_long_press
    },
)

@Composable
fun actionLabel(action: LauncherAction, appsByKey: Map<String, AppEntry> = emptyMap()): String = when (action) {
    LauncherAction.None -> stringResource(R.string.action_none)
    LauncherAction.OpenDrawer -> stringResource(R.string.action_open_drawer)
    LauncherAction.OpenSettings -> stringResource(R.string.action_open_settings)
    LauncherAction.OpenNotifications -> stringResource(R.string.action_open_notifications)
    LauncherAction.LockScreen -> stringResource(R.string.action_lock_screen)
    LauncherAction.OpenWidgets -> stringResource(R.string.action_open_widgets)
    LauncherAction.OpenHomeMenu -> stringResource(R.string.action_open_home_menu)
    LauncherAction.OpenPages -> stringResource(R.string.action_open_pages)
    is LauncherAction.LaunchApp ->
        stringResource(R.string.action_launch_app, appsByKey[action.appKey]?.label ?: action.appKey)
}

/**
 * What an empty home screen says: each gesture that does something, then how to bring apps in.
 * It follows the chosen gestures, so it never teaches a gesture that was turned off.
 */
@Composable
fun homeHint(gestures: GestureMap, appsByKey: Map<String, AppEntry>): String {
    val lines = mutableListOf<String>()
    for (gesture in Gesture.entries) {
        val action = gestures.actionFor(gesture)
        if (action != LauncherAction.None) {
            lines += stringResource(
                R.string.home_hint_gesture,
                gestureLabel(gesture),
                actionLabel(action, appsByKey).replaceFirstChar { it.lowercase() },
            )
        }
    }
    lines += stringResource(R.string.home_hint_add)
    return lines.joinToString("\n")
}
