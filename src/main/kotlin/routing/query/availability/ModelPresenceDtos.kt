package routing.query.availability

import io.github.ktwinx.core.hdt.model.ModelName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelPresenceMode { HAS, HAS_NOT }

/**
 * A DT-level requirement: the twin must (or must not) have at least one observation
 * belonging to [modelName].
 *
 * Scope is no longer per-filter: every presence filter in a request shares the enclosing
 * query's `taskScope`, and never inherits the enclosing query's subject-attribute filters --
 * those build an `$in` per key on the observation's flat key/value map, and an observation
 * missing that key does not match. Sensor observations carry only `task` and `frame`, so
 * inheriting an enclosing filter such as `sex=F` would match zero sensor observations for
 * every twin -- silently making every HAS false and every HAS_NOT true.
 */
@Serializable
data class ModelPresenceFilterDto(
    val modelName: ModelName,
    val mode: ModelPresenceMode = ModelPresenceMode.HAS,
)
