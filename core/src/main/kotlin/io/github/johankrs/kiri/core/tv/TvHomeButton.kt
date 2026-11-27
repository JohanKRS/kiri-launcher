package io.github.johankrs.kiri.core.tv

/**
 * An activity Android has for the Home button: the app's [packageName] and the [className] it
 * names, with [targetName] the class that really opens when that name is an alias. [priority] is
 * what its app gave it; Android keeps a priority above 0 only for apps that came with the device.
 */
data class HomeActivity(
    val packageName: String,
    val className: String,
    val targetName: String? = null,
    val priority: Int = 0,
)

/** How Kiri can become what the Home button of the remote opens. */
enum class HomeRoute {
    /** It already is. */
    KIRI,

    /** Android lets the person choose the home screen: asking it is enough. */
    ASK_ANDROID,

    /**
     * Another home screen outranks every choice: when apps differ in priority, Android opens the
     * highest before it even looks at what the person chose (Google TV is such a home screen).
     * Kiri can only notice it coming up and come in its place.
     */
    TAKE_OVER,
}

/**
 * The home screen the Home button opens instead of Kiri: its app and the classes of its home
 * activities. Only those count, so the other screens of that app (Google TV's panel of settings,
 * say) are left alone.
 */
data class OtherHome(val packageName: String, val classNames: Set<String>) {
    /** Whether a window of the app [packageName], of the class [className], is this home screen. */
    fun isShowing(packageName: String?, className: String?): Boolean =
        packageName == this.packageName && className != null && className in classNames
}

/** What the Home button opens, and what Kiri can do about it. */
object TvHomeButton {
    /** The package Android names when it would ask which home screen to open. */
    const val CHOOSER = "android"

    /**
     * How Kiri ([own]) can become the home screen, from [all] the activities Android has for the
     * Home button and the package of the one it [opens] now (null or [CHOOSER] when it would ask).
     */
    fun route(all: List<HomeActivity>, opens: String?, own: String): HomeRoute {
        if (opens == own) return HomeRoute.KIRI
        val mine = all.filter { it.packageName == own }.maxOfOrNull { it.priority } ?: 0
        val outranked = all.any { it.packageName != own && it.priority > mine }
        return if (outranked) HomeRoute.TAKE_OVER else HomeRoute.ASK_ANDROID
    }

    /** The home screen that the Home button [opens] instead of Kiri ([own]); null when it opens Kiri or asks. */
    fun otherHome(all: List<HomeActivity>, opens: String?, own: String): OtherHome? {
        if (opens == null || opens == own || opens == CHOOSER) return null
        val classes = all.filter { it.packageName == opens }
            .flatMapTo(LinkedHashSet()) { listOfNotNull(it.className, it.targetName) }
        return if (classes.isEmpty()) null else OtherHome(opens, classes)
    }

    /**
     * Whether Kiri comes in place of the other home screen at [now], having come last at [last]
     * (both in milliseconds of the same clock): not twice within [GAP_MS], so a home screen that
     * shows its window twice on the way up opens Kiri only once.
     */
    fun comesBack(now: Long, last: Long?): Boolean = last == null || now - last >= GAP_MS || now < last

    /** The least time between two returns to Kiri. */
    const val GAP_MS = 600L
}
