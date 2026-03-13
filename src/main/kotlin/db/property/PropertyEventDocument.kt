package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import java.util.*
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

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

    companion object {
        fun fromDocument(document: Document): PropertyEventMetadata {
            val hidRaw = document.getString("hdtId")
            val midRaw = document.getString("modelId")
            val pidRaw = document.getString("propertyId")
            val pnRaw = document.getString("propertyName")
            return PropertyEventMetadata(
                HdtId(hidRaw),
                ModelId(midRaw),
                PropertyName(pidRaw),
                PropertyId(pnRaw)
            )
        }
    }
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
        fun fromDocument(doc: Document): PropertyEventDocument {
            val meta = doc.get("metaField", Document::class.java)
            val metaField = PropertyEventMetadata.fromDocument(meta)
            val value = doc["value"]?.pv()
            val time = doc.getDate("timeField")?.toInstant()?.toKotlinInstant()
            return PropertyEventDocument(
                metaField,
                time!!,
                value!!
            )
        }

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