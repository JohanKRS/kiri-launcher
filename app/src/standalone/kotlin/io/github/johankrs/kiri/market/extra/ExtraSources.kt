package io.github.johankrs.kiri.market.extra

import io.github.johankrs.kiri.market.CompleteSource
import io.github.johankrs.kiri.market.HttpFetcher

/**
 * The price sources a build adds beside the official services: none here. A build made with the
 * folder `personal` takes this object from there instead (see `app/build.gradle.kts`).
 */
object ExtraSources {
    fun create(http: HttpFetcher): List<CompleteSource> = emptyList()
}
