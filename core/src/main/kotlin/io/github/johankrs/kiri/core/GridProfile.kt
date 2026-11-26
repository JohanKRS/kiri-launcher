package io.github.johankrs.kiri.core

/** A place where a grid of icons is shown. */
enum class Surface {
    HOME,
    DRAWER,
}

/**
 * Identifies one independently adjustable grid: the same surface has its own settings on a
 * phone in portrait, a phone in landscape, a tablet and a TV.
 */
data class GridProfile(
    val surface: Surface,
    val formFactor: FormFactor,
    val landscape: Boolean,
) {
    /** Stable id used as the prefix of this profile's stored settings. */
    val id: String = buildString {
        append(surface.name.lowercase())
        append('.')
        append(formFactor.name.lowercase())
        append('.')
        append(if (landscape) "landscape" else "portrait")
    }
}

/** The numeric values of a [GridSpec] that a person can adjust, each with its allowed range. */
enum class GridField(val key: String, val range: IntRange) {
    COLUMNS("columns", GridSpec.COLUMNS),
    ROWS("rows", GridSpec.ROWS),
    MARGIN_HORIZONTAL("marginHorizontal", GridSpec.MARGIN_DP),
    MARGIN_TOP("marginTop", GridSpec.MARGIN_DP),
    MARGIN_BOTTOM("marginBottom", GridSpec.MARGIN_DP),
    GAP("gap", GridSpec.GAP_DP),
    ICON_SIZE("iconSize", GridSpec.ICON_SIZE_DP),

    /** On the home screen, the room between rows when it is not the room between columns. */
    ROW_GAP("rowGap", GridSpec.GAP_DP);

    fun get(spec: GridSpec): Int = when (this) {
        COLUMNS -> spec.columns
        ROWS -> spec.rows
        MARGIN_HORIZONTAL -> spec.marginHorizontalDp
        MARGIN_TOP -> spec.marginTopDp
        MARGIN_BOTTOM -> spec.marginBottomDp
        GAP -> spec.gapDp
        ICON_SIZE -> spec.iconSizeDp
        ROW_GAP -> spec.rowGapDp
    }

    /** Returns [spec] with this field changed to [value], kept inside the allowed range. */
    fun set(spec: GridSpec, value: Int): GridSpec {
        val safe = value.coerceIn(range)
        return when (this) {
            COLUMNS -> spec.copy(columns = safe)
            ROWS -> spec.copy(rows = safe)
            MARGIN_HORIZONTAL -> spec.copy(marginHorizontalDp = safe)
            MARGIN_TOP -> spec.copy(marginTopDp = safe)
            MARGIN_BOTTOM -> spec.copy(marginBottomDp = safe)
            GAP -> spec.copy(gapDp = safe)
            ICON_SIZE -> spec.copy(iconSizeDp = safe)
            ROW_GAP -> spec.copy(rowGapDp = safe)
        }
    }

    /** Key under which this field of [profile] is stored. */
    fun storageKey(profile: GridProfile): String = "${profile.id}.$key"

    companion object {
        const val SHOW_LABELS_KEY = "showLabels"

        fun showLabelsStorageKey(profile: GridProfile): String = "${profile.id}.$SHOW_LABELS_KEY"

        const val PAGE_DOTS_KEY = "pageDots"

        fun pageDotsStorageKey(profile: GridProfile): String = "${profile.id}.$PAGE_DOTS_KEY"

        const val EVEN_GAPS_KEY = "evenGaps"

        fun evenGapsStorageKey(profile: GridProfile): String = "${profile.id}.$EVEN_GAPS_KEY"

        const val PLACE_KEY = "place"

        fun placeStorageKey(profile: GridProfile): String = "${profile.id}.$PLACE_KEY"

        /**
         * Fields that make sense on [surface], in the order they are offered. The drawer
         * scrolls, so it has no row count. On the home screen the icons go from one side
         * margin to the other, so the room between them follows from the rest; the room
         * between rows is offered on its own, after the switch that makes it the same.
         */
        fun forSurface(surface: Surface): List<GridField> = when (surface) {
            Surface.HOME -> listOf(COLUMNS, ROWS, ICON_SIZE, MARGIN_HORIZONTAL, MARGIN_TOP, MARGIN_BOTTOM)
            Surface.DRAWER -> listOf(COLUMNS, MARGIN_HORIZONTAL, MARGIN_TOP, MARGIN_BOTTOM, GAP, ICON_SIZE)
        }
    }
}

/**
 * Rebuilds the grid of [profile] from stored values. Anything missing falls back to the
 * profile's default, and anything out of range is pulled back in.
 */
fun GridSpec.Companion.fromStored(
    profile: GridProfile,
    int: (key: String) -> Int?,
    boolean: (key: String) -> Boolean?,
): GridSpec {
    var spec = default(profile.formFactor, profile.landscape)
    for (field in GridField.entries) {
        int(field.storageKey(profile))?.let { spec = field.set(spec, it) }
    }
    boolean(GridField.showLabelsStorageKey(profile))?.let { spec = spec.copy(showLabels = it) }
    boolean(GridField.pageDotsStorageKey(profile))?.let { spec = spec.copy(showPageDots = it) }
    boolean(GridField.evenGapsStorageKey(profile))?.let { spec = spec.copy(evenGaps = it) }
    GridPlace.fromKey(int(GridField.placeStorageKey(profile)))?.let { spec = spec.copy(place = it) }
    return spec
}
