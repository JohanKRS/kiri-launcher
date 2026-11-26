package io.github.johankrs.kiri.ui.theme

import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.max

/**
 * What glass shows through itself: the wallpaper, already blurred, as Kiri read it.
 *
 * Not every device blurs what is behind an app's window, and none does it for single panes
 * inside the window. So every pane of glass draws its own piece of this picture, the piece that
 * is right behind it on screen, and looks frosted anywhere.
 */
@Stable
class Backdrop {
    /**
     * The blurred wallpaper, as tall as the screen and at least as wide: wider when the
     * wallpaper travels with the pages. Null when there is none to show.
     */
    var image: ImageBitmap? by mutableStateOf(null)

    /**
     * How far the wallpaper has travelled, from 0 (its left end is on screen) to 1 (its right
     * end). Read only while drawing, so glass follows the wallpaper without being rebuilt.
     */
    var travel: Float by mutableFloatStateOf(0.5f)

    /**
     * The wallpaper blurred until only its colours are left, for a whole screen of glass such
     * as the settings. Null when Kiri has no picture of the wallpaper.
     */
    var deepImage: ImageBitmap? by mutableStateOf(null)

    /** The size of the screen the picture is stretched over, in pixels. */
    var screenSize: IntSize by mutableStateOf(IntSize.Zero)

    /** Top-left corner of the root launcher view on screen, measured once at the root. */
    var windowOrigin: Offset by mutableStateOf(Offset.Zero)
}

/**
 * Draws [image], a picture of the wallpaper, as the wallpaper lies right now on a screen of
 * [width] by [height] pixels whose corner is at the origin: stretched to the height, and moved
 * sideways by how far it has travelled ([travel], from 0 to 1). What falls outside the screen
 * is for the caller to cut.
 */
