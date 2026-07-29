package routing.query.availability

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class ModelMatchMode { ANY, ALL }

@Serializable
data class HdtsByModelRequestDto(
    /** Null or empty means "every model" -- the full availability matrix. */
    val modelNames: List<ModelName>? = null,
    /** Ignored when [modelNames] is null or empty. */
    val match: ModelMatchMode = ModelMatchMode.ANY,
    /** Conjunctive `$in`-per-key over observation `metadata`, same idiom as /query/cohort. */
    val metadataFilters: Map<String, List<String>>? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

@Serializable
data class ModelAvailability(
    val modelName: ModelName,
    val observationCount: Long,
    val firstTimestamp: Instant,
    val lastTimestamp: Instant,
)

@Serializable
data class HdtModelAvailability(
    val hdtId: HdtId,
    val models: List<ModelAvailability>,
)
