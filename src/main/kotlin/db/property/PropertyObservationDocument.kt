package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyObservation
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import java.util.*
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

// MIGRATION NOTE: The MongoDB `property_events` collection is a time-series collection.
// To rename it in MongoDB 6.0+ (Atlas), run this before deploying this version:
//   db.adminCommand({ renameCollection: "yourdb.property_events", to: "yourdb.observations" })
// If zero-downtime is required, keep the old collection name active during the transition
// and handle both names transiently until migration is confirmed complete.

fun Any.pv(): PropertyValue? = when (this) {
    is Int -> PropertyValue.IntPropertyValue(this)
    is Long -> PropertyValue.LongPropertyValue(this)
    is Float -> PropertyValue.FloatPropertyValue(this)
    is Double -> PropertyValue.DoublePropertyValue(this)
    is String -> PropertyValue.StringPropertyValue(this)
    is Boolean -> PropertyValue.BooleanPropertyValue(this)
    else -> null
}

fun PropertyValue.toBsonValue(): Any? = when (this) {
    is PropertyValue.StringPropertyValue -> this.value
    is PropertyValue.IntPropertyValue -> this.value
    is PropertyValue.LongPropertyValue -> this.value
    is PropertyValue.FloatPropertyValue -> this.value
    is PropertyValue.DoublePropertyValue -> this.value
    is PropertyValue.BooleanPropertyValue -> this.value
    else -> null
}

@Serializable
data class PropertyEventMetadata(
    val hdtId: HdtId,
    val modelId: ModelId,
    val modelName: ModelName,
    val propertyName: PropertyName,
    val propertyId: PropertyId,
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromDocument(document: Document): PropertyEventMetadata? {
            val hidRaw = document.getString("hdtId") ?: return null
            val midRaw = document.getString("modelId") ?: return null
            val mnRaw = document.getString("modelName") ?: return null
            val pidRaw = document.getString("propertyId") ?: return null
            val pnRaw = document.getString("propertyName") ?: return null
            return PropertyEventMetadata(
                HdtId(hidRaw),
                ModelId(midRaw),
                ModelName(mnRaw),
                PropertyName(pnRaw),
                PropertyId(pidRaw)
            )
        }
    }
}

@Serializable
data class PropertyObservationDocument(
    val metaField: PropertyEventMetadata,
    val timeField: Instant,
    val value: PropertyValue,
    val metadata: Map<String, String>
) {
    fun toDocument(): Document {
        val mF = metaField.toDocument()
        val tF = Date.from(timeField.toJavaInstant())
        val metadataDoc = Document(metadata)
        val doc = Document()
            .append("metaField", mF)
            .append("timeField", tF)
            .append("value", value.toBsonValue())
            .append("metadata", metadataDoc)
        return doc
    }

    companion object {
        fun fromDocument(doc: Document): PropertyObservationDocument? {
            val meta = doc.get("metaField", Document::class.java)
            val metaField = PropertyEventMetadata.fromDocument(meta) ?: return null
            val value = doc["value"]?.pv() ?: return null
            val time = doc.getDate("timeField")?.toInstant()?.toKotlinInstant() ?: return null
            val metadataDoc = doc.get("metadata", Document::class.java)
            val metadata = metadataDoc?.entries
                ?.associate { (k, v) -> k to v.toString() }
                ?: emptyMap()
            return PropertyObservationDocument(
                metaField,
                time,
                value,
                metadata
            )
        }

        fun fromObservation(observation: PropertyObservation): PropertyObservationDocument {
            val mnRaw = observation.modelId.value.split(":").last()
            val modelName = ModelName(mnRaw)
            val meta = PropertyEventMetadata(
                hdtId = observation.hdtId,
                modelId = observation.modelId,
                modelName = modelName,
                propertyName = observation.propertyName,
                propertyId = observation.propertyId
            )
            return PropertyObservationDocument(
                metaField = meta,
                timeField = observation.timestamp,
                value = observation.value,
                metadata = observation.metadata
            )
        }
    }
}