fun DrawScope.drawWallpaper(image: ImageBitmap, travel: Float, width: Float, height: Float) {
    val fullWidth = max(image.width * height / image.height, width)
    translate(left = -(fullWidth - width) * travel) {
        drawImage(
            image = image,
            dstSize = IntSize(ceil(fullWidth).toInt(), ceil(height).toInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** The backdrop for the glass below this point, or null where glass is only tinted. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Draws, behind this element, the piece of the blurred wallpaper that is behind it on screen.
 * With a [shape] the piece is cut to it (via an anti-aliased [BitmapShader], avoiding GPU
 * stencil clipPath passes); without one it fills the bounds of the element.
 * With [deep] it is the picture blurred the most. With [vivid] its colours come out stronger
 * and darker (see [VIVID_SATURATION] and [VIVID_BRIGHTNESS]): glass that shows the colour of
 * the wallpaper, yet keeps white text readable on a light one. Where there is no
 * [LocalBackdrop] it draws nothing and skips coordinate tracking entirely.
 */
fun Modifier.backdrop(shape: Shape? = null, deep: Boolean = false, vivid: Boolean = false): Modifier =
    this then BackdropElement(shape, deep, vivid)

/** How much more colourful the wallpaper is behind vivid glass. */
const val VIVID_SATURATION = 1.6f

/** How bright the wallpaper is left behind vivid glass, as a share of its brightness. */
const val VIVID_BRIGHTNESS = 0.46f

private val VividFilter = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(VIVID_SATURATION)
        postConcat(ColorMatrix().apply { setScale(VIVID_BRIGHTNESS, VIVID_BRIGHTNESS, VIVID_BRIGHTNESS, 1f) })
    },
)

private data class BackdropElement(
    val shape: Shape?,
    val deep: Boolean,
    val vivid: Boolean,
) : ModifierNodeElement<BackdropNode>() {
    override fun create(): BackdropNode = BackdropNode(shape, deep, vivid)

    override fun update(node: BackdropNode) {
        node.update(shape, deep, vivid)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "backdrop"
        properties["shape"] = shape
        properties["deep"] = deep
        properties["vivid"] = vivid
    }
}

private class BackdropNode(
    private var shape: Shape?,
    private var deep: Boolean,
    vivid: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var originInRoot: Offset = Offset.Zero
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = if (vivid) VividFilter else null
    }

    private var cachedImage: ImageBitmap? = null
    private var cachedShader: BitmapShader? = null

    private var cachedPathShape: Shape? = null
    private var cachedPathSize: Size = Size.Unspecified
    private var cachedPathDirection: LayoutDirection? = null
    private var cachedPathDensity: Float = Float.NaN
    private var cachedAndroidPath: android.graphics.Path? = null

    fun update(shape: Shape?, deep: Boolean, vivid: Boolean) {
        val filter = if (vivid) VividFilter else null
        if (paint.colorFilter !== filter) {
            paint.colorFilter = filter
            invalidateDraw()
        }
        if (this.shape != shape) {
            this.shape = shape
            cachedPathShape = null
            cachedAndroidPath = null
            invalidateDraw()
        }
        if (this.deep != deep) {
            this.deep = deep
            invalidateDraw()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!isAttached) return
        // Inside surfaces without a backdrop (such as the app drawer), skip coordinate math completely.
        if (currentValueOf(LocalBackdrop) == null) return
        val inRoot = coordinates.positionInRoot()
        if (inRoot != originInRoot) {
            originInRoot = inRoot
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val backdrop = currentValueOf(LocalBackdrop)
        if (backdrop != null) {
            val image = if (deep) backdrop.deepImage else backdrop.image
            val screen = backdrop.screenSize
            if (image != null && screen.width > 0 && screen.height > 0 && image.width > 0 && image.height > 0) {
                val shader = shaderFor(image)
                val screenW = screen.width.toFloat()
                val screenH = screen.height.toFloat()
                val fullWidth = max(image.width * screenH / image.height, screenW)
                val travel = backdrop.travel
                val win = backdrop.windowOrigin
                val atX = originInRoot.x + win.x
                val atY = originInRoot.y + win.y

                matrix.setScale(fullWidth / image.width, screenH / image.height)
                matrix.postTranslate(-(fullWidth - screenW) * travel - atX, -atY)
                shader.setLocalMatrix(matrix)
                paint.shader = shader

                val native = drawContext.canvas.nativeCanvas
                val targetShape = shape
                if (targetShape != null) {
                    val path = pathFor(targetShape, size, layoutDirection, density)
                    native.drawPath(path, paint)
                } else {
                    native.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        }
        drawContent()
    }

    private fun shaderFor(image: ImageBitmap): BitmapShader {
        if (cachedImage === image) {
            cachedShader?.let { return it }
        }
        val created = BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        cachedImage = image
        cachedShader = created
        return created
    }

    private fun ContentDrawScope.pathFor(
        targetShape: Shape,
        size: Size,
        direction: LayoutDirection,
        density: Float,
    ): android.graphics.Path {
        val existing = cachedAndroidPath
        if (existing != null &&
            cachedPathShape == targetShape &&
            cachedPathSize == size &&
            cachedPathDirection == direction &&
            cachedPathDensity == density
        ) {
            return existing
        }
        val composePath = Path().apply { addOutline(targetShape.createOutline(size, direction, this@pathFor)) }
        val androidPath = composePath.asAndroidPath()
        cachedPathShape = targetShape
        cachedPathSize = size
        cachedPathDirection = direction
        cachedPathDensity = density
        cachedAndroidPath = androidPath
        return androidPath
    }
}

/**
 * The sheet the settings lie on: the wallpaper, blurred until only its colours are left, under
 * so much of the colour of the theme that every word on it reads well. The wallpaper is only
 * just felt underneath. Where Kiri has no picture of the wallpaper the colour is nearly solid.
 */
@Composable
fun Modifier.kiriSheet(): Modifier {
    val surface = KiriTheme.colors.surface
    val frosted = LocalBackdrop.current?.deepImage != null
    return this
        .backdrop(deep = true)
        .background(surface.copy(alpha = if (frosted) SHEET_TINT else SHEET_TINT_ALONE))
}

private const val SHEET_TINT = 0.84f
private const val SHEET_TINT_ALONE = 0.95f

/**
 * Stands in for the wallpaper inside a preview: a plain colour, or with [blurred] the whole
 * blurred wallpaper when Kiri has it.
 *
 * A preview is a small picture of a full screen, so what is inside it is not where it seems to
 * be: glass in [content] is only tinted, it does not look for its piece of the wallpaper.
 */
@Composable
fun WallpaperStandIn(
    modifier: Modifier = Modifier,
    blurred: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    Box(
        modifier = modifier
            .background(KiriTheme.colors.previewBackdrop)
            .drawBehind {
                val image = backdrop?.image
                if (blurred && image != null) {
                    clipRect { drawWallpaper(image, backdrop.travel, size.width, size.height) }
                }
            },
    ) {
        CompositionLocalProvider(LocalBackdrop provides null) {
            content()
        }
    }
}
