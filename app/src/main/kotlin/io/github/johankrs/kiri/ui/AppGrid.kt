package io.github.johankrs.kiri.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.GridSpec
import io.github.johankrs.kiri.core.look.GlassShine
import io.github.johankrs.kiri.core.look.IconSettings
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.data.IconPicture
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.backdrop
import io.github.johankrs.kiri.ui.theme.kiriGlassPlate
import kotlin.math.roundToInt

/**
 * A scrolling grid of apps laid out by [spec].
 *
 * With [interactive] off it is a picture of the grid: it does not scroll, launch or take focus.
 * Settings use that to show a live preview drawn by the very same code as the real thing.
 *
 * [topRoom] and [bottomRoom] are what the bars of the system take at the two ends. The grid
 * reaches under them: at rest the apps stay clear of the bars, and scrolled they dissolve as
 * they go under one, instead of being cut at its edge.
 */
@Composable
fun AppGrid(
    apps: List<AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    labelStyle: TextStyle,
    interactive: Boolean,
    onLaunch: (app: AppEntry, iconBounds: Rect?) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((app: AppEntry, iconBounds: Rect) -> Unit)? = null,
    topRoom: Dp = 0.dp,
    bottomRoom: Dp = 0.dp,
) {
    val gridState = rememberLazyGridState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The narrowest a cell of the grid gets: the grid splits its width in whole pixels and
        // gives what is left over to some cells only, so a name is fitted to the others.
        val cellWidthPx = with(LocalDensity.current) {
            (constraints.maxWidth - spec.marginHorizontalDp.dp.roundToPx() * 2) / spec.columns
        }
        val labels = rememberLabelMetrics(apps, cellWidthPx, labelStyle, spec.showLabels)
        // The icons are told how they are scrolled, to lean and catch the light as they go.
        val glass = rememberGlassMotion(horizontal = false)
        val scrolled = remember(glass) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    glass.moved(-consumed.y)
                    return Offset.Zero
                }
            }
        }

        CompositionLocalProvider(LocalGlassMotion provides if (interactive) glass else null) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(spec.columns),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrolled)
                    // Whole where the apps rest, and gone before they are far under a bar.
                    .softEnds(
                        top = SoftEnd(
                            gone = topRoom * GONE_UNDER_BAR,
                            whole = topRoom + minOf(spec.marginTopDp.dp, SOFT_END),
                        ),
                        bottom = SoftEnd(
                            gone = bottomRoom * GONE_UNDER_BAR,
                            whole = bottomRoom + minOf(spec.marginBottomDp.dp, SOFT_END),
                        ),
                        fadeTop = { gridState.canScrollBackward },
                        fadeBottom = { gridState.canScrollForward },
                    ),
                contentPadding = PaddingValues(
                    start = spec.marginHorizontalDp.dp,
                    end = spec.marginHorizontalDp.dp,
                    top = topRoom + spec.marginTopDp.dp,
                    bottom = bottomRoom + spec.marginBottomDp.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(spec.gapDp.dp),
                userScrollEnabled = interactive,
            ) {
                items(apps, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        spec = spec,
                        labelStyle = labelStyle,
                        labelHeight = labels.height,
                        repository = repository,
                        interactive = interactive,
                        onClick = { bounds -> onLaunch(app, bounds) },
                        onLongClick = if (onLongPress != null) { { bounds: Rect -> onLongPress(app, bounds) } } else null,
                        fittedLabelStyle = labels.styleFor(app.label),
                    )
                }
            }
        }
    }
}

/**
 * Shared label measurements for a grid: the height of the tallest name so rows line up, and the
 * fitted [TextStyle] for each long name so individual cells never need a second layout pass.
 */
@Immutable
internal class LabelMetrics(
    val height: Dp,
    private val fitted: Map<String, TextStyle>,
    private val baseStyle: TextStyle,
) {
    fun styleFor(label: String): TextStyle = fitted[label] ?: baseStyle
}

/**
 * App names are never cut: a long name wraps onto as many lines as it needs. So that every row
 * of the grid still has the same height, all cells reserve the height of the tallest name and
 * reuse the fitted styles computed here.
 */
