package io.github.whdt.query

import io.github.whdt.core.hdt.model.id.HdtId
import kotlinx.serialization.Serializable

@Serializable
data class FindByNameResponse(
    val hdtId: HdtId,
    val propertyName: String,
    val propertyValue: String,
)
