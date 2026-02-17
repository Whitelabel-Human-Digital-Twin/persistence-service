package io.github.whdt.query

import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable

@Serializable
data class BetweenRequest(
    val propertyName: String,
    val min: PropertyValue,
    val max: PropertyValue
)