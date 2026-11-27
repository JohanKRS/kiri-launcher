package io.github.johankrs.kiri

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Kiri's accessibility service. It does only what an app cannot do by itself and what the person
 * asked for: today, locking the screen from a gesture on the home screen ([lockScreen]). It
 * reads nothing on the screen and is sent no events. It stays off until the person turns it on
 * in Android's accessibility settings ([openSettings]).
 *
 * More than off, it is absent while nothing needs it ([offer]): the manifest leaves it disabled,
 * and Kiri lets Android list it only while a gesture locks the screen. So a device where nobody
 * chose that has no accessibility service from Kiri at all, which apps that refuse to work beside
 * one (some banks do) can see.
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

        /**
         * Whether this build has the service at all. Google Play's build does not (see
         * app/src/play/AndroidManifest.xml), and then no gesture can lock the screen.
         */
        fun exists(context: Context): Boolean = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(
                ComponentName(context, KiriAccessibilityService::class.java),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }.isSuccess

        /** Locks the screen, as the power button does. False when the service is not on. */
        fun lockScreen(): Boolean = running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /**
         * Lets Android list the service ([wanted]) or takes it away again. Taken away, it is not
         * only off but gone from the accessibility settings; offered again, it comes back on by
         * itself when the person had turned it on before.
         */
        fun offer(context: Context, wanted: Boolean) {
            if (!exists(context)) return
            val me = ComponentName(context, KiriAccessibilityService::class.java)
            val state = if (wanted) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                // The manifest's own state, which is disabled.
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            val pm = context.packageManager
            runCatching {
                if (pm.getComponentEnabledSetting(me) != state) {
                    pm.setComponentEnabledSetting(me, state, PackageManager.DONT_KILL_APP)
                }
            }
        }

        /** True when the person turned the service on, even before Android has started it. */
        fun isOn(context: Context): Boolean {
            if (running != null) return true
            val me = ComponentName(context, KiriAccessibilityService::class.java)
            val offered = runCatching { context.packageManager.getComponentEnabledSetting(me) }.getOrNull()
            if (offered != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return false
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
