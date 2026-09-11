package routing.query.event.comparison.dto

import io.github.ktwinx.core.hdt.model.ModelName
import routing.query.availability.ModelPresenceFilterDto
import routing.query.event.comparison.ComparisonOperator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

@Serializable
data class PropertyComparisonDto(
    val propertyName: String,
    val comparison: ComparisonOperator,
    val value: JsonElement
)

@Serializable
data class PropertiesByComparisonsRequestDto(
    val comparisons: List<PropertyComparisonDto>,
    val modelNames: List<ModelName>? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    /** Conjunction of `$in` predicates over `metadata.<key>`; absent/empty = no filter. Must not contain a `task` key -- use [taskScope] instead. */
    val metadataFilters: Map<String, List<String>>? = null,
    /** DT-level model presence requirements, conjunctively applied to the matched set. */
    val modelPresence: List<ModelPresenceFilterDto>? = null,
    /** Restricts observation acquisition scope to these `task` values; applied to the aggregation filters AND to every model-presence check. Null/empty means "every task". */
    val taskScope: List<String>? = null,
)