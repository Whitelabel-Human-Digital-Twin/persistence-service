package io.github.whdt.routing.query.event.comparison

import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable

@Serializable
enum class ComparisonOperator {
    GT, GTE, LT, LTE, EQ
}

@Serializable
data class Comparison(
    val comparison: ComparisonOperator,
    val value: PropertyValue,
)

@Serializable
data class PropertyComparison(
    val propertyName: PropertyName,
    val comparison: ComparisonOperator,
    val value: PropertyValue,
)