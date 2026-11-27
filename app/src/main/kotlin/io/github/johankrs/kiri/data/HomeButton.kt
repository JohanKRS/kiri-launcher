package io.github.johankrs.kiri.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import io.github.johankrs.kiri.core.tv.HomeActivity
import io.github.johankrs.kiri.core.tv.HomeRoute
import io.github.johankrs.kiri.core.tv.OtherHome
import io.github.johankrs.kiri.core.tv.TvHomeButton

/**
 * What the Home button opens, and the ways Kiri has to become it. Android's own way (the home
 * role, or its settings of default apps) works where every home screen is equal; a home screen
 * that came with the device and has a higher priority, such as Google TV's, wins over any
 * choice, and then Kiri can only come in its place (see `KiriAccessibilityService`).
 */
object HomeButton {
    /** Where the Home button leads now and how Kiri can be there instead. */
    data class State(
        val route: HomeRoute,
        /** The home screen the Home button opens instead of Kiri; null when it opens Kiri or asks. */
        val other: OtherHome?,
        /** That home screen's name, such as "Google TV". */
        val otherName: String?,
    )

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Every activity Android has for the Home button, with the priority Android gave it. */
    private fun activities(context: Context): List<HomeActivity> = runCatching {
        context.packageManager.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            HomeActivity(
                packageName = activity.packageName,
                className = activity.name,
                targetName = activity.targetActivity,
                priority = info.priority,
            )
        }
    }.getOrDefault(emptyList())

    /** The package of what the Home button opens now; [TvHomeButton.CHOOSER] when Android would ask. */
    private fun opens(context: Context): String? = runCatching {
        context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    fun state(context: Context): State {
        val all = activities(context)
        val opens = opens(context)
        val other = TvHomeButton.otherHome(all, opens, context.packageName)
        return State(
            route = TvHomeButton.route(all, opens, context.packageName),
            other = other,
            otherName = other?.let { label(context, it.packageName) },
        )
    }

    /** The home screen the Home button opens instead of Kiri, to watch for it; null when there is none. */
    fun other(context: Context): OtherHome? =
        TvHomeButton.otherHome(activities(context), opens(context), context.packageName)

    /** The icon of the app [packageName], to show what the Home button opens. */
    fun icon(context: Context, packageName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    private fun label(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Android's own request to make Kiri the home screen: a question it asks itself. Null where
     * it has none, or where Kiri already holds that role (which, with a home screen of higher
     * priority, does not make the Home button open Kiri).
     */
    fun askIntent(context: Context): Intent? = runCatching {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
        roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }.getOrNull()

    /** Android's settings where the home screen is chosen, where the device has them. */
    fun settingsIntent(context: Context): Intent? =
        listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            .firstOrNull { intent -> context.opensReal(intent) }
}
