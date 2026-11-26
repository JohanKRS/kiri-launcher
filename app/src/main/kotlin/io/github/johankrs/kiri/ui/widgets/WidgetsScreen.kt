package io.github.johankrs.kiri.ui.widgets

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.johankrs.kiri.MainActivity
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.HomeItem
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.data.AppRepository
import io.github.johankrs.kiri.ui.AppIcon
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import io.github.johankrs.kiri.ui.settings.SectionTitle
import io.github.johankrs.kiri.ui.settings.SettingsScaffold
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import io.github.johankrs.kiri.ui.theme.LocalBackdrop
import io.github.johankrs.kiri.widgets.HomeCells
import io.github.johankrs.kiri.widgets.KiriWidget
import io.github.johankrs.kiri.widgets.KiriWidgets
import io.github.johankrs.kiri.widgets.WidgetApp
import io.github.johankrs.kiri.widgets.WidgetFacts
import io.github.johankrs.kiri.widgets.WidgetOffer
import io.github.johankrs.kiri.widgets.loadWidgetApps
import io.github.johankrs.kiri.widgets.startSpan
import io.github.johankrs.kiri.widgets.system.SystemWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A widget that is on the home screen: its item there, and what to call it. */
@Immutable
class PlacedWidget(val item: HomeItem, val facts: WidgetFacts)

private val BlockCorner = 22.dp
private val AppIconSize = 36.dp
private val PreviewWidth = 104.dp
private val PreviewHeight = 68.dp
private val PreviewShape = RoundedCornerShape(14.dp)

/** How large one cell is taken to be when one of Kiri's own widgets is drawn as a preview. */
private val PreviewCellWidth = 76.dp
private val PreviewCellHeight = 84.dp

private val OpenedSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })

/**
 * The screen of the widgets, a place of its own next to the settings: at the top the widgets
 * that are on the home screen, to go to, adjust or take away; below, every widget that can be
 * put there, under the app it belongs to. Kiri's own are listed under Kiri like any other's.
 *
 * Apps are closed until tapped, so the list stays short. [onAdd] is told which widget was
 * picked; [cells] tells how large the cells of the home screen are, for the sizes shown.
 */
