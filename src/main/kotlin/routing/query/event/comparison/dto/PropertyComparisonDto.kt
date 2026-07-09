package routing.query.event.comparison.dto

import io.github.ktwinx.core.hdt.model.ModelName
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
    /** Conjunction of `$in` predicates over `metadata.<key>`; absent/empty = no filter. */
    val metadataFilters: Map<String, List<String>>? = null,
)