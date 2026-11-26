package io.github.johankrs.kiri.widgets.system

import android.appwidget.AppWidgetProviderInfo
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.GlassStyle
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.core.look.WidgetLook
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.PanePress
import io.github.johankrs.kiri.ui.theme.kiriGlass
import io.github.johankrs.kiri.ui.theme.panePress
import io.github.johankrs.kiri.ui.theme.rememberPanePress
import io.github.johankrs.kiri.ui.theme.spec
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * A widget of an installed app, filling [modifier]'s space.
 *
 * With [live] on it is the widget itself: it answers touches and changes as its app says. A
 * widget has only one such view, so everything else that shows it, the copy a finger carries or
 * a preview in the settings, turns [live] off and gets a picture of the live one instead.
 */
@Composable
fun SystemWidgetFace(content: ItemContent.AppWidget, live: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val system = remember(context) { SystemWidgets.of(context) }
    // Dressed like one of Kiri's own, when the person chose so for this widget: the pane of
    // glass, with its corners, goes around the widget here; the rest is done to its views.
    val look = LocalWidgetLooks.current[content.appWidgetId] ?: WidgetLook.AsMade
    val glass = look.glass
    // What is pressed in a dressed widget is lit up by its pane, out to the pane's edge.
    val press = rememberPanePress()
    val frame = if (glass != null) {
        Modifier
            .kiriGlass(glass, WidgetShape)
            .then(if (live) Modifier.panePress(press, glass.spec().content) else Modifier)
            .padding(look.marginDp.dp)
    } else {
        Modifier
    }
    if (live) {
        LiveWidget(content, system, look, press, modifier, frame)
    } else {
        WidgetPicture(content, system, modifier, frame)
    }
}

/** How each widget of another app is dressed, by the number of its copy. None: as its app made it. */
val LocalWidgetLooks = compositionLocalOf<Map<Int, WidgetLook>> { emptyMap() }

/**
 * Places a widget again where the copy `old`, which Android forgot, was shown. Null where
 * widgets cannot be placed (a preview).
 */
val LocalWidgetPlaceAgain = compositionLocalOf<((old: Int, info: AppWidgetProviderInfo) -> Unit)?> { null }

/** The corners of Kiri's own widgets, as they come. */
private val WidgetShape = RoundedCornerShape(28.dp)

@Composable
private fun LiveWidget(
    content: ItemContent.AppWidget,
    system: SystemWidgets,
    look: WidgetLook,
    press: PanePress,
    modifier: Modifier,
    frame: Modifier,
) {
    val context = LocalContext.current
    val id = content.appWidgetId
    // Asked again when apps change what they offer: a widget whose app was away comes back.
    val view = remember(id, context, system.revision) { system.viewFor(context, id) }
    if (view == null) {
        // Android forgot this copy (as after a restore on a new device), but its app still
        // offers the widget: a tap places it again, where it was.
        val placeAgain = LocalWidgetPlaceAgain.current
        val offer by produceState<AppWidgetProviderInfo?>(null, content.provider, system.revision, placeAgain) {
            value = if (placeAgain == null) null else system.offers().firstOrNull { it.provider.flattenToString() == content.provider }
        }
        val again = offer
        if (placeAgain != null && again != null) {
            PlaceAgainStandIn(appLabelOf(content), modifier) { placeAgain(id, again) }
        } else {
            WidgetStandIn(stringResource(R.string.widget_unavailable), modifier)
        }
        return
    }

    val dress = rememberWidgetDress(look)
    val inset = with(LocalDensity.current) { look.marginDp.dp.roundToPx() }
    DisposableEffect(view, dress, press, inset) {
        view.wear(dress, press, inset)
        onDispose { }
    }

    // What the widget looks like, kept for whoever has to draw a copy of it.
    val picture = rememberGraphicsLayer()
    DisposableEffect(id, picture) {
        system.pictures[id] = picture
        onDispose {
            if (system.pictures[id] === picture) system.pictures.remove(id)
        }
    }

    // The widget is told how much room it has. While its size is still changing, as when it is
    // made wider step by step, it is told once the size has stopped, not at every frame.
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val told = remember(view) { booleanArrayOf(false) }
    LaunchedEffect(view, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        if (told[0]) delay(SIZE_PAUSE_MS)
        told[0] = true
        with(density) {
            view.tellSize(size.width.toDp().value.roundToInt(), size.height.toDp().value.roundToInt())
        }
    }

    key(view) {
        AndroidView(
            // A view sits in one place at a time: it is taken out of where it was last shown.
            factory = {
                (view.parent as? ViewGroup)?.removeView(view)
                view
            },
            modifier = modifier
                .then(frame)
                .onSizeChanged { size = it }
                .drawWithContent {
                    picture.record { this@drawWithContent.drawContent() }
                    drawLayer(picture)
                },
        )
    }
}

/** The picture of a widget that is on show somewhere else, made to fit inside this space. */
@Composable
private fun WidgetPicture(content: ItemContent.AppWidget, system: SystemWidgets, modifier: Modifier, frame: Modifier) {
    val picture = system.pictures[content.appWidgetId]?.takeIf { !it.isReleased && it.size.width > 0 && it.size.height > 0 }
    if (picture == null) {
        WidgetStandIn(appLabelOf(content), modifier)
        return
    }
    Box(
        modifier.then(frame).drawBehind {
            if (picture.isReleased) return@drawBehind
            val fit = minOf(size.width / picture.size.width, size.height / picture.size.height)
            translate(
                left = (size.width - picture.size.width * fit) / 2f,
                top = (size.height - picture.size.height * fit) / 2f,
            ) {
                scale(fit, pivot = Offset.Zero) { drawLayer(picture) }
            }
        },
    )
}

/** A quiet pane of glass with a few words, where a widget cannot be shown. */
@Composable
private fun WidgetStandIn(text: String, modifier: Modifier) {
    val glass = GlassStyle.FUME
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.kiriGlass(glass, WidgetShape),
    ) {
        BasicText(
            text = text,
            style = KiriType.caption.copy(color = glass.spec().contentMuted, textAlign = TextAlign.Center),
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * Where a widget Android forgot used to be: the same quiet glass, with the name of its app and
 * a plus, and a tap places it again.
 */
@Composable
private fun PlaceAgainStandIn(appLabel: String, modifier: Modifier, onClick: () -> Unit) {
    val glass = GlassStyle.FUME
    val colors = glass.spec()
    val action = stringResource(R.string.widget_place_again)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .kiriGlass(glass, WidgetShape)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(12.dp),
    ) {
        GlyphIcon(Glyph.PLUS, colors.content, Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = appLabel,
            style = KiriType.body.copy(color = colors.content, textAlign = TextAlign.Center),
        )
        BasicText(
            text = action,
            style = KiriType.caption.copy(color = colors.contentMuted, textAlign = TextAlign.Center),
        )
    }
}

/** The name of the app a widget belongs to, or of its package when the app is gone. */
@Composable
private fun appLabelOf(content: ItemContent.AppWidget): String {
    val context = LocalContext.current
    return remember(content.packageName) {
        runCatching {
            val packages = context.packageManager
            packages.getApplicationLabel(packages.getApplicationInfo(content.packageName, 0)).toString()
        }.getOrDefault(content.packageName)
    }
}

/** How long the size of a widget has to stay the same before the widget is told about it. */
private const val SIZE_PAUSE_MS = 220L
