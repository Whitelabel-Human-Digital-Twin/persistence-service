package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import kotlin.time.Instant

@Serializable
data class PropertyDocument(
    val hdtId: HdtId,
    val modelId: ModelId,
    val propertyName: PropertyName,
    val timestamp: Instant,
    val value: PropertyValue
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(serializer(), this))

    companion object {
        fun fromWhdtProperty(hdtId: HdtId, property: Property): PropertyDocument {
            return PropertyDocument(hdtId = hdtId, modelId = property.modelId, propertyName = property.name, timestamp = property.timestamp, value = property.value)
        }
        fun fromDocument(document: Document): PropertyDocument {
            val copy = Document(document)
            copy.remove("_id")
            return Json.decodeFromString(serializer(), copy.toJson())
        }
    }
}