@Composable
internal fun rememberLabelMetrics(
    apps: List<AppEntry>,
    cellWidthPx: Int,
    labelStyle: TextStyle,
    showLabels: Boolean,
): LabelMetrics {
    val labels = remember(apps) { apps.map { it.label } }
    return rememberLabelMetricsOf(labels, cellWidthPx, labelStyle, showLabels)
}

/** The same as [rememberLabelMetrics], for any names: those of apps and of folders together. */
@Composable
internal fun rememberLabelMetricsOf(
    labels: List<String>,
    cellWidthPx: Int,
    labelStyle: TextStyle,
    showLabels: Boolean,
): LabelMetrics {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    return remember(labels, cellWidthPx, labelStyle, showLabels, density) {
        if (!showLabels || labels.isEmpty() || cellWidthPx <= 0) {
            LabelMetrics(0.dp, emptyMap(), labelStyle)
        } else {
            val constraints = Constraints(maxWidth = cellWidthPx.coerceAtLeast(1))
            val fitted = HashMap<String, TextStyle>()
            var tallestPx = 0
            for (label in labels) {
                val style = fitted[label] ?: measurer.fitLabel(label, labelStyle, cellWidthPx).also {
                    if (it !== labelStyle) fitted[label] = it
                }
                val heightPx = measurer.measure(text = label, style = style, constraints = constraints).size.height
                if (heightPx > tallestPx) tallestPx = heightPx
            }
            LabelMetrics(with(density) { tallestPx.toDp() }, fitted, labelStyle)
        }
    }
}

/** A word of at least this many letters may be too long for a cell: shorter ones are not measured. */
private const val LONG_WORD = 9

/** A name of at least this many letters may need a third line: shorter ones are not measured. */
private const val LONG_NAME = 13

/** The smallest a label is made, as a share of its size, to keep a long word in one piece. */
private const val SMALLEST_LABEL = 0.72f

/** A name is kept to this many lines when a slightly smaller size is enough for that. */
private const val MOST_LINES = 2

/**
 * The steps a long name is taken down by to fit on [MOST_LINES] lines: a share of its size,
 * and how much closer its letters come, in ems. Small steps, so no name shrinks more than it
 * must and the difference from its neighbours stays hard to notice.
 */
private val TIGHTER_LABELS = listOf(0.94f to -0.01f, 0.88f to -0.02f, 0.82f to -0.025f, 0.78f to -0.03f)

/**
 * [style] for [label] in a cell [widthPx] wide. A name is never cut. A word is not broken in
 * two if a slightly smaller size keeps it whole: "Supermercados" stays one word instead of
 * leaving an "s" on a line of its own. And a long name is made a little smaller, its letters a
 * little closer, when that keeps it on two lines: three are too many under an icon. Only a
 * name that fits on two lines at no size it can still be read at keeps its third line.
 */
internal fun TextMeasurer.fitLabel(label: String, style: TextStyle, widthPx: Int): TextStyle {
    if (widthPx <= 0) return style
    val longest = label.split(' ').maxByOrNull { it.length } ?: return style
    if (longest.length < LONG_WORD && label.length < LONG_NAME) return style
    var fitted = style
    if (longest.length >= LONG_WORD) {
        val wide = measure(text = longest, style = style, softWrap = false, maxLines = 1).size.width
        if (wide > widthPx) {
            val share = (widthPx / wide.toFloat() * 0.98f).coerceAtLeast(SMALLEST_LABEL)
            fitted = style.smaller(share)
        }
    }
    val constraints = Constraints(maxWidth = widthPx)
    if (measure(text = label, style = fitted, constraints = constraints).lineCount <= MOST_LINES) return fitted
    for ((share, closer) in TIGHTER_LABELS) {
        val tighter = style.smaller(share).copy(letterSpacing = closer.em)
        if (measure(text = label, style = tighter, constraints = constraints).lineCount <= MOST_LINES) return tighter
    }
    return fitted
}

/** This style at [share] of its size: the lines come closer together with the letters. */
private fun TextStyle.smaller(share: Float): TextStyle =
    copy(fontSize = fontSize * share, lineHeight = if (lineHeight.isSp) lineHeight * share else lineHeight)

/** How far into the margin of the grid the apps are still dissolving, at the most. */
private val SOFT_END = 14.dp

