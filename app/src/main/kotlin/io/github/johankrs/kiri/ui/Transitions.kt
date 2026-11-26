package io.github.johankrs.kiri.ui

import android.app.ActivityOptions
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.AppOpenStyle
import io.github.johankrs.kiri.core.look.DrawerStyle
import io.github.johankrs.kiri.core.look.HomeStyle
import java.util.function.Consumer
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How an app should come on screen, to hand to `startActivity`. Null leaves it to the device.
 *
 * [iconBounds] is where the tapped icon is inside [view]; without it an app cannot grow out of
 * its icon, so the device's own way is used.
 */
internal fun appOpenOptions(style: AppOpenStyle, view: View, iconBounds: Rect?): ActivityOptions? = when (style) {
    AppOpenStyle.GROW -> iconBounds?.let { bounds ->
        ActivityOptions.makeScaleUpAnimation(
            view,
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.width.roundToInt(),
            bounds.height.roundToInt(),
        )
    }

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

        // The glass opens out of a circle as it is drawn (see [drawerGlass]): the sheet stays.
        DrawerStyle.REVEAL -> Unit

        // The glass clouds over quickly where it is; the apps then rise onto it.
        DrawerStyle.CASCADE -> alpha = (progress * CASCADE_GLASS).coerceAtMost(1f)
    }
}

/** In [DrawerStyle.CASCADE] the glass is all there once the drawer is 1/this of the way open. */
private const val CASCADE_GLASS = 2.2f

/**
 * Whether the apps of the drawer arrive all together, as one layer ([drawerContents]). In
 * the other styles each app arrives on its own (see [drawerItemArriving]).
 */
internal val DrawerStyle.arrivesWhole: Boolean
    get() = this == DrawerStyle.SLIDE || this == DrawerStyle.ZOOM || this == DrawerStyle.FADE

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
        DrawerStyle.FADE, DrawerStyle.REVEAL, DrawerStyle.CASCADE -> block()
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
 *
 * In [DrawerStyle.REVEAL] the glass is a circle around [revealFrom] instead, growing with
 * [progress] until it covers the screen, with an edge as soft as the rising one.
 */
internal fun DrawScope.drawerGlass(
    style: DrawerStyle,
    progress: Float,
    tint: Color,
    edge: SheetEdge,
    revealFrom: Offset = Offset.Unspecified,
    frost: DrawScope.() -> Unit,
) {
    if (style == DrawerStyle.REVEAL && progress < 1f) {
        revealedGlass(progress, tint, edge, revealFrom, frost)
        return
    }
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

/** The glass of [drawerGlass] seen only inside the circle of [DrawerStyle.REVEAL]. */
private fun DrawScope.revealedGlass(
    progress: Float,
    tint: Color,
    edge: SheetEdge,
    from: Offset,
    frost: DrawScope.() -> Unit,
) {
    val center = revealCenter(from, size)
    val radius = revealRadius(progress, center, size)
    if (radius <= 1f) return
    val soft = REVEAL_SOFT.toPx()
    val bounds = Rect(0f, 0f, size.width, size.height)
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, edge.revealLayer)
    clipRect {
        frost()
        drawRect(tint)
    }
    val whole = ((radius - soft) / radius).coerceIn(0f, 1f)
    // Full inside, nothing outside, and an S-curve between, so the rim never reads as a line.
    val stops = Array(EDGE_STOPS + 1) { i ->
        val along = i / EDGE_STOPS.toFloat()
        val gone = along * along * (3f - 2f * along)
        (whole + (1f - whole) * along) to Color.Black.copy(alpha = 1f - gone)
    }
    drawRect(
        brush = Brush.radialGradient(colorStops = arrayOf(0f to Color.Black) + stops, center = center, radius = radius),
        blendMode = BlendMode.DstIn,
    )
    canvas.restore()
}

/** Where the circle of [DrawerStyle.REVEAL] opens from: the finger, else the bottom middle. */
private fun revealCenter(from: Offset, size: Size): Offset =
    if (from.isSpecified) from else Offset(size.width / 2f, size.height)

/**
 * How large the circle of [DrawerStyle.REVEAL] is at [progress]. Open, it is so far past every
 * corner that both its soft rim and the apps arriving behind it are done.
 */
private fun androidx.compose.ui.unit.Density.revealRadius(progress: Float, center: Offset, size: Size): Float {
    val far = maxOf(
        hypot(center.x, center.y),
        hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y),
        hypot(size.width - center.x, size.height - center.y),
    )
    val past = maxOf(REVEAL_SOFT, REVEAL_LEAD + REVEAL_BAND).toPx()
    // It grows ahead of the finger, so the apps at the far end show before the end of the drag.
    val open = 1f - progress.coerceIn(0f, 1f)
    return (1f - open * open) * (far + past)
}

