package io.github.whdt.routing.query.event.comparison

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.db.property.pv
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
            val propertyValue = value.pv() ?: return null
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