package io.github.johankrs.kiri

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Kiri's accessibility service. It does only what an app cannot do by itself and what the person
 * asked for: today, locking the screen from a gesture on the home screen ([lockScreen]). It
 * reads nothing on the screen and is sent no events. It stays off until the person turns it on
 * in Android's accessibility settings ([openSettings]).
 *
 * There is one service for all of Kiri: whatever else comes to need it, such as the buttons of
 * a TV remote, is added here rather than in a second one, so the person turns on only one.
 */
class KiriAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        running = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (running === this) running = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var running: KiriAccessibilityService? = null

        /** Locks the screen, as the power button does. False when the service is not on. */
        fun lockScreen(): Boolean = running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /** True when the person turned the service on, even before Android has started it. */
        fun isOn(context: Context): Boolean {
            if (running != null) return true
            val me = ComponentName(context, KiriAccessibilityService::class.java)
            val enabled = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull() ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Opens Android's accessibility settings, on Kiri's own page where Android lets an app
         * go there, and otherwise on the list with Kiri picked out where the device does that.
         */
        fun openSettings(context: Context) {
            val me = ComponentName(context, KiriAccessibilityService::class.java).flattenToString()
            val details = Intent(DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, me)
            val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(FRAGMENT_ARGS_KEY, me)
                .putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, me) })
            for (intent in listOf(details, list)) {
                val opened = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                if (opened.isSuccess) return
            }
        }

        private const val DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
        private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
