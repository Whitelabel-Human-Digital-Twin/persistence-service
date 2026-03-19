package io.github.whdt.request

import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.db.property.query.PropertyComparison
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class HdtIdsByPropertyComparisonsRequest(
    val comparisons: List<PropertyComparison>,
    val modelNames: List<ModelName>? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)