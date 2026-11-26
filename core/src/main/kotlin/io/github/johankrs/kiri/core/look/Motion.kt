package io.github.johankrs.kiri.core.look

/** How an app comes on screen when it is opened from the launcher. Android draws it. */
enum class AppOpenStyle(val key: String) {
    /** The whole app grows out of the icon that was tapped, to the full screen. */
    GROW("grow"),

    /** A frame opens out of the icon that was tapped and shows the app at its own size. */
    FROM_ICON("icon"),
    SLIDE_UP("slideUp"),
    FADE("fade"),

    /** Whatever the device does by itself. */
    SYSTEM("system"),
}

/**
 * How the launcher's own screen goes out of sight when the drawer or an app comes over it, and
 * how it comes back. The same for the drawer and for apps.
 */
enum class HomeStyle(val key: String) {
    /** It steps back a little and goes; it comes back growing into place. */
    RECEDE("zoom"),

    /**
     * It grows larger from a point and goes, as if passed through: from the icon of the app
     * that opens, or from the middle for the drawer. Coming back, everything flies into place.
     */
    FLY_BACK("flyBack"),

    /** Like [FLY_BACK], but every item flies on its own: the nearest land first. */
    WAVE("wave"),

    /** It goes out of focus, as the far side of a photograph, and comes back sharp. */
    FOCUS("focus"),
    FADE("fade"),

    /** It stays still. Under the drawer it still fades, not to be seen through the glass. */
    NONE("none"),
}

/** How the app drawer comes over the home screen, and leaves it. */
enum class DrawerStyle(val key: String) {
    /** A sheet that rises from the bottom of the screen. */
    SLIDE("slide"),

    /** Comes forward from a little further away. */
    ZOOM("zoom"),

    /** The glass opens in a circle from where the finger started, showing each app it reaches. */
    REVEAL("reveal"),

    /** The glass clouds over where it is, and the apps rise into place row by row. */
    CASCADE("cascade"),
    FADE("fade"),
}

/** The person's choices for how things move. */
data class MotionSettings(
    val appOpen: AppOpenStyle = AppOpenStyle.FROM_ICON,
    val home: HomeStyle = HomeStyle.RECEDE,
    val drawer: DrawerStyle = DrawerStyle.SLIDE,
    /** From [SPEED] : 1 is calm, 3 is the standard, 5 is brisk. */
    val speed: Int = DEFAULT_SPEED,
) {
    /**
     * What to multiply the stiffness of a spring by, so that [speed] changes how fast things
     * settle without changing how they feel. Durations are divided by [durationScale] instead.
     */
    val stiffnessScale: Float
        get() = when (speed.coerceIn(SPEED)) {
            1 -> 0.4f
            2 -> 0.65f
            3 -> 1f
            4 -> 1.6f
            else -> 2.5f
        }

    /** A spring that is n times stiffer settles in roughly 1/sqrt(n) of the time. */
    val durationScale: Float get() = 1f / kotlin.math.sqrt(stiffnessScale)

    companion object {
        val SPEED = 1..5
        const val DEFAULT_SPEED = 3

        const val KEY_APP_OPEN = "motion.appOpen"
        /** Named for what it first chose: how the home screen came back after an app. */
        const val KEY_HOME = "motion.homeReturn"
        const val KEY_DRAWER = "motion.drawer"
        const val KEY_SPEED = "motion.speed"

        /** Builds the settings from saved values; anything missing or unknown is the default. */
        fun fromStored(string: (key: String) -> String?, int: (key: String) -> Int?): MotionSettings {
            val defaults = MotionSettings()
            return MotionSettings(
                appOpen = AppOpenStyle.entries.firstOrNull { it.key == string(KEY_APP_OPEN) } ?: defaults.appOpen,
                home = HomeStyle.entries.firstOrNull { it.key == string(KEY_HOME) } ?: defaults.home,
                drawer = DrawerStyle.entries.firstOrNull { it.key == string(KEY_DRAWER) } ?: defaults.drawer,
                speed = (int(KEY_SPEED) ?: defaults.speed).coerceIn(SPEED),
            )
        }
    }
}
