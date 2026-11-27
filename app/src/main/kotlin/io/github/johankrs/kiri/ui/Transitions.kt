package io.github.johankrs.kiri.ui

import android.app.ActivityOptions
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.AppOpenStyle
import io.github.johankrs.kiri.core.look.DrawerStyle
import io.github.johankrs.kiri.core.look.HomeReturnStyle
import java.util.function.Consumer
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How an app should come on screen, to hand to `startActivity`. Null leaves it to the device.
 *
 * [iconBounds] is where the tapped icon is inside [view]; without it an app cannot grow out of
 * its icon, so the device's own way is used.
 */
internal fun appOpenOptions(style: AppOpenStyle, view: View, iconBounds: Rect?): ActivityOptions? = when (style) {
    AppOpenStyle.FROM_ICON -> iconBounds?.let { bounds ->
        ActivityOptions.makeClipRevealAnimation(
            view,
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.width.roundToInt(),
            bounds.height.roundToInt(),
        )
    }

    AppOpenStyle.SLIDE_UP ->
        ActivityOptions.makeCustomAnimation(view.context, R.anim.kiri_app_slide_up, R.anim.kiri_stay)

    AppOpenStyle.FADE ->
        ActivityOptions.makeCustomAnimation(view.context, R.anim.kiri_app_fade_in, R.anim.kiri_stay)

    AppOpenStyle.SYSTEM -> null
}

/** The drawer sheet when it is [progress] of the way open (0 closed, 1 open). */
internal fun GraphicsLayerScope.drawerSheet(style: DrawerStyle, progress: Float) {
    if (progress <= 0f) {
        // Closed, it waits below the screen whatever the style, so it never takes a touch.
        translationY = size.height
        alpha = 0f
        return
    }
    when (style) {
        // It fades in as it starts to rise, but in [drawerGlass], not here: the soft edge above
        // it is drawn outside its bounds, and a see-through layer would cut that off.
        DrawerStyle.SLIDE -> translationY = (1f - progress) * size.height

        DrawerStyle.ZOOM -> {
            val scale = 1.12f - 0.12f * progress
            scaleX = scale
            scaleY = scale
            alpha = progress
        }

        DrawerStyle.FADE -> alpha = progress
    }
}

/**
 * What lies on the drawer sheet, the apps, when the sheet is [progress] of the way open.
 *
 * The sheet and what lies on it are two layers at slightly different depths: the sheet comes
 * first, and the apps settle onto it a moment later, from a hair closer to the eye. Closing
 * runs the same way backwards. It is meant to be felt more than seen.
 */
internal fun GraphicsLayerScope.drawerContents(progress: Float) {
    val arrived = ((progress - CONTENTS_START) / (1f - CONTENTS_START)).coerceIn(0f, 1f)
    // Quick at first, then gently into place.
    val settled = 1f - (1f - arrived) * (1f - arrived)
    alpha = settled
    val scale = 1f + CONTENTS_LIFT * (1f - settled)
    scaleX = scale
    scaleY = scale
    translationY = (1f - settled) * CONTENTS_DROP_DP * density
}

/** How far open the sheet is before the apps start to show. */
private const val CONTENTS_START = 0.3f

/** How much larger the apps are before they settle. */
private const val CONTENTS_LIFT = 0.035f

/** How far below their place the apps start, in dp. */
private const val CONTENTS_DROP_DP = 12f

/**
 * Draws [block] where it would be on a screen at rest, from inside the sheet that
 * [drawerSheet] moved: the sheet travels, and the wallpaper it blurs stays where it is. So the
 * blur belongs to the sheet: it is seen only where the sheet is, and comes and goes with it.
 */
internal fun DrawScope.heldStill(style: DrawerStyle, progress: Float, block: DrawScope.() -> Unit) {
    when (style) {
        DrawerStyle.SLIDE -> translate(top = -(1f - progress.coerceIn(0f, 1f)) * size.height, block = block)
        DrawerStyle.ZOOM -> scale(1f / (1.12f - 0.12f * progress.coerceIn(0f, 1f)), block = block)
        DrawerStyle.FADE -> block()
    }
}

/**
 * The glass of the drawer sheet when it is [progress] of the way open: what it shows through
 * itself ([frost], the blurred wallpaper) under its [tint].
 *
 * A sheet that [rises] has no hard top edge: above it the glass dissolves into what is
 * behind, from its full colour to nothing over the height of [edge]. Fully open, that soft
 * edge is above the screen, and the whole screen is the same glass. Such a sheet also fades
 * in over the first part of its way (the apps on it only come later).
 */
