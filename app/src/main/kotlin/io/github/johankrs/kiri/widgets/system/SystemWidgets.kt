package io.github.johankrs.kiri.widgets.system

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.LruCache
import android.util.SizeF
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The widgets that installed apps offer, and the part of Android that lets Kiri show them.
 *
 * Android draws such a widget itself, in a view it hands over; Kiri gives each one a place and
 * a size. A copy of a widget is known by a number, its "app widget id", which Android hands out
 * and remembers together with what the person set that copy up to show.
 *
 * One instance serves the whole process. Everything but [offers] and [preview] is for the main
 * thread. Where the device has no widgets at all, as on most TVs, [available] is false and
 * everything here quietly does nothing.
 */
@Stable
class SystemWidgets private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val manager: AppWidgetManager? =
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
            runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull()
        } else {
            null
        }
    private val host: Host? = manager?.let { Host(appContext) }

    val available: Boolean get() = host != null

    /** Goes up when an app changes what widgets it offers, or one on show changes what it is. */
    var revision by mutableIntStateOf(0)
        private set

    /** How the last trip to a widget's own setup screen ended. Whoever sent it there clears it. */
    var setupResult by mutableStateOf<SetupResult?>(null)

    /** The views of the widgets on show, kept so that a page coming back has nothing to build. */
    private val views = HashMap<Int, AppWidgetHostView>()

    /**
     * What each widget on show looks like right now, for whoever draws a copy of it: the one
     * carried by a finger, or the one in a preview. A widget only has the one view.
     */
    internal val pictures = HashMap<Int, GraphicsLayer>()

    private val previews = LruCache<String, ImageBitmap>(PREVIEWS_KEPT)

    /** Widgets only change what they show while this is on: while the launcher is in view. */
    fun startListening() {
        // Android may refuse when the widgets hold more than it can hand over at once.
        runCatching { host?.startListening() }
    }

    fun stopListening() {
        runCatching { host?.stopListening() }
    }

    /** What Android knows about the copy [appWidgetId], or null when it or its app is gone. */
    fun info(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager?.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /** Every widget the apps of this device offer for a home screen, work profile included. */
    suspend fun offers(): List<AppWidgetProviderInfo> = withContext(Dispatchers.Default) {
        val manager = manager ?: return@withContext emptyList()
        val users = appContext.getSystemService(UserManager::class.java)?.userProfiles.orEmpty()
        users.flatMap { user ->
            runCatching { manager.getInstalledProvidersForProfile(user) }.getOrNull().orEmpty()
        }.filter { info ->
            info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
        }
    }

    /** A number for a new copy of a widget, or null where there are no widgets. */
    fun allocate(): Int? = runCatching { host?.allocateAppWidgetId() }.getOrNull()

    /**
     * Ties the number [appWidgetId] to the widget [info]. False when Android wants the person
     * to allow Kiri to show widgets first: [bindRequest] asks for that.
     */
    fun bind(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean = runCatching {
        manager?.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null) == true
    }.getOrDefault(false)

    /** The screen where Android asks the person whether Kiri may show this widget. */
    fun bindRequest(appWidgetId: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    /** Whether the widget has to be set up on a screen of its own before it can be shown. */
    fun needsSetup(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && !(info.canBeSetUpLater && info.hasOptionalSetup)

    /** Whether the widget has a setup screen that can be opened again once it is placed. */
    fun canSetUpAgain(info: AppWidgetProviderInfo): Boolean = info.configure != null && info.canBeSetUpLater

    /**
     * Opens the widget's own setup screen over [activity]. How it ends arrives in [setupResult].
     * False when the screen could not be opened.
     */
    fun setUp(activity: Activity, appWidgetId: Int): Boolean = runCatching {
        val host = host ?: return false
        // The screen belongs to another app: since Android 14 opening it has to be allowed here.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        setupResult = null
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQUEST_SETUP, options)
    }.isSuccess

    /** Called by the activity with every result it gets. True when the result was for a widget. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_SETUP) return false
        setupResult = SetupResult(done = resultCode == Activity.RESULT_OK)
        return true
    }

    /** Gives the number of a copy back to Android, which then forgets that copy. */
    fun delete(appWidgetId: Int) {
        views.remove(appWidgetId)
        pictures.remove(appWidgetId)
        runCatching { host?.deleteAppWidgetId(appWidgetId) }
    }

    private var tidied = false

    /**
     * Gives back every number that is not one of [inUse]: copies left behind when adding a
     * widget was cut short, or when a layout was replaced. Done once, the first time it is
     * asked: later, a copy may be on its way to being placed.
     */
    fun tidyOnce(inUse: Set<Int>) {
        if (tidied) return
        tidied = true
        val host = host ?: return
        val known = runCatching { host.appWidgetIds }.getOrNull() ?: return
        for (id in known) if (id !in inUse) delete(id)
    }

    /**
     * The view that shows the copy [appWidgetId]. It is made for [context], which must be the
     * activity, and then kept: the next call returns the same one, which may still sit where it
     * was last shown. Null when the copy or its app is gone.
     */
    fun viewFor(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val host = host ?: return null
        val kept = views[appWidgetId]
        if (kept != null && kept.context === context) return kept
        val info = info(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }.getOrNull()?.also {
            // The grid already keeps items apart: no second margin inside the cell.
            it.setPadding(0, 0, 0, 0)
            views[appWidgetId] = it
        }
    }

    /** Lets go of the views made for [context], when that activity is over. */
    fun forget(context: Context) {
        views.values.removeAll { it.context === context }
    }

    /**
     * The picture an app made to show what its widget looks like, no larger than asked. Null
     * when it made none.
     */
    suspend fun preview(info: AppWidgetProviderInfo, maxWidthPx: Int, maxHeightPx: Int): ImageBitmap? {
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return null
        val key = "${info.provider.flattenToShortString()}#${info.profile.hashCode()}@${maxWidthPx}x$maxHeightPx"
        previews.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = info.loadPreviewImage(appContext, appContext.resources.displayMetrics.densityDpi)
                    ?: return@runCatching null
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width <= 0 || height <= 0) return@runCatching null
                val scale = minOf(maxWidthPx / width.toFloat(), maxHeightPx / height.toFloat(), 1f)
                drawable.toBitmap(
                    width = (width * scale).roundToInt().coerceAtLeast(1),
                    height = (height * scale).roundToInt().coerceAtLeast(1),
                ).asImageBitmap()
            }.getOrNull()?.also { previews.put(key, it) }
        }
    }

    /** Lets go of the pictures made for the list of widgets. */
    fun clearPreviews() {
        previews.evictAll()
    }

    /** How a trip to a setup screen ended. A new object each time, so two ends are two events. */
    class SetupResult(val done: Boolean)

    private inner class Host(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView = HostView(context)

        override fun onProvidersChanged() {
            previews.evictAll()
            revision++
        }

        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo?) {
            super.onProviderChanged(appWidgetId, appWidget)
            revision++
        }
    }

    companion object {
        /** Tells Android which of an app's widget hosts this is. Never change it. */
        private const val HOST_ID = 0x4B495249

        /** Low on purpose: the numbers above 65535 belong to the activity's own results. */
        private const val REQUEST_SETUP = 4711

        private const val PREVIEWS_KEPT = 48

        @Volatile
        private var instance: SystemWidgets? = null

        fun of(context: Context): SystemWidgets = instance ?: synchronized(this) {
            instance ?: SystemWidgets(context.applicationContext).also { instance = it }
        }
    }
}

private val AppWidgetProviderInfo.canBeSetUpLater: Boolean
    get() = widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

private val AppWidgetProviderInfo.hasOptionalSetup: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0

/** Tells the widget how much room it has, in dp, so it can arrange itself for it. */
internal fun AppWidgetHostView.tellSize(widthDp: Int, heightDp: Int) {
    if (widthDp <= 0 || heightDp <= 0) return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        }
    }
}
