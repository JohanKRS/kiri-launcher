package io.github.johankrs.kiri.core.look

/** How an app comes on screen when it is opened from the launcher. */
enum class AppOpenStyle(val key: String) {
    /** Grows out of the icon that was tapped. */
    FROM_ICON("icon"),
    SLIDE_UP("slideUp"),
    FADE("fade"),

    /** Whatever the device does by itself. */
    SYSTEM("system"),
}

/** How the launcher's own screen comes back when an app is left. */
enum class HomeReturnStyle(val key: String) {
    ZOOM("zoom"),
    FADE("fade"),
    NONE("none"),
}

/** How the app drawer comes over the home screen, and leaves it. */
enum class DrawerStyle(val key: String) {
    /** A sheet that rises from the bottom of the screen. */
    SLIDE("slide"),

    /** Comes forward from a little further away while the home screen steps back. */
    ZOOM("zoom"),
    FADE("fade"),
}

/** The person's choices for how things move. */
data class MotionSettings(
    val appOpen: AppOpenStyle = AppOpenStyle.FROM_ICON,
    val homeReturn: HomeReturnStyle = HomeReturnStyle.ZOOM,
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
        const val KEY_HOME_RETURN = "motion.homeReturn"
        const val KEY_DRAWER = "motion.drawer"
        const val KEY_SPEED = "motion.speed"

        /** Builds the settings from saved values; anything missing or unknown is the default. */
        fun fromStored(string: (key: String) -> String?, int: (key: String) -> Int?): MotionSettings {
            val defaults = MotionSettings()
            return MotionSettings(
                appOpen = AppOpenStyle.entries.firstOrNull { it.key == string(KEY_APP_OPEN) } ?: defaults.appOpen,
                homeReturn = HomeReturnStyle.entries.firstOrNull { it.key == string(KEY_HOME_RETURN) }
                    ?: defaults.homeReturn,
                drawer = DrawerStyle.entries.firstOrNull { it.key == string(KEY_DRAWER) } ?: defaults.drawer,
                speed = (int(KEY_SPEED) ?: defaults.speed).coerceIn(SPEED),
            )
        }
    }
}