/** How far under a bar of the system the last of an app is seen, as a share of the bar. */
private const val GONE_UNDER_BAR = 0.3f

private val FocusShape = RoundedCornerShape(16.dp)
private val SamplesShape = RoundedCornerShape(16.dp)

/** The name of an app under its icon, fitted to the width it gets (see [fitLabel]). */
@Composable
private fun AppLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    var widthPx by remember { mutableIntStateOf(0) }
    val fitted = remember(text, style, widthPx) { measurer.fitLabel(text, style, widthPx) }
    BasicText(text = text, style = fitted, modifier = modifier.onSizeChanged { widthPx = it.width })
}

private class FixedState<T>(override val value: T) : State<T>

/**
 * How icons look where that is not what [AppRepository.iconSettings] says: home and drawer
 * keep the look they had while the settings cover them. Null follows the repository.
 */
internal val LocalIconSettings = compositionLocalOf<IconSettings?> { null }

/**
 * Whether what is drawn here is out of sight just now, as under the settings or in the closed
 * drawer. It is asked only when the focus looks for a place, so it can change at any frame
 * without anything being composed again; what is out of sight takes no focus.
 */
internal val LocalOutOfSight = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * The icon of [app] at [sizePx], dressed in the look the person chose.
 *
 * An icon seen before is there from the first frame without spawning a coroutine; a new one
 * arrives a moment later. Nothing is given before the saved look is known, so an icon is never
 * seen changing right after it appears. When the look changes, the old picture stays until the
 * new one is ready.
 */
@Composable
internal fun rememberAppIcon(app: AppEntry, repository: AppRepository, sizePx: Int): State<IconPicture?> {
    val settings = LocalIconSettings.current ?: repository.iconSettings
    val cached = remember(app.key, sizePx, settings) {
        settings?.let { repository.cachedIcon(app, sizePx, it) }
    }
    val loaded = remember(app.key) { mutableStateOf(cached) }
    if (cached != null) {
        SideEffect {
            if (loaded.value !== cached) loaded.value = cached
        }
        return remember(cached) { FixedState(cached) }
    }
    if (settings != null && sizePx > 0) {
        LaunchedEffect(app.key, sizePx, settings) {
            loaded.value = repository.loadIcon(app, sizePx, settings)
        }
    }
    return loaded
}

/**
 * Shows [picture] filling this element: on its plate of glass when it has one, over the
 * shadow it casts when it is a tile of glass. The relief of the tile is in the picture itself.
 */
