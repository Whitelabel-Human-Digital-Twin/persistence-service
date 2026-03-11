package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.BsonDateTime
import org.bson.Document
import kotlin.time.Instant

@Serializable
data class PropertyEventMetadata(
    val hdtId: HdtId,
    val modelId: ModelId,
    val propertyName: PropertyName,
    val propertyId: PropertyId,
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))
}

@Serializable
data class PropertyEventDocument(
    val metaField: PropertyEventMetadata,
    val timeField: Instant,
    val value: PropertyValue
) {
    fun toDocument(): Document {
        val mF = metaField.toDocument()
        val tF = BsonDateTime(timeField.toEpochMilliseconds())
        val valueF = Json.encodeToString(PropertyValue.serializer(), value)
        val doc = Document()
            .append("metaField", mF)
            .append("timeField", tF)
            .append("value", valueF)
        return doc
    }

    companion object {
        fun fromWhdtProperty(hdtId: HdtId, property: Property): PropertyEventDocument {
            val meta = PropertyEventMetadata(
                hdtId = hdtId,
                modelId = property.modelId,
                propertyName = property.name,
                propertyId = property.id
            )
            return PropertyEventDocument(
                metaField = meta,
                timeField = property.timestamp,
                value = property.value
            )
        }
    }
}