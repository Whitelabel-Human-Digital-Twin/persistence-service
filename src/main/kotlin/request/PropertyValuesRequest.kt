package io.github.whdt.request

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PropertyValuesRequest(
    val hdtId: HdtId? = null,
    val modelId: ModelId? = null,
    val propertyId: PropertyId? = null,
    val propertyName: PropertyName? = null,
    val from: Instant,
    val to: Instant,
)
