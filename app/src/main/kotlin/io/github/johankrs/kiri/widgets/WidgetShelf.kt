package io.github.johankrs.kiri.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import androidx.compose.runtime.Immutable
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.WidgetCells
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.core.layout.GridSize
import io.github.johankrs.kiri.core.layout.ItemContent
import io.github.johankrs.kiri.widgets.system.SystemWidgets
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One widget that can be put on the home screen. Kiri's own widgets and the ones other apps
 * offer stand side by side here: exactly one of [kiri] and [system] is set.
 */
@Immutable
class WidgetOffer(
    /** Unlike any other offer, and the same every time the list is read. */
    val key: String,
    val label: String,
    /** A line from the app about what the widget is for, when it wrote one. */
    val description: String?,
    val kiri: KiriWidget? = null,
    val system: AppWidgetProviderInfo? = null,
)

/** An app and the widgets it offers. Kiri is one of them, for its own widgets. */
@Immutable
class WidgetApp(
    val key: String,
    val label: String,
    val packageName: String,
    val offers: List<WidgetOffer>,
)

/** What is worth saying about a widget that is on the home screen. */
@Immutable
class WidgetFacts(
    val label: String,
    val appLabel: String,
    val packageName: String,
    /** Whether it has settings of its own that can be opened now. */
    val adjustable: Boolean,
)

/**
 * The home grid as a widget meets it: how many cells there are and how large they are, in dp,
 * with the room between cells side by side ([gapDp]) and one under the other ([rowGapDp]).
 */
@Immutable
class HomeCells(
    val grid: GridSize,
    val cellWidthDp: Float,
    val cellHeightDp: Float,
    val gapDp: Float,
    val rowGapDp: Float = gapDp,
)

/**
 * Every app that offers widgets, by name, each with its widgets by name. Kiri stands among
 * them like any other app.
 */
suspend fun loadWidgetApps(context: Context, kiri: KiriWidgets, system: SystemWidgets): List<WidgetApp> =
    withContext(Dispatchers.Default) {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val packages = context.packageManager
        val launcherApps = context.getSystemService(LauncherApps::class.java)

        val own = WidgetApp(
            key = context.packageName,
            label = context.getString(R.string.app_name),
            packageName = context.packageName,
            offers = kiri.all.map { widget ->
                WidgetOffer(key = "kiri:${widget.spec.id}", label = context.getString(widget.titleRes), description = null, kiri = widget)
            },
        )

        val others = system.offers()
            .groupBy { it.provider.packageName to it.profile }
            .map { (owner, infos) ->
                val (packageName, user) = owner
                val appLabel = runCatching {
                    launcherApps?.getApplicationInfo(packageName, 0, user)?.let { packages.getApplicationLabel(it).toString() }
                }.getOrNull() ?: packageName
                WidgetApp(
                    key = if (user == Process.myUserHandle()) packageName else "$packageName#${user.hashCode()}",
                    label = appLabel,
                    packageName = packageName,
                    offers = infos.map { info ->
                        WidgetOffer(
                            key = "app:${info.provider.flattenToShortString()}#${user.hashCode()}",
                            label = runCatching { info.loadLabel(packages) }.getOrNull()?.takeIf { it.isNotBlank() } ?: appLabel,
                            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                runCatching { info.loadDescription(context)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            },
                            system = info,
                        )
                    }.sortedWith(compareBy(collator) { it.label }),
                )
            }

        (others + own).filter { it.offers.isNotEmpty() }.sortedWith(compareBy<WidgetApp, String>(collator) { it.label }.thenBy { it.key })
    }

/** The size [this] starts with when it is put on the home screen. */
fun WidgetOffer.startSpan(cells: HomeCells, density: Float): CellSpan =
    kiri?.spec?.defaultSize?.clampedTo(cells.grid) ?: system?.startSpan(cells, density) ?: CellSpan.Single

/**
 * The size a widget of another app starts with: the cells its app says it was made for, or as
 * many as hold the size it asks for. Android gives those sizes in pixels.
 */
fun AppWidgetProviderInfo.startSpan(cells: HomeCells, density: Float): CellSpan {
    val asked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetCellWidth > 0 && targetCellHeight > 0) {
        CellSpan(targetCellWidth, targetCellHeight)
    } else {
        null
    }
    return WidgetCells.startSpan(
        targetCells = asked,
        minWidthDp = minWidth / density,
        minHeightDp = minHeight / density,
        cellWidthDp = cells.cellWidthDp,
        cellHeightDp = cells.cellHeightDp,
        gapDp = cells.gapDp,
        rowGapDp = cells.rowGapDp,
        grid = cells.grid,
    )
}

/**
 * The smallest and the largest size the widget shown by [content] takes on the home screen.
 * One of Kiri's own says so itself. Another app's goes from the least it says it can be drawn
 * at up to the whole page: how large a widget may get is the person's call, not the app's.
 */
fun sizeLimits(
    content: ItemContent,
    kiri: KiriWidgets,
    system: SystemWidgets,
    cells: HomeCells,
    density: Float,
): Pair<CellSpan, CellSpan>? = when (content) {
    is ItemContent.App, is ItemContent.Folder -> null

    is ItemContent.Widget -> kiri.byId(content.widgetId)?.spec?.let { spec ->
        spec.minSize.clampedTo(cells.grid) to spec.maxSize.clampedTo(cells.grid)
    }

    is ItemContent.AppWidget -> {
        val whole = CellSpan(cells.grid.columns, cells.grid.rows)
        val info = system.info(content.appWidgetId)
        if (info == null) {
            CellSpan.Single to whole
        } else {
            val leastWidth = if (info.minResizeWidth > 0) minOf(info.minResizeWidth, info.minWidth) else info.minWidth
            val leastHeight = if (info.minResizeHeight > 0) minOf(info.minResizeHeight, info.minHeight) else info.minHeight
            CellSpan(
                columns = WidgetCells.holding(leastWidth / density, cells.cellWidthDp, cells.gapDp),
                rows = WidgetCells.holding(leastHeight / density, cells.cellHeightDp, cells.rowGapDp),
            ).clampedTo(cells.grid) to whole
        }
    }
}

/** What to call the widget shown by [content] and whose it is. Null for what is not a widget. */
fun widgetFacts(context: Context, content: ItemContent, kiri: KiriWidgets, system: SystemWidgets): WidgetFacts? =
    when (content) {
        is ItemContent.App, is ItemContent.Folder -> null

        is ItemContent.Widget -> kiri.byId(content.widgetId)?.let { widget ->
            WidgetFacts(
                label = context.getString(widget.titleRes),
                appLabel = context.getString(R.string.app_name),
                packageName = context.packageName,
                adjustable = widget.hasSettings,
            )
        }

        is ItemContent.AppWidget -> {
            val packages = context.packageManager
            val info = system.info(content.appWidgetId)
            val appLabel = runCatching {
                packages.getApplicationLabel(packages.getApplicationInfo(content.packageName, 0)).toString()
            }.getOrDefault(content.packageName)
            WidgetFacts(
                label = info?.let { runCatching { it.loadLabel(packages) }.getOrNull() }?.takeIf { it.isNotBlank() } ?: appLabel,
                appLabel = appLabel,
                packageName = content.packageName,
                adjustable = info != null && system.canSetUpAgain(info),
            )
        }
    }
