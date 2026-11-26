package io.github.johankrs.kiri.core

/**
 * Everything that defines how icons are laid out on one screen. All sizes are in dp.
 *
 * One spec exists per form factor and orientation, so a phone in portrait and a tablet in
 * landscape are adjusted independently.
 */
data class GridSpec(
    val columns: Int,
    val rows: Int,
    val marginHorizontalDp: Int,
    val marginTopDp: Int,
    val marginBottomDp: Int,
    val gapDp: Int,
    val iconSizeDp: Int,
    val showLabels: Boolean,
    /** The dots that tell which page of the home screen is showing, while the pages turn. */
    val showPageDots: Boolean = true,
    /**
     * On the home screen, the room between rows is the room between columns. Otherwise it is
     * [rowGapDp].
     */
    val evenGaps: Boolean = true,
    /** On the home screen, the room between a row (names included) and the next, when not [evenGaps]. */
    val rowGapDp: Int = DEFAULT_ROW_GAP_DP,
    /** On the home screen, where the rows sit when they do not fill the height. */
    val place: GridPlace = GridPlace.TOP,
) {
    /** Returns a copy with every value pulled back inside its allowed range. */
    fun coerced(): GridSpec = copy(
        columns = columns.coerceIn(COLUMNS),
        rows = rows.coerceIn(ROWS),
        marginHorizontalDp = marginHorizontalDp.coerceIn(MARGIN_DP),
        marginTopDp = marginTopDp.coerceIn(MARGIN_DP),
        marginBottomDp = marginBottomDp.coerceIn(MARGIN_DP),
        gapDp = gapDp.coerceIn(GAP_DP),
        iconSizeDp = iconSizeDp.coerceIn(ICON_SIZE_DP),
        rowGapDp = rowGapDp.coerceIn(GAP_DP),
    )

    companion object {
        val COLUMNS = 2..14
        val ROWS = 2..14
        val MARGIN_DP = 0..160
        val GAP_DP = 0..64
        val ICON_SIZE_DP = 32..128
        const val DEFAULT_ROW_GAP_DP = 20

        fun default(formFactor: FormFactor, landscape: Boolean): GridSpec = when (formFactor) {
            FormFactor.PHONE ->
                if (landscape) GridSpec(8, 4, 32, 8, 8, 14, 52, true)
                else GridSpec(5, 6, 16, 24, 12, 18, 56, true)

            FormFactor.TABLET ->
                if (landscape) GridSpec(10, 6, 48, 24, 24, 24, 64, true)
                else GridSpec(7, 9, 32, 32, 24, 24, 64, true)

            FormFactor.TV -> GridSpec(6, 4, 56, 40, 40, 28, 96, true)
        }
    }
}

/** Where the rows of the home screen sit when they leave part of the height free. */
enum class GridPlace(val key: Int) {
    TOP(0),
    MIDDLE(1),
    BOTTOM(2),
    ;

    companion object {
        fun fromKey(key: Int?): GridPlace? = entries.firstOrNull { it.key == key }
    }
}
