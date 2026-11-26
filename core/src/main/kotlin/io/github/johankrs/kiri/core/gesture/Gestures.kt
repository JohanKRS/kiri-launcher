package io.github.johankrs.kiri.core.gesture

/** Something a person can do on an empty place of the home screen. */
enum class Gesture(val key: String) {
    SWIPE_UP("swipeUp"),
    SWIPE_DOWN("swipeDown"),
    DOUBLE_TAP("doubleTap"),
    LONG_PRESS("longPress"),
}

/**
 * Something the launcher can do in answer to a gesture. A new kind of action is added here,
 * gets an [id] to be saved under, and is carried out in the app.
 */
sealed interface LauncherAction {
    val id: String

    data object None : LauncherAction {
        override val id = "none"
    }

    data object OpenDrawer : LauncherAction {
        override val id = "drawer"
    }

    data object OpenSettings : LauncherAction {
        override val id = "settings"
    }

    data object OpenNotifications : LauncherAction {
        override val id = "notifications"
    }

    /** Locks the screen and turns it off, as the power button does. */
    data object LockScreen : LauncherAction {
        override val id = "lock"
    }

    /** Opens the screen where widgets are chosen and looked after. */
    data object OpenWidgets : LauncherAction {
        override val id = "widgets"
    }

    /** Opens the small menu of the home screen: widgets, pages, wallpaper and settings. */
    data object OpenHomeMenu : LauncherAction {
        override val id = "homeMenu"
    }

    /** Shows every page of the home screen small, to add, take away and put them in order. */
    data object OpenPages : LauncherAction {
        override val id = "pages"
    }

    /** Opens one app, identified by `AppEntry.key`. */
    data class LaunchApp(val appKey: String) : LauncherAction {
        override val id get() = APP_PREFIX + appKey
    }

    companion object {
        private const val APP_PREFIX = "app:"

        /** The actions that need nothing else to be chosen, in the order they are offered. */
        val simple: List<LauncherAction> =
            listOf(OpenDrawer, OpenNotifications, LockScreen, OpenHomeMenu, OpenPages, OpenWidgets, OpenSettings, None)

        /** Null when [id] is absent or names an action this version does not know. */
        fun fromId(id: String?): LauncherAction? = when {
            id == null -> null
            id.startsWith(APP_PREFIX) -> id.removePrefix(APP_PREFIX).takeIf { it.isNotBlank() }?.let(::LaunchApp)
            else -> simple.firstOrNull { it.id == id }
        }
    }
}

/** Which action each gesture triggers. Gestures nobody changed keep their default. */
data class GestureMap(private val chosen: Map<Gesture, LauncherAction> = emptyMap()) {

    /** The action picked for [gesture], as shown in the settings. */
    fun chosenFor(gesture: Gesture): LauncherAction = chosen[gesture] ?: default(gesture)

    /** True when at least one gesture opens the settings, or the menu that leads to them. */
    val settingsReachable: Boolean
        get() = Gesture.entries.any {
            val action = chosenFor(it)
            action == LauncherAction.OpenSettings || action == LauncherAction.OpenHomeMenu
        }

    /**
     * True when a gesture locks the screen, the one thing Kiri needs its accessibility service
     * for: without such a gesture the service is not offered to Android at all.
     */
    val locksScreen: Boolean
        get() = Gesture.entries.any { chosenFor(it) == LauncherAction.LockScreen }

    /**
     * What really happens on [gesture]. It is [chosenFor], with one safety net: when no gesture
     * opens the settings, the long press does, so the way back in can never be lost.
     */
    fun actionFor(gesture: Gesture): LauncherAction =
        if (gesture == Gesture.LONG_PRESS && !settingsReachable) LauncherAction.OpenSettings else chosenFor(gesture)

    fun with(gesture: Gesture, action: LauncherAction): GestureMap = copy(chosen = chosen + (gesture to action))

    companion object {
        fun default(gesture: Gesture): LauncherAction = when (gesture) {
            Gesture.SWIPE_UP -> LauncherAction.OpenDrawer
            Gesture.SWIPE_DOWN -> LauncherAction.OpenNotifications
            Gesture.DOUBLE_TAP -> LauncherAction.None
            Gesture.LONG_PRESS -> LauncherAction.OpenHomeMenu
        }

        /** Key under which the action of [gesture] is stored. */
        fun storageKey(gesture: Gesture): String = "gesture.${gesture.key}"

        /** Rebuilds the map from stored ids. Anything missing or unknown keeps its default. */
        fun fromStored(read: (key: String) -> String?): GestureMap = GestureMap(
            Gesture.entries.mapNotNull { gesture ->
                LauncherAction.fromId(read(storageKey(gesture)))?.let { gesture to it }
            }.toMap(),
        )
    }
}
