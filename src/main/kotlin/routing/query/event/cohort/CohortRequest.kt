package routing.query.event.cohort

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import routing.query.event.comparison.PropertyPopulationStats

@Serializable
data class CohortPropertyCell(
    val propertyName: PropertyName,
    val value: PropertyValue?,
    val count: Long,
    val avg: Double?,
    val min: Double?,
    val max: Double?,
    val median: Double?,
    val p25: Double?,
    val p75: Double?,
)

@Serializable
data class CohortRow(
    val hdtId: HdtId,
    val properties: List<CohortPropertyCell>,
)

@Serializable
data class CohortResult(
    val rows: List<CohortRow>,
    val populationStats: List<PropertyPopulationStats>,
)
