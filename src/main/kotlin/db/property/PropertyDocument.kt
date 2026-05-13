package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import org.bson.Document
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@Serializable
data class PropertyDocument(
    val hdtId: HdtId,
    val modelId: ModelId,
    val propertyId: PropertyId,
    val propertyName: PropertyName,
    val description: String,
    val declaredType: String,
    val initialValue: PropertyValue? = null,
    val metadata: Map<String, String> = emptyMap(),
    val lastUpdated: Instant = Clock.System.now(),
) {
    fun toDocument(): Document {
        val doc = Document()
            .append("hdtId", hdtId.id)
            .append("modelId", modelId.value)
            .append("propertyId", propertyId.value)
            .append("propertyName", propertyName.value)
            .append("description", description)
            .append("declaredType", declaredType)
            .append("metadata", Document(metadata))
            .append("lastUpdated", Date.from(lastUpdated.toJavaInstant()))
        if (initialValue != null) {
            doc.append(
                "initialValue",
                Document("type", declaredType).append("value", initialValue.toBsonValue())
            )
        }
        return doc
    }

    companion object {
        fun fromWhdtProperty(hdtId: HdtId, property: Property): PropertyDocument =
            PropertyDocument(
                hdtId = hdtId,
                modelId = property.modelId,
                propertyId = property.id,
                propertyName = property.name,
                description = property.description.value,
                declaredType = property.declaredType.name,
                initialValue = property.initialValue,
                metadata = property.metadata,
            )

        fun fromDocument(doc: Document): PropertyDocument? {
            val hdtIdRaw = doc.getString("hdtId") ?: return null
            val modelIdRaw = doc.getString("modelId") ?: return null
            val propertyIdRaw = doc.getString("propertyId") ?: return null
            val propertyNameRaw = doc.getString("propertyName") ?: return null
            val description = doc.getString("description") ?: return null
            val declaredType = doc.getString("declaredType") ?: return null
            val metadataDoc = doc.get("metadata", Document::class.java)
            val metadata = metadataDoc?.entries?.associate { it.key to it.value.toString() } ?: emptyMap()
            val lastUpdated = doc.getDate("lastUpdated")?.toInstant()?.toKotlinInstant() ?: Clock.System.now()
            val initialValueDoc = doc.get("initialValue", Document::class.java)
            val initialValue = initialValueDoc?.get("value")?.pv()
            return PropertyDocument(
                hdtId = HdtId(hdtIdRaw),
                modelId = ModelId(modelIdRaw),
                propertyId = PropertyId(propertyIdRaw),
                propertyName = PropertyName(propertyNameRaw),
                description = description,
                declaredType = declaredType,
                initialValue = initialValue,
                metadata = metadata,
                lastUpdated = lastUpdated,
            )
        }
    }
}
