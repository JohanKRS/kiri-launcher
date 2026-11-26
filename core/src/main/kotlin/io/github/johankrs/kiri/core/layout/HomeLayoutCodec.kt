package io.github.johankrs.kiri.core.layout

import io.github.johankrs.kiri.core.look.FolderGround

/**
 * Turns a home layout into text and back, so the whole layout is saved as a single setting.
 *
 * One line per item, fields separated by tabs:
 * `id, page, column, row, span columns, span rows, kind, payload`.
 * Lines that cannot be understood are skipped, so one bad entry never costs the whole layout.
 *
 * Before the items, one line says how many pages there are and which is the main one:
 * `pages, count, main page`. Older versions skip it as a line they do not understand, and a
 * layout without it has as many pages as its items use. Then, when known, the grid the items
 * were arranged on: `grid, columns, rows`, skipped the same way.
 *
 * A folder's payload is its id, its own ground (empty when it follows all folders), its name
 * and its apps, separated by the unit separator; the apps among themselves by the record
 * separator. Names never hold either (see [cleanName]).
 */
object HomeLayoutCodec {
    private const val HEADER = "kiri-home-1"
    private const val KIND_APP = "app"
    private const val KIND_WIDGET = "widget"
    private const val KIND_FOLDER = "folder"
    private const val FIELD = '\u001F'
    private const val APP = '\u001E'
    private const val KIND_APP_WIDGET = "appwidget"
    private const val PAGES = "pages"
    private const val GRID = "grid"

    fun encode(layout: HomeLayout): String = buildString {
        append(HEADER)
        append('\n')
        append(listOf(PAGES, layout.pageCount, layout.startPage).joinToString("\t"))
        layout.grid?.let { grid ->
            append('\n')
            append(listOf(GRID, grid.columns, grid.rows).joinToString("\t"))
        }
        for (item in layout.items) {
            val (kind, payload) = when (val content = item.content) {
                is ItemContent.App -> KIND_APP to content.appKey
                is ItemContent.Widget -> KIND_WIDGET to content.widgetId
                is ItemContent.Folder -> KIND_FOLDER to listOf(
                    content.id,
                    content.ground?.key.orEmpty(),
                    cleanName(content.name),
                    content.appKeys.joinToString(APP.toString()),
                ).joinToString(FIELD.toString())
                is ItemContent.AppWidget -> KIND_APP_WIDGET to "${content.appWidgetId}:${content.provider}"
            }
            append('\n')
            append(
                listOf(
                    item.id,
                    item.position.page,
                    item.position.column,
                    item.position.row,
                    item.span.columns,
                    item.span.rows,
                    kind,
                    payload,
                ).joinToString("\t"),
            )
        }
    }

    /** Returns null when [text] is absent or was not written by [encode]. */
    fun decode(text: String?): HomeLayout? {
        val lines = text?.split('\n') ?: return null
        if (lines.firstOrNull() != HEADER) return null

        val items = mutableListOf<HomeItem>()
        val seenIds = mutableSetOf<Long>()
        var pages = 0
        var main = 0
        var grid: GridSize? = null
        for (line in lines.drop(1)) {
            if (line.startsWith(GRID + "\t")) {
                val fields = line.split('\t')
                val columns = fields.getOrNull(1)?.toIntOrNull()
                val rows = fields.getOrNull(2)?.toIntOrNull()
                if (columns != null && rows != null && columns in 1..MAX_STORED_CELLS && rows in 1..MAX_STORED_CELLS) {
                    grid = GridSize(columns, rows)
                }
                continue
            }
            if (line.startsWith(PAGES + "\t")) {
                val fields = line.split('\t')
                pages = fields.getOrNull(1)?.toIntOrNull()?.coerceIn(0, MAX_STORED_PAGES) ?: 0
                main = fields.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                continue
            }
            val item = decodeItem(line) ?: continue
            if (seenIds.add(item.id)) items += item
        }
        val layout = HomeLayout(items, pages, grid = grid)
        return layout.copy(pages = layout.pageCount, mainPage = main.coerceIn(0, layout.pageCount - 1))
    }

    /** More pages than anyone keeps: a number past it is a mistake, not a choice. */
    private const val MAX_STORED_PAGES = 99

    /** More columns or rows than any grid has. */
    private const val MAX_STORED_CELLS = 99

    private fun decodeItem(line: String): HomeItem? {
        val fields = line.split('\t', limit = 8)
        if (fields.size != 8) return null

        val id = fields[0].toLongOrNull() ?: return null
        val numbers = fields.subList(1, 6).map { it.toIntOrNull() ?: return null }
        val (page, column, row, spanColumns, spanRows) = numbers
        if (page < 0 || column < 0 || row < 0 || spanColumns < 1 || spanRows < 1) return null

        val content = when (fields[6]) {
            KIND_APP -> ItemContent.App(fields[7]).takeIf { fields[7].isNotBlank() }
            KIND_WIDGET -> ItemContent.Widget(fields[7]).takeIf { fields[7].isNotBlank() }
            KIND_FOLDER -> decodeFolder(fields[7])
            KIND_APP_WIDGET -> decodeAppWidget(fields[7])
            else -> null
        } ?: return null

        return HomeItem(id, CellPosition(page, column, row), content, CellSpan(spanColumns, spanRows))
    }

    private fun decodeFolder(payload: String): ItemContent.Folder? {
        val parts = payload.split(FIELD, limit = 4)
        if (parts.size != 4 || parts[0].isBlank()) return null
        val apps = parts[3].split(APP).filter { it.isNotBlank() }.distinct()
        if (apps.isEmpty()) return null
        return ItemContent.Folder(
            id = parts[0],
            name = parts[2],
            appKeys = apps,
            ground = FolderGround.fromKey(parts[1].ifEmpty { null }),
        )
    }

    /** `number:package/class`, as written by [encode]. */
    private fun decodeAppWidget(payload: String): ItemContent.AppWidget? {
        val id = payload.substringBefore(':', missingDelimiterValue = "").toIntOrNull() ?: return null
        val provider = payload.substringAfter(':', missingDelimiterValue = "")
        return ItemContent.AppWidget(id, provider).takeIf { provider.isNotBlank() }
    }
}
