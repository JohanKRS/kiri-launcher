package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.math.ceil
import kotlin.math.sqrt

/** What a closed folder sits on: one of the glasses, or nothing at all. */
enum class FolderGround(val key: String, val glass: GlassStyle?) {
    FUME("fume", GlassStyle.FUME),
    CRISTAL("cristal", GlassStyle.CRISTAL),
    NEVOA("nevoa", GlassStyle.NEVOA),

    /** Only the small icons, straight on the wallpaper: the glass comes when the folder opens. */
    NONE("none", null),
    ;

    companion object {
        fun fromKey(key: String?): FolderGround? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How every folder looks, closed and open. One folder can still have a ground of its own (see
 * `ItemContent.Folder.ground`).
 */
data class FolderLook(
    val ground: FolderGround = FolderGround.FUME,
    /** How much tint covers the glass of a closed folder, in percent. */
    val groundOpacity: Int = VIVID_OPACITY,
    /** The outline of a closed folder; null for the shape the icons have. */
    val shape: IconShape? = null,
    /** At most this many small icons in a closed folder: one of [MINIS]. */
    val minis: Int = DEFAULT_MINIS,
    /** Room between the edge of a closed folder and its icons, a level of [LEVELS]. */
    val margin: Int = 2,
    /** Room between the small icons, a level of [LEVELS]. */
    val spacing: Int = 2,
    val showName: Boolean = true,
    val panelGlass: GlassStyle = GlassStyle.FUME,
    val panelOpacity: Int = VIVID_OPACITY,
    /** Columns of the open folder; [BEST_FIT] lets [FolderGrid] choose. */
    val columns: Int = BEST_FIT,
    /** Room between the apps of an open folder, a level of [LEVELS]. */
    val panelSpacing: Int = 2,
    /** Room between the edge of an open folder and its apps, a level of [LEVELS]. */
    val panelMargin: Int = 2,
    val panelNames: Boolean = true,
    val panelTitle: Boolean = true,
    /** How much the home screen darkens behind an open folder, a level of [DIM]. */
    val dim: Int = 1,
) {
    /** The room around the small icons, as a share of the side of the folder. */
    val marginShare: Float get() = MARGINS[margin.coerceIn(LEVELS)]

    /** The room between two small icons, as a share of the side of the folder. */
    val spacingShare: Float get() = SPACINGS[spacing.coerceIn(LEVELS)]

    /** The room between two icons side by side in an open folder, in dp. */
    val panelGapDp: Float get() = PANEL_GAPS[panelSpacing.coerceIn(LEVELS)]

    /** The room between a row of an open folder (its names included) and the next, in dp. */
    val panelRowGapDp: Float get() = PANEL_ROW_GAPS[panelSpacing.coerceIn(LEVELS)]

    /** The room between the edge of an open folder and its apps (and its name), in dp. */
    val panelMarginDp: Float get() = PANEL_MARGINS[panelMargin.coerceIn(LEVELS)]

    /** How dark the home screen gets behind an open folder: the alpha of black over it. */
    val dimAlpha: Float get() = dim.coerceIn(DIM) * 0.11f

    fun withGroundOpacity(percent: Int): FolderLook = copy(groundOpacity = percent.coerceIn(OPACITY))
    fun withPanelOpacity(percent: Int): FolderLook = copy(panelOpacity = percent.coerceIn(OPACITY))

    companion object {
        /** The tint the menus use over vivid glass; folders match them. */
        const val VIVID_OPACITY = 30
        const val BEST_FIT = 0
        const val DEFAULT_MINIS = 9
        val MINIS = listOf(4, 9, 16)
        val OPACITY = 5..95
        const val OPACITY_STEP = 5
        val LEVELS = 0..5
        val DIM = 0..4

        private val MARGINS = floatArrayOf(0f, 0.06f, 0.1f, 0.14f, 0.18f, 0.22f)
        private val SPACINGS = floatArrayOf(0f, 0.03f, 0.05f, 0.07f, 0.09f, 0.11f)
        private val PANEL_GAPS = floatArrayOf(8f, 14f, 20f, 26f, 32f, 40f)
        private val PANEL_ROW_GAPS = floatArrayOf(8f, 11f, 14f, 18f, 22f, 26f)
        private val PANEL_MARGINS = floatArrayOf(8f, 12f, 18f, 24f, 30f, 36f)

        const val KEY_GROUND = "folders.ground"
        const val KEY_GROUND_OPACITY = "folders.groundOpacity"
        const val KEY_SHAPE = "folders.shape"
        const val KEY_MINIS = "folders.minis"
        const val KEY_MARGIN = "folders.margin"
        const val KEY_SPACING = "folders.spacing"
        const val KEY_SHOW_NAME = "folders.showName"
        const val KEY_PANEL_GLASS = "folders.panelGlass"
        const val KEY_PANEL_OPACITY = "folders.panelOpacity"
        const val KEY_COLUMNS = "folders.columns"
        const val KEY_PANEL_SPACING = "folders.panelSpacing"
        const val KEY_PANEL_MARGIN = "folders.panelMargin"
        const val KEY_PANEL_NAMES = "folders.panelNames"
        const val KEY_PANEL_TITLE = "folders.panelTitle"
        const val KEY_DIM = "folders.dim"

        /** What [KEY_SHAPE] holds when the folders take the shape of the icons. */
        const val SAME_SHAPE = "icons"

        /** Builds the look from saved values; anything missing or unknown is the default. */
        fun fromStored(
            string: (key: String) -> String?,
            int: (key: String) -> Int?,
            boolean: (key: String) -> Boolean?,
        ): FolderLook {
            val default = FolderLook()
            return FolderLook(
                ground = FolderGround.fromKey(string(KEY_GROUND)) ?: default.ground,
                groundOpacity = (int(KEY_GROUND_OPACITY) ?: default.groundOpacity).coerceIn(OPACITY),
                shape = string(KEY_SHAPE)?.let { IconShape.fromKey(it) },
                minis = int(KEY_MINIS)?.takeIf { it in MINIS } ?: default.minis,
                margin = (int(KEY_MARGIN) ?: default.margin).coerceIn(LEVELS),
                spacing = (int(KEY_SPACING) ?: default.spacing).coerceIn(LEVELS),
                showName = boolean(KEY_SHOW_NAME) ?: default.showName,
                panelGlass = string(KEY_PANEL_GLASS)?.let { GlassStyle.fromKey(it) } ?: default.panelGlass,
                panelOpacity = (int(KEY_PANEL_OPACITY) ?: default.panelOpacity).coerceIn(OPACITY),
                columns = (int(KEY_COLUMNS) ?: default.columns).let { if (it < 2) BEST_FIT else it },
                panelSpacing = (int(KEY_PANEL_SPACING) ?: default.panelSpacing).coerceIn(LEVELS),
                panelMargin = (int(KEY_PANEL_MARGIN) ?: default.panelMargin).coerceIn(LEVELS),
                panelNames = boolean(KEY_PANEL_NAMES) ?: default.panelNames,
                panelTitle = boolean(KEY_PANEL_TITLE) ?: default.panelTitle,
                dim = (int(KEY_DIM) ?: default.dim).coerceIn(DIM),
            )
        }
    }
}

/**
 * Where the apps of a folder go. The same rule holds closed and open: as many columns as the
 * square root of the number of apps, rounded up, and the last row centred. So opening a folder
 * only has to grow each small icon into its own place.
 */
object FolderGrid {
    /** Columns for [count] apps, never more than [most] (the columns of the home screen). */
    fun columns(count: Int, most: Int = Int.MAX_VALUE, chosen: Int = FolderLook.BEST_FIT): Int {
        val apps = count.coerceAtLeast(1)
        val wanted = if (chosen >= 2) chosen else ceil(sqrt(apps.toDouble())).toInt()
        return wanted.coerceAtMost(most.coerceAtLeast(1)).coerceAtMost(apps).coerceAtLeast(1)
    }

    fun rows(count: Int, columns: Int): Int = (count.coerceAtLeast(1) + columns - 1) / columns

    /**
     * The column, counted in cells and possibly halfway between two, and the row of the app at
     * [index] among [count] apps in [columns] columns. The last row is centred.
     */
    fun cell(index: Int, count: Int, columns: Int): Pair<Float, Int> {
        val row = index / columns
        val rows = rows(count, columns)
        val inRow = if (row == rows - 1) count - row * columns else columns
        return ((columns - inRow) / 2f + index % columns) to row
    }

    /** One small icon of a closed folder: its corner and side, as shares of the folder's side. */
    data class Slot(val x: Float, val y: Float, val size: Float)

    /**
     * The small icons of a closed folder of [count] apps. When there are more apps than
     * [FolderLook.minis], the last place stands for the rest: [more] says how many.
     */
    class Closed(val slots: List<Slot>, val more: Int) {
        /** How many apps are drawn as small icons. */
        val drawn: Int get() = if (more > 0) slots.size - 1 else slots.size
    }

    fun closed(count: Int, look: FolderLook): Closed {
        val shown = count.coerceIn(1, look.minis.coerceAtLeast(1))
        val columns = columns(shown)
        val rows = rows(shown, columns)
        val margin = look.marginShare
        val gap = look.spacingShare
        val inner = 1f - 2f * margin
        val side = (inner - (columns - 1) * gap) / columns
        val top = margin + (inner - (rows * side + (rows - 1) * gap)) / 2f
        val slots = List(shown) { index ->
            val (column, row) = cell(index, shown, columns)
            Slot(x = margin + column * (side + gap), y = top + row * (side + gap), size = side)
        }
        val more = if (count > shown) count - (shown - 1) else 0
        return Closed(slots, more)
    }
}
