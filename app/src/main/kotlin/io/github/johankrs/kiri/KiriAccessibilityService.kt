package io.github.johankrs.kiri

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.johankrs.kiri.core.tv.OtherHome
import io.github.johankrs.kiri.core.tv.TvHomeButton
import io.github.johankrs.kiri.data.HomeButton
import io.github.johankrs.kiri.data.SettingsStore
import io.github.johankrs.kiri.data.openFirstReal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kiri's accessibility service. It does only what an app cannot do by itself and what the person
 * asked for:
 * - locking the screen from a gesture on the home screen ([lockScreen]);
 * - on a TV whose own home screen outranks every other (Google TV's does), coming in its place
 *   when the Home button opens it, if the person asked for that ([watchHome]).
 *
 * It reads nothing on the screen. For the first it is sent no events at all; for the second only
 * one kind, from that home screen's app alone: that one of its windows came up, with the name of
 * its class. It stays off until the person turns it on in Android's accessibility settings
 * ([openSettings]).
 *
 * More than off, it is absent while nothing needs it ([offer]): the manifest leaves it disabled,
 * and Kiri lets Android list it only while a gesture locks the screen or the TV's Home button is
 * to open Kiri. So a device where nobody chose that has no accessibility service from Kiri at all,
 * which apps that refuse to work beside one (some banks do) can see.
 *
 * There is one service for all of Kiri: whatever else comes to need it, such as the other buttons
 * of a TV remote, is added here rather than in a second one, so the person turns on only one.
 */
class KiriAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Whether the person wants Kiri in place of the TV's own home screen. */
    private var homeWanted = false

    /** The TV's own home screen while Kiri watches for it to come up; null while it does not. */
    private var watched: OtherHome? = null

    /** When Kiri last came in place of it, on the clock of [SystemClock.uptimeMillis]. */
    private var lastReturn: Long? = null

    override fun onServiceConnected() {
        Log.i(TAG, "connected")
        running = this
        connected = true
        if (isTelevision()) {
            scope.launch {
                SettingsStore.of(this@KiriAccessibilityService).tvHomeButton.collect { wanted ->
                    homeWanted = wanted
                    watchHome()
                }
            }
        }
    }

    /**
     * Asks Android for the windows of the TV's own home screen coming up, and for nothing else,
     * while the person wants Kiri on the Home button; for no events at all otherwise. What that
     * home screen is can change (an update, another one chosen), so it is looked up again
     * whenever Kiri comes to the front ([refresh]).
     */
    private fun watchHome() {
        val other = if (homeWanted) HomeButton.other(this) else null
        watched = other
        val info = serviceInfo ?: return
        val types = if (other != null) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        val packages = other?.let { arrayOf(it.packageName) }
        if (info.eventTypes == types && info.packageNames.contentEquals(packages)) return
        info.eventTypes = types
        info.packageNames = packages
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val other = watched ?: return
        // Events come only to a service Android runs: whatever was missed of its start, it runs.
        if (running !== this) {
            running = this
            connected = true
        }
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!other.isShowing(event.packageName?.toString(), event.className?.toString())) return
        val now = SystemClock.uptimeMillis()
        if (!TvHomeButton.comesBack(now, lastReturn)) return
        lastReturn = now
        // As the Home button would open Kiri if it could: on its first screen.
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { startActivity(home) }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "unbound")
        forget()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "destroyed")
        forget()
        scope.cancel()
        super.onDestroy()
    }

    private fun forget() {
        if (running === this) {
            running = null
            connected = false
        }
    }

    companion object {
        @Volatile
        private var running: KiriAccessibilityService? = null

        /**
         * Whether this copy of Kiri has the service running now, for screens to follow (with
         * [isRunning], which asks Android). Turned on in Android's settings is not enough:
         * Android 11 can keep a service on that it no longer starts (it counts it as crashed)
         * until it is turned off and on again there.
         */
        var connected by mutableStateOf(false)
            private set

        /**
         * Whether Android runs the service now, as its own accessibility settings count it (a
         * service Android counts as crashed is not running), or Kiri saw it start. Asks Android,
         * so not on the main thread in a loop.
         */
        fun isRunning(context: Context): Boolean {
            if (connected) return true
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val me = ComponentName(context, KiriAccessibilityService::class.java)
            return runCatching {
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                    val info = it.resolveInfo?.serviceInfo
                    info != null && ComponentName(info.packageName, info.name) == me
                }
            }.getOrDefault(false)
        }

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

        /** Looks again at what the Home button opens, as Kiri comes to the front. */
        fun refresh() {
            running?.watchHome()
        }

        /**
         * Lets Android list the service ([wanted]) or takes it away again. Taken away, it is not
         * only off but gone from the accessibility settings, and Android forgets it was on:
         * offered again, the person turns it on once more (seen on a Galaxy S24, Android 17).
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
                    // Turned off as Android's own switch does first: taken away while running,
                    // it would stay on in Android's settings and count as crashed, and Android
                    // 11 would not start it again once offered anew.
                    if (!wanted) running?.disableSelf()
                    pm.setComponentEnabledSetting(me, state, PackageManager.DONT_KILL_APP)
                    Log.i(TAG, if (wanted) "offered" else "taken away")
                    tellAccessibilitySettings(context)
                }
            }
        }

        /**
         * Makes Android's accessibility settings look at Kiri's services again. They look again
         * when a whole app changes, but on many devices not when one of its parts does: a service
         * just turned on stays out of the list (seen on a Hisense TV with Android 11 and a TCL TV
         * with Android 14). Kiri switches between the two ways of being enabled that mean the
         * same (on by default, on by choice), which Android tells as a change of the whole app,
         * without stopping it. Done only as the service is offered or taken away, so never while
         * it runs and is wanted.
         */
        private fun tellAccessibilitySettings(context: Context) {
            val pm = context.packageManager
            val now = pm.getApplicationEnabledSetting(context.packageName)
            val next = if (now == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            pm.setApplicationEnabledSetting(context.packageName, next, PackageManager.DONT_KILL_APP)
        }

        /**
         * Offers the service and opens Android's settings once they list it. Android takes a
         * moment to see a component that was just turned on, and a settings page opened before
         * that knows nothing of Kiri (One UI then shows only the general list).
         */
        suspend fun offerAndOpenSettings(context: Context) {
            offer(context, true)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val me = ComponentName(context, KiriAccessibilityService::class.java)
            fun listed(): Boolean = runCatching {
                manager?.installedAccessibilityServiceList.orEmpty().any {
                    val info = it.resolveInfo?.serviceInfo
                    info != null && ComponentName(info.packageName, info.name) == me
                }
            }.getOrDefault(false)
            // Two seconds at most: past that, the general list is still better than nothing.
            for (attempt in 0 until 20) {
                if (listed()) break
                delay(100)
            }
            openSettings(context)
        }

        /**
         * True when the person turned the service on, even before Android has started it (or
         * when Android does not start it: see [connected]).
         */
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
         * A TV with Google TV has its own page for them, under another name: the usual one only
         * leads to "No app can perform this action" there.
         */
        fun openSettings(context: Context) {
            val me = ComponentName(context, KiriAccessibilityService::class.java).flattenToString()
            val details = Intent(DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, me)
            val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(FRAGMENT_ARGS_KEY, me)
                .putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, me) })
            val tv = Intent(TV_SETTINGS)
            val ways = if (context.isTelevision()) listOf(tv, details, list) else listOf(details, list)
            context.openFirstReal(ways)
        }

        /** For `adb logcat -s KiriA11y`: when Android starts and stops the service, and why Kiri asked. */
        private const val TAG = "KiriA11y"
        private const val DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        private const val TV_SETTINGS = "android.settings.ACCESSIBILITY_TV_OEM_LINK"
        private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
        private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
