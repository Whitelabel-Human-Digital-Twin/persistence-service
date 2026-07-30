package db.property

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.Coding
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyValue
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
            tags = mapOf("unit" to "celsius"),
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
        assertEquals(original.tags, restored.tags)
        assertNull(restored.coding)
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
            tags = emptyMap(),
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

    @Test
    fun `toDocument and fromDocument round-trip with non-null coding`() {
        val coding = Coding(system = "http://loinc.org", code = "8867-4")
        val original = PropertyDocument(
            hdtId = HdtId("hdt-3"),
            modelId = ModelId("model-3"),
            propertyId = PropertyId("prop-3"),
            propertyName = PropertyName("heartRate"),
            description = "Heart rate",
            declaredType = "INT",
            tags = mapOf("unit" to "bpm"),
            coding = coding,
        )

        val bson = original.toDocument()
        val restored = PropertyDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(coding, restored.coding)
        assertEquals(original.tags, restored.tags)
    }

    @Test
    fun `toDocument and fromDocument round-trip with null coding defaults to null`() {
        val original = PropertyDocument(
            hdtId = HdtId("hdt-4"),
            modelId = ModelId("model-4"),
            propertyId = PropertyId("prop-4"),
            propertyName = PropertyName("weight"),
            description = "Body weight",
            declaredType = "FLOAT",
            coding = null,
        )

        val bson = original.toDocument()
        val restored = PropertyDocument.fromDocument(bson)

        assertNotNull(restored)
        assertNull(restored.coding)
    }

    @Test
    fun `toDocument and fromDocument round-trip preserves ordinal`() {
        val original = PropertyDocument(
            hdtId = HdtId("hdt-5"),
            modelId = ModelId("model-5"),
            propertyId = PropertyId("prop-5"),
            propertyName = PropertyName("heartRate"),
            description = "Heart rate",
            declaredType = "INT",
            ordinal = 4,
        )

        val bson = original.toDocument()
        val restored = PropertyDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(4, restored.ordinal)
    }

    @Test
    fun `a PropertyDocument built with no ordinal round-trips as -1`() {
        val original = PropertyDocument(
            hdtId = HdtId("hdt-6"),
            modelId = ModelId("model-6"),
            propertyId = PropertyId("prop-6"),
            propertyName = PropertyName("spo2"),
            description = "Oxygen saturation",
            declaredType = "DOUBLE",
        )

        val bson = original.toDocument()
        val restored = PropertyDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(-1, restored.ordinal)
    }

    @Test
    fun `a raw Document with no ordinal key deserializes to -1`() {
        val doc = Document()
            .append("hdtId", "hdt-7")
            .append("modelId", "model-7")
            .append("propertyId", "prop-7")
            .append("propertyName", "diagnosis")
            .append("description", "Diagnosis")
            .append("declaredType", "STRING")
            .append("tags", Document())
        // Deliberately no "ordinal" key: simulates a pre-existing document ingested
        // before this field existed.

        val restored = PropertyDocument.fromDocument(doc)

        assertNotNull(restored)
        assertEquals(-1, restored.ordinal)
    }
}
