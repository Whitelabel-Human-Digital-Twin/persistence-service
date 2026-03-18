package io.github.whdt.request

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.property.PropertyName
import kotlinx.serialization.Serializable

@Serializable
data class FindByNameResponse(
    val hdtId: HdtId,
    val propertyName: PropertyName,
    val propertyValue: String,
)
