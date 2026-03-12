package io.github.whdt.db.property.query

data class PropertyStatsPerHdt(
    val hdtId: String,
    val count: Long,
    val avg: Double?,
    val min: Double?,
    val max: Double?
)
