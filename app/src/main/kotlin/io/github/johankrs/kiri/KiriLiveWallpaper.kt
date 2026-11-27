package io.github.johankrs.kiri

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import io.github.johankrs.kiri.core.look.LiveLook
import io.github.johankrs.kiri.data.SettingsStore
import io.github.johankrs.kiri.ui.live.LiveRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Kiri's live wallpaper on a phone or tablet: the scene chosen in Kiri's settings, drawn small
 * and stretched (see [LiveRenderer]), following the home pages a little. It draws only while
 * it is seen, and changes as soon as the settings do.
 */
class KiriLiveWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = LiveEngine()

    private inner class LiveEngine : Engine() {
        private val renderer = LiveRenderer("wallpaper", stretched = true)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var look = LiveLook()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            scope.launch {
                SettingsStore.of(applicationContext).liveLook().collect { next ->
                    val recolored = next.color != look.color
                    look = next
                    renderer.setLook(next)
                    // Android follows the wallpaper's colors. Kiri's glass is told by its settings.
                    if (recolored) notifyColorsChanged()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.attach(holder, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer.detach()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            renderer.setSeen(visible)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            renderer.setOffset(xOffset)
        }

        override fun onComputeColors(): WallpaperColors = WallpaperColors(
            Color.valueOf(look.color.top),
            Color.valueOf(look.color.ground),
            Color.valueOf(look.color.deep),
        )

        override fun onDestroy() {
            scope.cancel()
            renderer.release()
            super.onDestroy()
        }
    }

    companion object {
        fun component(context: Context): ComponentName = ComponentName(context, KiriLiveWallpaper::class.java)

        /** Whether Kiri's live wallpaper is the one on the home screen now. */
        fun isOn(context: Context): Boolean =
            runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context) }.getOrDefault(false)
    }
}
