package io.github.johankrs.kiri

import android.app.UiModeManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.data.SettingsStore
import io.github.johankrs.kiri.data.WallpaperSource
import io.github.johankrs.kiri.data.WallpaperStudio
import io.github.johankrs.kiri.ui.KiriApp
import io.github.johankrs.kiri.ui.theme.Backdrop
import io.github.johankrs.kiri.ui.theme.GlassPanes
import io.github.johankrs.kiri.ui.theme.LocalBackdrop
import io.github.johankrs.kiri.widgets.system.SystemWidgets

class MainActivity : ComponentActivity() {
    /** Bumped every time the launcher comes back to the front. */
    private var refreshTick by mutableIntStateOf(0)

    /** Bumped every time the Home button is pressed, so the launcher returns to its first screen. */
    private var homeTick by mutableIntStateOf(0)

    /**
     * Bumped when the launcher is in front again without having been out of sight, as when
     * something that covered only part of it closes. A return from out of sight bumps
     * [refreshTick] instead.
     */
    private var resumeTick by mutableIntStateOf(0)

    /** Whether the launcher came back from out of sight since it was last in front. */
    private var startedSinceResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val repository = AppRepository.of(this)
        val settings = SettingsStore.of(this)
        setContent {
            // The blurred wallpaper that every pane of glass shows through, wherever it is.
            val backdrop = remember { Backdrop() }
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                KiriApp(
                    repository = repository,
                    settings = settings,
                    backdrop = backdrop,
                    refreshTick = refreshTick,
                    homeTick = homeTick,
                    resumeTick = resumeTick,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Widgets of other apps only change what they show while someone listens for it.
        SystemWidgets.of(this).startListening()
        // What the Home button opens may have changed while Kiri was out of sight.
        KiriAccessibilityService.refresh()
        refreshTick++
        startedSinceResume = true
    }

    override fun onResume() {
        super.onResume()
        if (!startedSinceResume) resumeTick++
        startedSinceResume = false
    }

    override fun onStop() {
        super.onStop()
        SystemWidgets.of(this).stopListening()
    }

    override fun onDestroy() {
        SystemWidgets.of(this).forget(this)
        super.onDestroy()
    }

    /** How a widget's own setup screen ended. Android only reports that this way. */
    @Deprecated("Deprecated in the platform, but the only way a widget's setup screen answers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        SystemWidgets.of(this).onActivityResult(requestCode, resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeTick++
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppRepository.of(this).onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            WallpaperSource.of(this).trimMemory()
            WallpaperStudio.of(this).releasePreview()
            GlassPanes.clear()
        }
    }
}

fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