internal fun DrawScope.drawerGlass(
    style: DrawerStyle,
    progress: Float,
    tint: Color,
    edge: SheetEdge,
    frost: DrawScope.() -> Unit,
) {
    val rises = style.rises
    val fade = if (rises) (progress * RISE_FADE_IN).coerceIn(0f, 1f) else 1f
    // Fully open, the soft edge lies above the screen: nothing of it would be seen.
    val soft = if (rises && progress < 1f) edge.heightPx else 0f
    val width = size.width
    val height = size.height
    val canvas = drawContext.canvas
    if (fade < 1f) {
        edge.faded.alpha = fade
        canvas.saveLayer(Rect(0f, -soft, width, height), edge.faded)
    }
    clipRect(0f, 0f, width, height) {
        frost()
        drawRect(tint)
    }
    if (soft > 0f) {
        // Only the strip of the soft edge is drawn apart, to be dissolved: a small layer.
        val strip = Rect(0f, -soft, width, 0f)
        canvas.saveLayer(strip, edge.layer)
        clipRect(strip.left, strip.top, strip.right, strip.bottom) {
            frost()
            drawRect(tint, topLeft = strip.topLeft, size = strip.size)
        }
        drawRect(edge.mask, topLeft = strip.topLeft, size = strip.size, blendMode = BlendMode.DstIn)
        canvas.restore()
    }
    if (fade < 1f) canvas.restore()
}

/**
 * The soft top edge of a rising drawer sheet, [heightPx] tall, made once for a screen.
 * Its glass goes from nothing to full on an S-curve, so neither end of it reads as a line.
 */
internal class SheetEdge(val heightPx: Float) {
    val mask: Brush = Brush.verticalGradient(
        colorStops = Array(EDGE_STOPS + 1) { i ->
            val along = i / EDGE_STOPS.toFloat()
            along to Color.Black.copy(alpha = along * along * (3f - 2f * along))
        },
        startY = -heightPx,
        endY = 0f,
    )
    val layer = Paint()
    val faded = Paint()
}

/** How tall the soft top edge of a rising drawer sheet is. */
internal val SHEET_EDGE = 140.dp

/** How many steps the S-curve of the soft edge is drawn with. */
private const val EDGE_STOPS = 12

/** A sheet that rises fades in over the first 1/this of its way. */
private const val RISE_FADE_IN = 4f

/**
 * Whether the drawer sheet comes from below in this style, so that its top edge crosses the
 * screen: that edge is drawn soft (see [drawerGlass]). A sheet that only grows or fades in
 * has its edges outside the screen all the way.
 */
internal val DrawerStyle.rises: Boolean
    get() = this == DrawerStyle.SLIDE

/** How far the drawer has come when the home screen under it can no longer be seen. */
private const val HOME_GONE = 0.34f

/** The home screen while the drawer comes over it: it fades, and steps back a little. */
internal fun GraphicsLayerScope.homeBehindDrawer(style: DrawerStyle, progress: Float) {
    // Gone before the apps of the drawer begin to arrive, so the two are never seen through
    // each other: the home screen leaves, the sheet comes, and then the apps settle on it.
    alpha = (1f - progress / HOME_GONE).coerceIn(0f, 1f)
    val scale = when (style) {
        DrawerStyle.SLIDE -> 1f - 0.06f * progress
        DrawerStyle.ZOOM -> 1f - 0.1f * progress
        DrawerStyle.FADE -> 1f
    }
    scaleX = scale
    scaleY = scale
}

/** The launcher's own screen as it comes back after an app: [arrived] goes from 0 to 1. */
internal fun GraphicsLayerScope.homeReturning(style: HomeReturnStyle, arrived: Float) {
    when (style) {
        HomeReturnStyle.ZOOM -> {
            val scale = 0.92f + 0.08f * arrived
            scaleX = scale
            scaleY = scale
            alpha = arrived
        }

        HomeReturnStyle.FADE -> alpha = arrived
        HomeReturnStyle.NONE -> Unit
    }
}

/**
 * Blurs what is behind the launcher's window, which is the wallpaper, by [radiusPx] pixels.
 *
 * The system does the blurring, and only from Android 12 on; a device may also refuse it (see
 * [rememberSystemBlurEnabled]), and then nothing happens. It is the only blur that follows a
 * live wallpaper. [radiusPx] is read as state, so the
 * blur follows whatever it depends on, such as how far the drawer is open.
 */
@Composable
internal fun WallpaperBlur(radiusPx: () -> Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val window = LocalActivity.current?.window ?: return
    val radius by rememberUpdatedState(radiusPx)
    LaunchedEffect(window) {
        snapshotFlow { radius().coerceAtLeast(0) }
            .distinctUntilChanged()
            .collect { px -> window.blurBehind(px) }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.blurBehind(radiusPx: Int) {
    if (radiusPx > 0) {
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
    attributes = attributes.apply { blurBehindRadius = radiusPx }
}

/**
 * Whether the device blurs what is behind an app's window when asked, right now. It can be
 * missing altogether, and where it exists it goes away while the device saves battery or when
 * the person asked for less transparency.
 */
@Composable
internal fun rememberSystemBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember { mutableStateOf(windowManager?.isCrossWindowBlurEnabled == true) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { now -> enabled = now }
        windowManager?.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager?.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}