/** How wide the soft rim of the circle of [DrawerStyle.REVEAL] is. */
private val REVEAL_SOFT = 96.dp

/**
 * One app of the drawer as the drawer opens, in the styles where each comes on its own (see
 * [arrivesWhole]). [center] is where its middle is on [screen]; [revealFrom] is where the
 * circle of [DrawerStyle.REVEAL] opened from.
 */
internal fun GraphicsLayerScope.drawerItemArriving(
    style: DrawerStyle,
    progress: Float,
    revealFrom: Offset,
    screen: Size,
    center: Offset,
) {
    val open = progress.coerceIn(0f, 1f)
    if (open >= 1f) return
    when (style) {
        // Each app shows as the rim of the circle passes over it.
        DrawerStyle.REVEAL -> {
            val from = revealCenter(revealFrom, screen)
            val radius = revealRadius(open, from, screen)
            val inside = radius - REVEAL_LEAD.toPx() - (center - from).getDistance()
            val shown = (inside / REVEAL_BAND.toPx()).coerceIn(0f, 1f)
            val eased = 1f - (1f - shown) * (1f - shown)
            alpha *= shown
            val scale = 0.9f + 0.1f * eased
            scaleX *= scale
            scaleY *= scale
            translationY += (1f - eased) * 10.dp.toPx()
        }

        // Row by row from the bottom of the screen, each rising into place.
        DrawerStyle.CASCADE -> {
            val fromBottom = (1f - center.y / screen.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val shown = ((open - CASCADE_START - fromBottom * CASCADE_SPREAD) / CASCADE_EACH).coerceIn(0f, 1f)
            val eased = 1f - (1f - shown) * (1f - shown) * (1f - shown)
            alpha *= shown
            val scale = 0.97f + 0.03f * eased
            scaleX *= scale
            scaleY *= scale
            translationY += (1f - eased) * 70.dp.toPx()
        }

        DrawerStyle.SLIDE, DrawerStyle.ZOOM, DrawerStyle.FADE -> Unit
    }
}

/** An app of [DrawerStyle.REVEAL] starts to show when the rim is this far past its middle. */
private val REVEAL_LEAD = 60.dp

/** ...and is whole when the rim is this much further on. */
private val REVEAL_BAND = 120.dp

/** In [DrawerStyle.CASCADE], the bottom row starts to rise when the drawer is this far open, */
private const val CASCADE_START = 0.06f

/** the top row this much later, */
private const val CASCADE_SPREAD = 0.4f

/** and each row takes this much of the way to arrive. */
private const val CASCADE_EACH = 0.5f

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
    val revealLayer = Paint()
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

/**
 * The launcher's screen when it is [away] out of sight: 0 in place, 1 gone. Coming back in a
 * style that flies ([flies]) it goes a hair past 0 and settles.
 *
 * [pivot] is where it goes from and comes back to, in its own coordinates: the icon of the app
 * that opens. Null is the middle, as for the drawer. While an app is opening over it, it does
 * not fade ([fades] false): the app may take a moment to show, and the screen must not be
 * empty meanwhile. Under the drawer ([underDrawer]) it is always gone before the apps of the
 * drawer arrive, so the two are never seen through each other.
 *
 * In [HomeStyle.WAVE] each item moves on its own ([homeItemAway]) when [byItem]; where there
 * are no such items, as on a TV, the screen flies as a whole.
 */
internal fun GraphicsLayerScope.homeAway(
    style: HomeStyle,
    away: Float,
    pivot: Offset?,
    fades: Boolean = true,
    underDrawer: Boolean = false,
    byItem: Boolean = false,
) {
    val gone = away.coerceIn(0f, 1f)
    fun fadeBy(whole: Float) {
        if (fades) alpha = (1f - gone / whole).coerceIn(0f, 1f)
    }
    when (if (style == HomeStyle.WAVE && !byItem) HomeStyle.FLY_BACK else style) {
        HomeStyle.RECEDE -> {
            val scale = 1f - 0.07f * gone
            scaleX = scale
            scaleY = scale
            fadeBy(if (underDrawer) HOME_GONE else 0.4f)
        }

        HomeStyle.FLY_BACK -> {
            if (pivot != null && size.width > 0f && size.height > 0f) {
                transformOrigin = TransformOrigin(pivot.x / size.width, pivot.y / size.height)
            }
            val scale = 1f + FLY_REACH * away
            scaleX = scale
            scaleY = scale
            fadeBy(if (underDrawer) HOME_GONE else 0.6f)
        }

        // The items move themselves; the screen stays, and under the drawer it still fades.
        HomeStyle.WAVE -> if (underDrawer) fadeBy(HOME_GONE)

        // Under the drawer it goes the whole way sooner, so as to be gone when the apps arrive.
        HomeStyle.FOCUS -> {
            val deep = if (underDrawer) (gone / HOME_GONE).coerceAtMost(1f) else gone
            val scale = 1f - 0.05f * deep
            scaleX = scale
            scaleY = scale
            val blur = FOCUS_BLUR.toPx() * deep
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.5f) {
                renderEffect = BlurEffect(blur, blur, TileMode.Decal)
            }
            if (fades) alpha = (1f - (deep - 0.25f) / 0.75f).coerceIn(0f, 1f)
        }

        HomeStyle.FADE -> fadeBy(if (underDrawer) HOME_GONE else 0.5f)

        HomeStyle.NONE -> if (underDrawer) fadeBy(HOME_GONE)
    }
}