@Composable
fun WidgetsScreen(
    placed: List<PlacedWidget>,
    apps: List<AppEntry>,
    repository: AppRepository,
    kiri: KiriWidgets,
    system: SystemWidgets,
    cells: () -> HomeCells,
    onAdd: (WidgetOffer) -> Unit,
    onShow: (HomeItem) -> Unit,
    onAdjust: (HomeItem) -> Unit,
    onRemove: (HomeItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = KiriTheme.colors
    val density = LocalDensity.current.density
    val muted = KiriType.caption.copy(color = colors.onSurfaceMuted)

    // Read again when an app is installed or removed, or changes the widgets it offers.
    val shelf by produceState<List<WidgetApp>?>(null, system.revision, repository.appChanges) {
        value = loadWidgetApps(context, kiri, system)
    }
    DisposableEffect(system) {
        onDispose { system.clearPreviews() }
    }
    var opened by rememberSaveable(stateSaver = OpenedSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val launchers = remember(apps) { apps.associateBy { it.packageName } }
    val icon: @Composable (String) -> Unit = { packageName -> PackageIcon(packageName, launchers, repository, AppIconSize) }
    // Which of Kiri's own widgets are placed: each of those exists only once.
    val placedOwn = remember(placed) {
        placed.mapNotNull { one -> (one.item.content as? ItemContent.Widget)?.let { it.widgetId to one.item } }.toMap()
    }

    SettingsScaffold(title = stringResource(R.string.widgets_title), onBack = onBack) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "placed-title") { SectionTitle(stringResource(R.string.widgets_on_home)) }
            if (placed.isEmpty()) {
                item(key = "placed-none") {
                    BasicText(
                        text = stringResource(R.string.widgets_none_on_home),
                        style = muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .blockPart(first = true, last = true)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
            itemsIndexed(placed, key = { _, one -> "placed:${one.item.id}" }) { index, one ->
                PlacedRow(
                    widget = one,
                    icon = icon,
                    onShow = { onShow(one.item) },
                    onAdjust = if (one.facts.adjustable) { { onAdjust(one.item) } } else null,
                    onRemove = { onRemove(one.item) },
                    modifier = Modifier
                        .animateItem()
                        .blockPart(first = index == 0, last = index == placed.lastIndex),
                )
            }

            item(key = "add-title") { SectionTitle(stringResource(R.string.widgets_add)) }
            val offered = shelf
            if (offered != null && offered.isEmpty()) {
                item(key = "add-none") {
                    BasicText(text = stringResource(R.string.widgets_none_offered), style = muted, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
            offered.orEmpty().forEachIndexed { index, app ->
                val isOpen = app.key in opened
                val lastApp = index == offered.orEmpty().lastIndex
                item(key = "app:${app.key}") {
                    AppRow(
                        app = app,
                        open = isOpen,
                        icon = icon,
                        onClick = { opened = if (isOpen) opened - app.key else opened + app.key },
                        modifier = Modifier
                            .animateItem()
                            .blockPart(first = index == 0, last = lastApp && !isOpen),
                    )
                }
                if (isOpen) {
                    itemsIndexed(app.offers, key = { _, offer -> "offer:${app.key}:${offer.key}" }) { place, offer ->
                        val own = offer.kiri?.let { placedOwn[it.spec.id] }
                        OfferRow(
                            offer = offer,
                            span = remember(offer.key) { offer.startSpan(cells(), density) },
                            system = system,
                            fallbackIcon = { icon(app.packageName) },
                            alreadyPlaced = own != null,
                            onClick = { if (own != null) onShow(own) else onAdd(offer) },
                            modifier = Modifier
                                .animateItem()
                                .blockPart(first = false, last = lastApp && place == app.offers.lastIndex, seam = place > 0),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of a block that is laid out row by row, so that a long list only builds the rows in
 * view: together the rows look like the single card the settings use for a group. The [first]
 * and [last] rows carry the round corners, and a faint line parts a row from the one above.
 */
@Composable
private fun Modifier.blockPart(first: Boolean, last: Boolean, seam: Boolean = !first): Modifier {
    val colors = KiriTheme.colors
    val top = if (first) BlockCorner else 0.dp
    val bottom = if (last) BlockCorner else 0.dp
    return this
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(colors.card)
        .drawWithContent {
            drawContent()
            if (seam) {
                drawLine(
                    color = colors.divider,
                    start = Offset(18.dp.toPx(), 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
}

/** A widget on the home screen. A tap goes to it; the signs at the end adjust it or take it away. */
@Composable
private fun PlacedRow(
    widget: PlacedWidget,
    icon: @Composable (String) -> Unit,
    onShow: () -> Unit,
    onAdjust: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    val facts = widget.facts
    val item = widget.item
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onShow)
            .heightIn(min = 58.dp)
            .padding(start = 14.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
    ) {
        icon(facts.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(text = facts.label, style = KiriType.body.copy(color = colors.onSurface))
            BasicText(
                text = stringResource(
                    R.string.widgets_place_summary,
                    facts.appLabel,
                    item.span.columns,
                    item.span.rows,
                    item.position.page + 1,
                ),
                style = KiriType.caption.copy(color = colors.onSurfaceMuted),
            )
        }
        if (onAdjust != null) {
            SignButton(Glyph.TUNE, stringResource(R.string.widgets_adjust_action, facts.label), onAdjust)
        }
        SignButton(Glyph.CLOSE, stringResource(R.string.widgets_remove_action, facts.label), onRemove)
    }
}

/** A sign to tap, with no background of its own. */
@Composable
private fun SignButton(glyph: Glyph, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        GlyphIcon(glyph, KiriTheme.colors.onSurfaceMuted)
    }
}

/** An app that offers widgets. A tap opens its widgets right below, and closes them again. */
@Composable
private fun AppRow(
    app: WidgetApp,
    open: Boolean,
    icon: @Composable (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    val turn = animateFloatAsState(if (open) -90f else 90f, spring(stiffness = Spring.StiffnessMediumLow), label = "app")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
    ) {
        icon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(text = app.label, style = KiriType.body.copy(color = colors.onSurface))
            BasicText(
                text = pluralStringResource(R.plurals.widgets_count, app.offers.size, app.offers.size),
                style = KiriType.caption.copy(color = colors.onSurfaceMuted),
            )
        }
        Spacer(Modifier.width(12.dp))
        // Points down while closed and up while open.
        GlyphIcon(Glyph.FORWARD, colors.onSurfaceMuted, Modifier.size(18.dp).graphicsLayer { rotationZ = turn.value })
    }
}

/** One widget to pick: what it looks like, its name and how many cells it starts with. */
@Composable
private fun OfferRow(
    offer: WidgetOffer,
    span: CellSpan,
    system: SystemWidgets,
    fallbackIcon: @Composable () -> Unit,
    alreadyPlaced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiriTheme.colors
    val action = if (alreadyPlaced) offer.label else stringResource(R.string.widgets_add_action, offer.label)
    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(PreviewWidth, PreviewHeight)
                    .clip(PreviewShape)
                    .background(colors.divider)
                    .clearAndSetSemantics { },
            ) {
                val own = offer.kiri
                if (own != null) OwnPreview(own, span) else AppPreview(offer, system, fallbackIcon)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(text = offer.label, style = KiriType.body.copy(color = colors.onSurface))
                BasicText(
                    text = if (alreadyPlaced) {
                        stringResource(R.string.widgets_already_on_home)
                    } else {
                        stringResource(R.string.widgets_size, span.columns, span.rows)
                    },
                    style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                )
                offer.description?.let { text ->
                    BasicText(text = text, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
                }
            }
            Spacer(Modifier.width(12.dp))
            GlyphIcon(
                glyph = Glyph.PLUS,
                color = if (alreadyPlaced) colors.onSurfaceMuted.copy(alpha = 0.35f) else colors.onSurface,
            )
        }
        // The whole row is the button, over the preview too: a preview is only to be looked at.
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = action },
        )
    }
}

/** The picture an app made of its widget, or the app's icon when it made none. */
@Composable
private fun AppPreview(offer: WidgetOffer, system: SystemWidgets, fallbackIcon: @Composable () -> Unit) {
    val info = offer.system ?: return
    val density = LocalDensity.current
    val widthPx = with(density) { (PreviewWidth - 12.dp).roundToPx() }
    val heightPx = with(density) { (PreviewHeight - 12.dp).roundToPx() }
    // Absent while it is read, null once it is known that there is none.
    val picture by produceState<Result<ImageBitmap?>?>(null, offer.key, widthPx, heightPx) {
        value = Result.success(system.preview(info, widthPx, heightPx))
    }
    val read = picture ?: return
    val bitmap = read.getOrNull()
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(6.dp),
        )
    } else {
        fallbackIcon()
    }
}

/** One of Kiri's own widgets, drawn for real at the size it starts with and made small. */
@Composable
private fun OwnPreview(widget: KiriWidget, span: CellSpan) {
    val width = PreviewCellWidth * span.columns
    val height = PreviewCellHeight * span.rows
    val fit = minOf((PreviewWidth - 12.dp) / width, (PreviewHeight - 12.dp) / height)
    // On this sheet its glass is only tinted: showing the wallpaper would cut a hole in the sheet.
    CompositionLocalProvider(LocalBackdrop provides null) {
        Box(
            Modifier
                .requiredSize(width, height)
                .graphicsLayer {
                    scaleX = fit
                    scaleY = fit
                },
        ) {
            widget.Content(span, Modifier.fillMaxSize())
        }
    }
}

/**
 * The icon of the app [packageName], dressed like every icon in Kiri when the app can be opened
 * from the drawer. An app that only offers widgets shows the icon it came with.
 */
@Composable
internal fun PackageIcon(
    packageName: String,
    launchers: Map<String, AppEntry>,
    repository: AppRepository,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entry = remember(packageName, launchers) { launchers[packageName] ?: context.ownEntry(packageName) }
    if (entry != null) {
        AppIcon(entry, repository, size, modifier)
        return
    }
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val own by produceState<ImageBitmap?>(null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.size(size)) {
        own?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28))) }
    }
}

/** Kiri is not in its own drawer, so its entry is made here, for its icon. */
private fun Context.ownEntry(packageName: String): AppEntry? =
    if (packageName == this.packageName) {
        AppEntry(packageName, MainActivity::class.java.name, getString(R.string.app_name))
    } else {
        null
    }
