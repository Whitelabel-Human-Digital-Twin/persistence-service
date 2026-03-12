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
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toJavaInstant

fun PropertyValue.toBsonValue(): Any? = when (this) {
    is PropertyValue.StringPropertyValue -> this.value
    is PropertyValue.IntPropertyValue -> this.value
    is PropertyValue.LongPropertyValue -> this.value
    is PropertyValue.DoublePropertyValue -> this.value
    is PropertyValue.BooleanPropertyValue -> this.value
    else -> null
}

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
        val tF = Date.from(timeField.toJavaInstant())
        val doc = Document()
            .append("metaField", mF)
            .append("timeField", tF)
            .append("value", value.toBsonValue())
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