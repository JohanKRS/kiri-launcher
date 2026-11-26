package io.github.johankrs.kiri.market

/** How far back the large chart of a stock looks. */
enum class ChartRange(val key: String) {
    /** The 24 hours that end at the latest trade. */
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    FIVE_YEARS("5y"),
    ;

    companion object {
        val DEFAULT = DAY

        fun fromKey(key: String?): ChartRange = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** A price at a moment, in seconds since 1970. */
data class PricePoint(val atEpochSeconds: Long, val price: Double)

/** The prices of one [ChartRange], oldest first. Never fewer than two. */
data class PriceSeries(val points: List<PricePoint>) {
    init {
        require(points.size >= 2) { "a chart needs at least two prices" }
    }

    val low: Double = points.minOf { it.price }
    val high: Double = points.maxOf { it.price }

    /** How much the price moved from the first point to the last, in percent. */
    val changePercent: Double? =
        points.first().price.takeIf { it != 0.0 }?.let { (points.last().price / it - 1.0) * 100.0 }

    /** The height of each point for drawing: 0 is the lowest price and 1 the highest. */
    val heights: List<Float> = sparkline(points.map { it.price })
}

sealed interface SeriesResult {
    data class Ok(val series: PriceSeries) : SeriesResult

    data class Failed(val reason: FailReason) : SeriesResult

    companion object {
        /** A series from [steps] (time to price, oldest first), or "not found" when too few. */
        fun of(steps: List<Pair<Long, Double>>): SeriesResult =
            if (steps.size < 2) Failed(FailReason.NOT_FOUND) else Ok(PriceSeries(steps.map { PricePoint(it.first, it.second) }))
    }
}
