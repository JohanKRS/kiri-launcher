package io.github.johankrs.kiri.ui.tv

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

/**
 * The window of the TV's home screen: see-through, with the TV's own wallpaper behind it, only
 * while that is the wallpaper chosen ([showsSystem]). Any other is drawn by Kiri over the whole
 * screen, and the window is solid: the TV then has nothing under it to lay together with it at
 * every frame, which on a 4K screen it did with the same graphics card that draws Kiri.
 *
 * A live wallpaper still shows: its surface is under the window, which leaves a hole for it.
 */
@Composable
internal fun TvWindow(showsSystem: Boolean) {
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(window, showsSystem) { window.showWallpaper(showsSystem) }
    // As the theme has it, for whatever comes after the TV's home screen in this window.
    DisposableEffect(window) { onDispose { window.showWallpaper(true) } }
}

private fun Window.showWallpaper(show: Boolean) {
    if (show) {
        addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        // A solid background makes the window opaque, so nothing under it is laid together with it.
        setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }
}
