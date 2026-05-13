package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import org.bson.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PropertyDocumentTest {

    @Test
    fun `toDocument and fromDocument round-trip preserves all fields`() {
        val original = PropertyDocument(
            hdtId = HdtId("hdt-1"),
            modelId = ModelId("model-1"),
            propertyId = PropertyId("prop-1"),
            propertyName = PropertyName("temperature"),
            description = "Ambient temperature",
            declaredType = "DOUBLE",
            initialValue = PropertyValue.DoublePropertyValue(20.0),
            metadata = mapOf("unit" to "celsius"),
        )

        val bson = original.toDocument()
        val restored = PropertyDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(original.hdtId, restored.hdtId)
        assertEquals(original.modelId, restored.modelId)
        assertEquals(original.propertyId, restored.propertyId)
        assertEquals(original.propertyName, restored.propertyName)
        assertEquals(original.description, restored.description)
        assertEquals(original.declaredType, restored.declaredType)
        assertEquals(original.initialValue, restored.initialValue)
        assertEquals(original.metadata, restored.metadata)
    }

    @Test
    fun `toDocument and fromDocument round-trip with null initialValue`() {
        val original = PropertyDocument(
            hdtId = HdtId("hdt-2"),
            modelId = ModelId("model-2"),
            propertyId = PropertyId("prop-2"),
            propertyName = PropertyName("status"),
            description = "Device status",
            declaredType = "STRING",
            initialValue = null,
            metadata = emptyMap(),
        )

        val bson = original.toDocument()
        val restored = PropertyDocument.fromDocument(bson)

        assertNotNull(restored)
        assertNull(restored.initialValue)
        assertEquals(original.propertyName, restored.propertyName)
        assertEquals(original.declaredType, restored.declaredType)
    }

    @Test
    fun `toDocument serializes initialValue with type and value fields`() {
        val doc = PropertyDocument(
            hdtId = HdtId("h"),
            modelId = ModelId("m"),
            propertyId = PropertyId("p"),
            propertyName = PropertyName("count"),
            description = "Count",
            declaredType = "INT",
            initialValue = PropertyValue.IntPropertyValue(42),
        ).toDocument()

        val initialValueDoc = doc.get("initialValue", Document::class.java)
        assertNotNull(initialValueDoc)
        assertEquals("INT", initialValueDoc.getString("type"))
        assertEquals(42, initialValueDoc["value"])
    }
}
