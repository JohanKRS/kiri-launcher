package io.github.johankrs.kiri.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.johankrs.kiri.R

/**
 * The colors Kiri's own icon comes in ("Fios de névoa": threads of mist that draw the flower of
 * the paulownia). Each one is an `activity-alias` of the manifest that opens MainActivity, with
 * its own icon and TV banner.
 */
enum class AppIconColor(
    val alias: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    /** What a TV shows in its list of apps instead of the icon. */
    @DrawableRes val banner: Int,
) {
    LILAC("IconLilac", R.mipmap.ic_launcher_lilac, R.string.app_icon_lilac, R.drawable.tv_banner_lilac),
    MIST("IconMist", R.mipmap.ic_launcher_mist, R.string.app_icon_mist, R.drawable.tv_banner_mist),
    SMOKE("IconSmoke", R.mipmap.ic_launcher_smoke, R.string.app_icon_smoke, R.drawable.tv_banner_smoke),
    NIGHT("IconNight", R.mipmap.ic_launcher_night, R.string.app_icon_night, R.drawable.tv_banner_night),
    ROSE("IconRose", R.mipmap.ic_launcher_rose, R.string.app_icon_rose, R.drawable.tv_banner_rose),
    BLUE("IconBlue", R.mipmap.ic_launcher_blue, R.string.app_icon_blue, R.drawable.tv_banner_blue),
    ;

    fun next(): AppIconColor = entries[(ordinal + 1) % entries.size]

    fun previous(): AppIconColor = entries[(ordinal + entries.size - 1) % entries.size]

    companion object {
        /** The one that is on in the manifest, before anything was chosen. */
        val DEFAULT = LILAC
    }
}

/**
 * Which icon Kiri shows in app lists (of Android, of other launchers, of TVs). Exactly one of
 * the entries of [AppIconColor] is on at a time; Android itself keeps which one, across updates.
 *
 * Only these entries are turned on and off. The entry that makes Kiri the home screen is
 * MainActivity itself and is never touched, so choosing a color cannot undo that.
 */
class AppIcons(context: Context) {
    private val packageName = context.packageName
    private val packageManager = context.packageManager

    /** The color shown now. */
    fun current(): AppIconColor = AppIconColor.entries.firstOrNull { isOn(it) } ?: AppIconColor.DEFAULT

    /** Shows [color] from now on, and hides the others. Kiri keeps running. */
    fun choose(color: AppIconColor) {
        if (color == current()) return
        val states = AppIconColor.entries.map { entry ->
            entry to if (entry == color) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // All at once, so no list ever shows Kiri twice or not at all.
                packageManager.setComponentEnabledSettings(
                    states.map { (entry, state) ->
                        PackageManager.ComponentEnabledSetting(component(entry), state, PackageManager.DONT_KILL_APP)
                    },
                )
            } else {
                // The new one first: for a moment Kiri may be listed twice, but never missing.
                states.sortedByDescending { it.first == color }.forEach { (entry, state) ->
                    packageManager.setComponentEnabledSetting(component(entry), state, PackageManager.DONT_KILL_APP)
                }
            }
        }
    }

    private fun isOn(color: AppIconColor): Boolean =
        when (runCatching { packageManager.getComponentEnabledSetting(component(color)) }.getOrNull()) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> color == AppIconColor.DEFAULT
            else -> false
        }

    /**
     * The entry of [color]. Class names stay in Kiri's namespace even where the package name
     * differs (the debug builds end in `.debug`).
     */
    private fun component(color: AppIconColor) = ComponentName(packageName, "$NAMESPACE.${color.alias}")

    private companion object {
        const val NAMESPACE = "io.github.johankrs.kiri"
    }
}
