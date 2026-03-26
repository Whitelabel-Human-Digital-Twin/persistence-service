package io.github.whdt.routing.query.event.stats

import kotlinx.serialization.Serializable

@Serializable
data class PropertyStatsPerHdt(
    val hdtId: String,
    val count: Long,
    val avg: Double?,
    val min: Double?,
    val max: Double?
)