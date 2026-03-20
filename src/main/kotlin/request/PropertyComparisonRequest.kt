package io.github.whdt.request

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.db.property.query.PropertyComparison
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PropertiesByComparisonsAggregateRequest(
    val comparisons: List<PropertyComparison>,
    val modelNames: List<ModelName>? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

@Serializable
data class EventMatch(
    val propertyName: PropertyName,
    val value: PropertyValue,
    val timeField: Instant,
)

@Serializable
data class PropertiesByComparisonsAggregateResponse(
    val hdtId: HdtId,
    val matchedEvents: List<EventMatch>,
)