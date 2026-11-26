package io.github.johankrs.kiri.data

import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.FormFactor
import io.github.johankrs.kiri.core.look.IconChoice
import io.github.johankrs.kiri.core.look.IconSettings
import io.github.johankrs.kiri.core.sortedByLabel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Reads the installed apps and their icons from the system, always off the main thread.
 *
 * One instance serves the whole process, so rotating the screen or switching themes never drops
 * rendered icons or re-parses icon packs.
 */
@Stable
class AppRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val iconCacheKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 8L)
        .toInt()
        .coerceIn(MIN_ICON_CACHE_KB, MAX_ICON_CACHE_KB)
    private val icons = object : LruCache<String, IconPicture>(iconCacheKb) {
        override fun sizeOf(key: String, value: IconPicture): Int =
            value.bitmap.width * value.bitmap.height * 4 / 1024 + 1
    }

    /**
     * Icons are dressed on a few cores, never all of them: two are left to the UI thread and the
     * RenderThread. A phone with eight cores gets four, a TV with four gets two.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val iconDispatcher = Dispatchers.Default.limitedParallelism(
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(MIN_ICON_THREADS, MAX_ICON_THREADS),
    )
    private val renderScope = CoroutineScope(SupervisorJob() + iconDispatcher)
    private val inFlight = ConcurrentHashMap<String, Deferred<IconPicture?>>()

    /** The banners of apps on a TV, by `AppEntry.key` and width. */
    private val banners = object : LruCache<String, AppBanner>(BANNER_CACHE_KB) {
        override fun sizeOf(key: String, value: AppBanner): Int =
            (value.picture?.let { it.width * it.height * 4 / 1024 } ?: 0) + 1
    }

    /** Reads icon packs, for apps whose icon was changed. */
    val iconPacks = IconPacks(appContext)

    /**
     * How icons look: the look they all share, and what was chosen for single apps, by
     * `AppEntry.key`. Null until the saved settings were read: until then no icon is shown, so
     * none is seen changing right after it appears. It is observable state: what draws an icon
     * is redrawn when something about it changes.
     */
    var iconSettings: IconSettings? by mutableStateOf(null)

    /** The icon picked for each app that has one, or null while that is not known yet. */
    val iconChoices: Map<String, IconChoice>? get() = iconSettings?.choices

    /** Goes up only when an app is installed, updated, removed or (un)suspended. */
    var appChanges by mutableIntStateOf(0)
        private set

    init {
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            val main = Handler(Looper.getMainLooper())
            val notifyChanged = {
                main.post { appChanges++ }
                Unit
            }
            runCatching {
                launcherApps.registerCallback(
                    object : LauncherApps.Callback() {
                        override fun onPackageRemoved(packageName: String, user: UserHandle) = notifyChanged()
                        override fun onPackageAdded(packageName: String, user: UserHandle) = notifyChanged()
                        override fun onPackageChanged(packageName: String, user: UserHandle) {
                            val prefix = "$packageName/"
                            icons.snapshot().keys.forEach { key -> if (key.startsWith(prefix)) icons.remove(key) }
                            banners.snapshot().keys.forEach { key -> if (key.startsWith(prefix)) banners.remove(key) }
                            notifyChanged()
                        }

                        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                            notifyChanged()

                        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                            notifyChanged()

                        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = notifyChanged()
                        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = notifyChanged()
                    },
                    main,
                )
            }
        }
    }

    /** Releases memory when the system asks (important on lower-RAM devices such as Android TVs). */
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            iconPacks.trimMemory(aggressive = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            icons.trimToSize(icons.maxSize() / 2)
            banners.trimToSize(banners.maxSize() / 2)
            IconRenderer.clearCaches()
        }
    }

    suspend fun loadApps(formFactor: FormFactor): List<AppEntry> = withContext(Dispatchers.Default) {
        val entries = if (formFactor == FormFactor.TV) {
            // Prefer an app's TV entry, but keep sideloaded apps that only have a phone entry.
            val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
            val tvPackages = tv.mapTo(HashSet()) { it.packageName }
            tv + query(Intent.CATEGORY_LAUNCHER).filter { it.packageName !in tvPackages }
        } else {
            query(Intent.CATEGORY_LAUNCHER)
        }
        entries.distinctBy { it.key }.sortedByLabel()
    }

    /** The apps that have an entry made for a TV, by `AppEntry.key`. */
    suspend fun loadTvAppKeys(): Set<String> = withContext(Dispatchers.Default) {
        query(Intent.CATEGORY_LEANBACK_LAUNCHER).mapTo(LinkedHashSet()) { it.key }
    }

    /** The banner of [app] if it was already read at this width. Instant, so it can be asked while drawing. */
    fun cachedBanner(app: AppEntry, widthPx: Int): AppBanner? = banners.get("${app.key}@$widthPx")

    /**
     * The banner [app] came with for a TV's home screen, [widthPx] wide and 16:9: the one of its
     * TV entry, or else the one of the whole app. Many apps made for phones have none.
     */
    suspend fun loadBanner(app: AppEntry, widthPx: Int): AppBanner {
        val key = "${app.key}@$widthPx"
        banners.get(key)?.let { return it }
        return withContext(iconDispatcher) {
            val drawable = runCatching { packageManager.getActivityBanner(app.componentName()) }.getOrNull()
                ?: runCatching { packageManager.getApplicationBanner(app.packageName) }.getOrNull()
            val picture = drawable?.let { art ->
                runCatching { art.toBitmap(widthPx, (widthPx * 9 + 8) / 16).asImageBitmap() }.getOrNull()
            }
            AppBanner(picture).also { banners.put(key, it) }
        }
    }

    /** The icon if it was already made at this size. Instant, so it can be asked while drawing. */
    fun cachedIcon(app: AppEntry, sizePx: Int, settings: IconSettings): IconPicture? =
        icons.get(cacheKey(app, sizePx, settings))

    /**
     * The icon of [app] dressed in the look of [settings]: the one picked for it, or its own
     * when none was picked or the picked one is gone (the icon pack was uninstalled, for example).
     * Concurrent requests for the same icon share one render pass.
     */
    suspend fun loadIcon(app: AppEntry, sizePx: Int, settings: IconSettings): IconPicture? {
        if (sizePx <= 0) return null
        val cacheKey = cacheKey(app, sizePx, settings)
        icons.get(cacheKey)?.let { return it }
        // The render is made lazily and only started once it is in the map: a render that
        // finished at once could otherwise try to take itself out of the map before it was put
        // there, and stay in it for good.
        var made: Deferred<IconPicture?>? = null
        val deferred = inFlight.computeIfAbsent(cacheKey) {
            renderScope.async(start = CoroutineStart.LAZY) {
                icons.get(cacheKey) ?: runCatching {
                    val drawable = settings.choices[app.key]?.let { iconPacks.drawable(it) }
                        ?: packageManager.getActivityIcon(app.componentName())
                    IconRenderer.render(
                        drawable = drawable,
                        size = sizePx,
                        look = settings.look,
                        appArtPercent = settings.artPercents[app.key],
                        appGround = settings.grounds[app.key],
                    )
                }.getOrNull()?.also { icons.put(cacheKey, it) }
            }.also { made = it }
        }
        if (deferred === made) {
            deferred.invokeOnCompletion { inFlight.remove(cacheKey, deferred) }
            deferred.start()
        }
        return deferred.await()
    }

    /** The icon [app] came with, untouched: no pack, no shape, no glass. */
    suspend fun loadOwnIcon(app: AppEntry, sizePx: Int): ImageBitmap? = withContext(iconDispatcher) {
        runCatching {
            packageManager.getActivityIcon(app.componentName()).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()
    }

    /** Everything the picture of an icon depends on. Another app's settings are not part of it. */
    private fun cacheKey(app: AppEntry, sizePx: Int, settings: IconSettings): String {
        val look = settings.look
        return "${app.key}@$sizePx@${settings.choices[app.key]?.stored.orEmpty()}" +
            "@${look.shape.key}.${look.glassUse.key}.${look.glass.key}.${look.glassOpacity}.${look.artPercent}.${look.shine.key}" +
            "@${look.relief.key}.${look.edgeLight.key}.${look.edgeWidth}.${look.shadow.key}" +
            "@${settings.artPercents[app.key] ?: ""}@${settings.grounds[app.key]?.key.orEmpty()}"
    }

    fun launchIntent(app: AppEntry): Intent = Intent(Intent.ACTION_MAIN)
        .setComponent(app.componentName())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** The system screen about [app], where it can also be uninstalled. */
    fun infoIntent(app: AppEntry): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Ways to show the page of [app] in a store, best first: in the Play Store, then in any
     * store app, then on the Play Store's website. The first one that opens is enough.
     */
    fun storeIntents(app: AppEntry): List<Intent> {
        val market = Uri.parse("market://details?id=${app.packageName}")
        val web = Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")
        return listOf(
            Intent(Intent.ACTION_VIEW, market).setPackage(PLAY_STORE),
            Intent(Intent.ACTION_VIEW, market),
            Intent(Intent.ACTION_VIEW, web),
        ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    /** Asks Android to uninstall [app]: it shows its own confirmation first. */
    fun uninstallIntent(app: AppEntry): Intent = Intent(Intent.ACTION_DELETE)
        .setData(Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Whether [app] can be uninstalled. Apps that came with the system cannot (Android would only
     * offer to remove their updates), so they are not offered for it.
     */
    fun canUninstall(app: AppEntry): Boolean = runCatching {
        packageManager.getApplicationInfo(app.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
    }.getOrDefault(false)

    /**
     * What kind of app [app] is, as its maker declared it: one of the `ApplicationInfo.CATEGORY_`
     * values, `CATEGORY_UNDEFINED` when it says nothing. A new folder is named after it.
     */
    @Suppress("DEPRECATION")
    fun category(app: AppEntry): Int = runCatching {
        val info = packageManager.getApplicationInfo(app.packageName, 0)
        if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) ApplicationInfo.CATEGORY_GAME else info.category
    }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)

    private fun query(category: String): List<AppEntry> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            .filter { it.activityInfo.packageName != appContext.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    label = info.loadLabel(packageManager).toString(),
                )
            }

    private fun AppEntry.componentName() = ComponentName(packageName, activityName)

    companion object {
        private const val PLAY_STORE = "com.android.vending"

        /** Bounds for the rendered-icon cache, scaled to the device's heap. */
        private const val MIN_ICON_CACHE_KB = 12 * 1024
        private const val MAX_ICON_CACHE_KB = 32 * 1024

        /** Room for the banners of apps on a TV: some forty at the sizes the TV draws them. */
        private const val BANNER_CACHE_KB = 12 * 1024

        /** Bounds for how many icons are dressed at once. */
        private const val MIN_ICON_THREADS = 2
        private const val MAX_ICON_THREADS = 4

        @Volatile
        private var instance: AppRepository? = null

        fun of(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also { instance = it }
        }
    }
}

/** The banner an app came with for a TV: [picture] is null when it has none. */
class AppBanner(val picture: ImageBitmap?)
