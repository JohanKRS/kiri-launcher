package io.github.johankrs.kiri.data

import android.annotation.SuppressLint
import android.content.Context

/**
 * Pulls down the notification shade. Android has no public call for this, so it goes through
 * the status bar service the way launchers usually do; it needs the EXPAND_STATUS_BAR
 * permission. Returns false when the system refuses, and then nothing happens.
 */
@SuppressLint("WrongConstant", "PrivateApi")
fun Context.expandNotifications(): Boolean {
    val service = getSystemService("statusbar") ?: return false
    return runCatching {
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(service)
    }.isSuccess
}
