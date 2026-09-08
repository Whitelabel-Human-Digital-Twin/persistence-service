package routing.query.availability

import io.github.ktwinx.core.hdt.model.ModelName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class ModelPresenceMode { HAS, HAS_NOT }

/**
 * A DT-level requirement: the twin must (or must not) have at least one observation
 * belonging to [modelName].
 *
 * Scoping is INDEPENDENT of the enclosing query's `metadataFilters`, `from` and `to`,
 * and this is deliberate. `toMetadataBson` builds an `$in` per key on `metadata.<key>`,
 * and an observation missing that key does not match. Sensor observations carry only
 * `task` and `frame`, so inheriting an enclosing filter such as `sex=F` would match zero
 * sensor observations for every twin -- silently making every HAS false and every HAS_NOT
 * true. The time window is independent for the same reason: CRF observations sit at visit
 * timestamps, sensor acquisitions at acquisition timestamps.
 */
@Serializable
data class ModelPresenceFilterDto(
    val modelName: ModelName,
    val mode: ModelPresenceMode = ModelPresenceMode.HAS,
    val metadataFilters: Map<String, List<String>>? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)