/** How much larger the screen is when it has flown out of sight in [HomeStyle.FLY_BACK]. */
private const val FLY_REACH = 0.45f

/** How blurred the screen is when it is out of sight in [HomeStyle.FOCUS]. */
private val FOCUS_BLUR = 10.dp

/**
 * One item of the launcher's screen (an app, a folder, a widget) in [HomeStyle.WAVE] when the
 * screen is [away] out of sight (see [homeAway]). [center] is where its middle is on [screen],
 * and [pivot] where the screen goes from and comes back to; null is the middle.
 *
 * Every item flies outward from [pivot] and grows as it goes. The nearest go last and come
 * back first, so on the way back a wave spreads out from [pivot].
 */
internal fun GraphicsLayerScope.homeItemAway(away: Float, pivot: Offset?, screen: Size, center: Offset, fades: Boolean = true) {
    if (away == 0f) return
    val from = pivot ?: Offset(screen.width / 2f, screen.height / 2f)
    val reach = maxOf(
        hypot(from.x, from.y),
        hypot(screen.width - from.x, from.y),
        hypot(from.x, screen.height - from.y),
        hypot(screen.width - from.x, screen.height - from.y),
    ).coerceAtLeast(1f)
    val offset = center - from
    val far = (offset.getDistance() / reach).coerceIn(0f, 1f)
    val own = if (away > 0f) {
        ((away - (1f - far) * WAVE_SPREAD) / (1f - WAVE_SPREAD)).coerceIn(0f, 1f)
    } else {
        // Settling: the nearest, which landed first, go a little further past.
        away * (1f - 0.5f * far)
    }
    val scale = 1f + 0.4f * own
    scaleX *= scale
    scaleY *= scale
    translationX += offset.x * 0.55f * own
    translationY += offset.y * 0.55f * own
    if (fades) alpha *= (1f - own / 0.75f).coerceIn(0f, 1f)
}

/** In [HomeStyle.WAVE], how much later the nearest items leave (and earlier land) than the farthest. */
private const val WAVE_SPREAD = 0.4f

/** Whether coming back in this style flies into place, a hair past it and back. */
internal val HomeStyle.flies: Boolean
    get() = this == HomeStyle.FLY_BACK || this == HomeStyle.WAVE

/** How the launcher's screen comes back in [style], [stiffnessScale] times as stiff. */
internal fun homeReturnSpring(style: HomeStyle, stiffnessScale: Float): SpringSpec<Float> = if (style.flies) {
    // Quick, and settling with the slightest give.
    spring(dampingRatio = 0.72f, stiffness = 520f * stiffnessScale, visibilityThreshold = 0.001f)
} else {
    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow * stiffnessScale, 0.001f)
}

/**
 * Moves one item of the launcher's screen (an app, a folder, a widget) while it is drawn,
 * given where its middle is on the screen.
 */
internal typealias ItemMove = GraphicsLayerScope.(center: Offset) -> Unit

/** How the items here move as the screen comes and goes. Null: they stay where they are. */
internal val LocalItemMove = staticCompositionLocalOf<ItemMove?> { null }

/**
 * Lets [move] move this item while it is drawn. Where it is is read only then, so moving
 * never measures or composes anything.
 */
@Composable
internal fun Modifier.itemMove(move: ItemMove?): Modifier {
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    if (move == null) return this
    return onPlaced { placed[0] = it }.graphicsLayer {
        val center = placed[0]?.takeIf { it.isAttached }?.boundsInRoot()?.center ?: return@graphicsLayer
        move(center)
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
