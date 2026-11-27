package io.github.johankrs.kiri.data

import android.content.Context
import android.content.pm.PackageManager

// What this build asks Android for, read once: it cannot change while the app runs.
@Volatile
private var requested: Set<String>? = null

/**
 * Whether this build of Kiri asks Android for [permission] at all. Google Play's build leaves
 * out what Play wants declared and justified (see app/src/play/AndroidManifest.xml), so a screen
 * that would offer to turn such a permission on asks this first.
 */
fun Context.declares(permission: String): Boolean {
    val known = requested ?: runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
    }.getOrNull()?.also { requested = it }
    // Should Android not say, the permission is offered as before: asking costs nothing.
    return known?.contains(permission) ?: true
}