@Composable
internal fun IconFace(picture: IconPicture?, modifier: Modifier = Modifier) {
    // While the icons are scrolled they answer like tiles of glass, where that was asked for.
    val motion = LocalGlassMotion.current?.takeIf { picture != null && picture.shine != GlassShine.OFF }
    val placed = if (motion != null && picture != null) modifier.glassTilt(motion, picture.shine) else modifier
    val shadow = picture?.shadow
    val shaded = if (picture != null && shadow != null && picture.bitmap.width > 0) {
        placed.drawBehind {
            // The picture was made for a size; it is shown at whatever this element is.
            val scale = size.width / picture.bitmap.width
            val pad = (picture.shadowPad * scale).roundToInt()
            drawImage(
                image = shadow,
                dstOffset = IntOffset(-pad, -pad),
                dstSize = IntSize((shadow.width * scale).roundToInt(), (shadow.height * scale).roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    } else {
        placed
    }
    val dressed = when {
        picture == null -> shaded
        picture.plate -> shaded.kiriGlassPlate(picture.glass, picture.shape.outline(), picture.glassOpacity, edge = false)
        else -> shaded
    }
    if (picture == null) {
        Box(dressed)
    } else {
        val imageModifier = if (motion != null && picture.shaped) {
            dressed.glassSheen(motion, picture.shine, picture.shape.outline())
        } else {
            dressed
        }
        Image(bitmap = picture.bitmap, contentDescription = null, modifier = imageModifier)
    }
}

/**
 * A square of [side], or a smaller one where there is less room: never anything but a square.
 * A grid with many columns has cells narrower than its icons, and an icon squeezed only
 * sideways would lose its shape.
 */
internal fun Modifier.squareUpTo(side: Dp): Modifier = layout { measurable, constraints ->
    val fitted = minOf(side.roundToPx(), constraints.maxWidth, constraints.maxHeight).coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(fitted, fitted))
    layout(fitted, fitted) { placeable.place(0, 0) }
}

/** Just the icon of [app], at [size]. */
@Composable
internal fun AppIcon(app: AppEntry, repository: AppRepository, size: Dp, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by rememberAppIcon(app, repository, sizePx)
    IconFace(icon, modifier.size(size))
}

/**
 * One app: its icon and, when the grid shows names, its name underneath.
 *
 * A tap and a long press both report where the icon is on screen (in root coordinates), so that
 * what they open can grow out of the icon or sit next to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppCell(
    app: AppEntry,
    spec: GridSpec,
    labelStyle: TextStyle,
    labelHeight: Dp,
    repository: AppRepository,
    interactive: Boolean,
    onClick: (iconBounds: Rect?) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((iconBounds: Rect) -> Unit)? = null,
    fittedLabelStyle: TextStyle? = null,
) {
    val iconSizePx = with(LocalDensity.current) { spec.iconSizeDp.dp.roundToPx() }
    val icon by rememberAppIcon(app, repository, iconSizePx)
    // Skip fade-in animation state completely when the icon is already cached on first frame.
    val startedReady = remember(app.key) { icon != null }
    val iconAlpha = if (startedReady) {
        null
    } else {
        animateFloatAsState(if (icon != null) 1f else 0f, tween(180), label = "icon")
    }

    val iconPlace = if (interactive) remember { arrayOfNulls<LayoutCoordinates>(1) } else null
    val cellModifier = if (interactive) {
        val iconBounds = { iconPlace?.get(0)?.takeIf { it.isAttached }?.boundsInRoot() }
        var focused by remember { mutableStateOf(false) }
        // The cell gives a little under the finger and springs back, read only during draw.
        val interactions = remember { MutableInteractionSource() }
        val pressed by interactions.collectIsPressedAsState()
        val pressScale = animateFloatAsState(
            targetValue = if (pressed) 0.9f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
            label = "press",
        )
        val outOfSight = LocalOutOfSight.current
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.background(color = labelStyle.color.copy(alpha = 0.18f), shape = FocusShape)
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val scale = pressScale.value
                scaleX = scale
                scaleY = scale
            }
            .focusProperties { if (outOfSight()) canFocus = false }
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = { onClick(iconBounds()) },
                onLongClick = if (onLongClick != null) { { iconBounds()?.let(onLongClick) } } else null,
            )
    } else {
        modifier.fillMaxWidth()
    }

    // Without its name under it, the icon still has to say which app it is.
    val named = if (spec.showLabels) cellModifier else cellModifier.semantics { contentDescription = app.label }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = named.padding(vertical = 6.dp),
    ) {
        val iconBase = Modifier.squareUpTo(spec.iconSizeDp.dp)
        val iconTracked = if (iconPlace != null) iconBase.onPlaced { iconPlace[0] = it } else iconBase
        val iconModifier = if (iconAlpha != null) {
            iconTracked.graphicsLayer { alpha = iconAlpha.value }
        } else {
            iconTracked
        }
        IconFace(picture = icon, modifier = iconModifier)
        if (spec.showLabels) {
            Spacer(Modifier.height(6.dp))
            val labelModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = labelHeight)
            if (fittedLabelStyle != null) {
                BasicText(text = app.label, style = fittedLabelStyle, modifier = labelModifier)
            } else {
                AppLabel(text = app.label, style = labelStyle, modifier = labelModifier)
            }
        }
    }
}

/**
 * A few real apps side by side over a stand-in for the wallpaper: what the settings show while
 * the look of icons is adjusted.
 */
@Composable
internal fun IconSamples(
    apps: List<AppEntry>,
    spec: GridSpec,
    repository: AppRepository,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SamplesShape)
            .background(KiriTheme.colors.previewBackdrop)
            // The blurred wallpaper, so that glass on top of it matches what is around it.
            .backdrop()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        for (row in apps.chunked(columns)) {
            Row(Modifier.fillMaxWidth()) {
                for (app in row) {
                    AppCell(
                        app = app,
                        spec = spec,
                        labelStyle = labelStyle,
                        labelHeight = 0.dp,
                        repository = repository,
                        interactive = false,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
