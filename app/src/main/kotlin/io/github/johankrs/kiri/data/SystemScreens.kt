package io.github.johankrs.kiri.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Opens the first of [intents] that leads to a real screen of the system, and tells whether one
 * did. A TV with Google TV answers some screens it does not have with a stand-in that only shows
 * "No app can perform this action" (its package of framework stubs): those count as missing.
 */
fun Context.openFirstReal(intents: List<Intent>): Boolean {
    for (intent in intents) {
        if (isStandIn(intent)) continue
        val opened = runCatching { startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (opened.isSuccess) return true
    }
    return false
}

/** Whether [intent] leads to a real screen: something answers it, and not a stand-in. */
fun Context.opensReal(intent: Intent): Boolean {
    val target = runCatching {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    }.getOrNull() ?: return false
    return !target.packageName.contains(STAND_IN)
}

private fun Context.isStandIn(intent: Intent): Boolean = runCatching {
    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.contains(STAND_IN)
}.getOrNull() == true

/** What the package of stand-ins is called on Google TV (com.google.android.tv.frameworkpackagestubs). */
private const val STAND_IN = "frameworkpackagestubs"
