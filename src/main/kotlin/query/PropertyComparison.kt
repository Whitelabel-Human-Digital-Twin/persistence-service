package io.github.whdt.query

import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable

@Serializable
enum class ComparisonOperator {
    GT, GTE, LT, LTE, EQ
}

@Serializable
data class PropertyComparisonRequest(
    val propertyName: String,
    val valueKey: String,
    val operator: ComparisonOperator,
    val value: PropertyValue
)

@Serializable
data class PropertyComparisonResponse(
    val hdtId: HdtId,
    val propertyName: String,
    val value: String
)