package io.github.whdt.db.property.query

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PropertyValuesById(
    val propertyId: PropertyId,
    val value: PropertyValue,
    val timestamp: Instant,
)

@Serializable
data class PropertyValuesByName(
    val modelId: ModelId,
    val propertyName: PropertyName,
    val value: PropertyValue,
    val timestamp: Instant,
)