package io.github.whdt.db.property.query

import kotlinx.serialization.Serializable

@Serializable
data class PropertyStatsPerHdt(
    val hdtId: String,
    val count: Long,
    val avg: Double?,
    val min: Double?,
    val max: Double?
)
