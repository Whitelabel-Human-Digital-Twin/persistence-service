package io.github.whdt.request

import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.db.property.query.PropertyComparison
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class HdtIdsByPropertyComparisonsRequest(
    val comparisons: List<PropertyComparison>,
    val modelId: ModelId? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)