package routing.query.event.comparison

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import io.github.ktwinx.core.hdt.model.property.toPropertyValue
import kotlinx.serialization.Serializable
import org.bson.Document
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

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
) {
    companion object {
        fun fromDocument(doc: Document): EventMatch? {
            val name = doc.getString("propertyName") ?: return null
            val value = doc["value"] ?: return null
            val propertyValue = value.toPropertyValue() ?: return null
            val timeField = doc.getDate("timeField").toInstant().toKotlinInstant()
            return EventMatch(PropertyName(name), propertyValue, timeField)
        }
    }
}

@Serializable
data class PropertiesByComparisonsAggregateResponse(
    val hdtId: HdtId,
    val matchedProperties: List<PropertyName>,
    val matchedEvents: List<EventMatch>,
) {
    companion object {
        fun fromDocument(doc: Document): PropertiesByComparisonsAggregateResponse? {
            val hdtId = doc.getString("_id") ?: return null
            val matchedProperties =
                doc.getList("matchedProperties", String::class.java)
                    .toList()
                    .map { PropertyName(it) }
            val eventMatches =
                doc.getList("matchedEvents", Document::class.java)
                    .toList()
                    .mapNotNull { EventMatch.fromDocument(it) }
            return PropertiesByComparisonsAggregateResponse(
                hdtId = HdtId(hdtId),
                matchedProperties = matchedProperties,
                matchedEvents = eventMatches,
            )
        }
    }
}

@Serializable
data class PropertyPopulationStats(
    val propertyName: PropertyName,
    val count: Long,
    val avg: Double?,
    val min: Double?,
    val max: Double?,
    val median: Double?,
    val p25: Double?,
    val p75: Double?,
) {
    companion object {
        fun fromDocument(doc: Document): PropertyPopulationStats? {
            val propertyName = doc.getString("_id") ?: return null
            val percentiles = doc.getList("pct", Number::class.java).orEmpty()
            return PropertyPopulationStats(
                propertyName = PropertyName(propertyName),
                count = (doc["count"] as Number).toLong(),
                avg = (doc["avg"] as? Number)?.toDouble(),
                min = (doc["min"] as? Number)?.toDouble(),
                max = (doc["max"] as? Number)?.toDouble(),
                p25 = percentiles.getOrNull(0)?.toDouble(),
                median = percentiles.getOrNull(1)?.toDouble(),
                p75 = percentiles.getOrNull(2)?.toDouble(),
            )
        }
    }
}

@Serializable
data class ComparisonSearchResult(
    val matches: List<PropertiesByComparisonsAggregateResponse>,
    val populationStats: List<PropertyPopulationStats>,
)