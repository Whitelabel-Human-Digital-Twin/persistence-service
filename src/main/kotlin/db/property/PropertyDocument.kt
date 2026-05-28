package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Coding
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyDescription
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.core.hdt.model.property.PropertyValueType
import io.github.whdt.core.hdt.model.property.toPropertyValue
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
    val tags: Map<String, String> = emptyMap(),
    val coding: Coding? = null,
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
            .append("tags", Document(tags))
            .append("lastUpdated", Date.from(lastUpdated.toJavaInstant()))
        if (initialValue != null) {
            doc.append(
                "initialValue",
                Document("type", declaredType).append("value", initialValue.toBsonValue())
            )
        }
        if (coding != null) {
            doc.append("coding", Document("system", coding.system).append("code", coding.code))
        }
        return doc
    }

    fun toProperty(): Property = Property(
        modelId = modelId,
        name = propertyName,
        description = PropertyDescription(description),
        declaredType = PropertyValueType.valueOf(declaredType),
        initialValue = initialValue,
        tags = tags,
        coding = coding,
    )

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
                tags = property.tags,
                coding = property.coding,
            )

        fun fromDocument(doc: Document): PropertyDocument? {
            val hdtIdRaw = doc.getString("hdtId") ?: return null
            val modelIdRaw = doc.getString("modelId") ?: return null
            val propertyIdRaw = doc.getString("propertyId") ?: return null
            val propertyNameRaw = doc.getString("propertyName") ?: return null
            val description = doc.getString("description") ?: return null
            val declaredType = doc.getString("declaredType") ?: return null
            val tagsDoc = doc.get("tags", Document::class.java)
            val tags = tagsDoc?.entries?.associate { it.key to it.value.toString() } ?: emptyMap()
            val lastUpdated = doc.getDate("lastUpdated")?.toInstant()?.toKotlinInstant() ?: Clock.System.now()
            val initialValueDoc = doc.get("initialValue", Document::class.java)
            val initialValue = initialValueDoc?.get("value")?.toPropertyValue()
            val codingDoc = doc.get("coding", Document::class.java)
            val coding = codingDoc?.let {
                val system = it.getString("system") ?: return@let null
                val code = it.getString("code") ?: return@let null
                Coding(system, code)
            }
            return PropertyDocument(
                hdtId = HdtId(hdtIdRaw),
                modelId = ModelId(modelIdRaw),
                propertyId = PropertyId(propertyIdRaw),
                propertyName = PropertyName(propertyNameRaw),
                description = description,
                declaredType = declaredType,
                initialValue = initialValue,
                tags = tags,
                coding = coding,
                lastUpdated = lastUpdated,
            )
        }
    }
}
