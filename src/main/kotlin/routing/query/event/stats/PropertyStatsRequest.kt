package routing.query.event.stats

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PropertyStatsRequest(
    val hdtIds: List<HdtId>,
    val modelIds: List<ModelId>,
    val modelNames: List<ModelName>,
    val propertyName: PropertyName,
    val from: Instant? = null,
    val to: Instant? = null
)