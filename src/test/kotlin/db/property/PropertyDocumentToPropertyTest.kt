package db.property

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.Coding
import io.github.ktwinx.core.hdt.model.property.Property
import io.github.ktwinx.core.hdt.model.property.PropertyDescription
import io.github.ktwinx.core.hdt.model.property.PropertyValueType
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertyDocumentToPropertyTest {

    private val hdtId = HdtId("hdt-test")

    @Test
    fun `toProperty reconstructs modelId and name`() {
        val doc = PropertyDocument(
            hdtId = hdtId,
            modelId = ModelId("model-1"),
            propertyId = io.github.ktwinx.core.hdt.model.property.PropertyId("prop-1"),
            propertyName = io.github.ktwinx.core.hdt.model.property.PropertyName("temperature"),
            description = "Ambient temperature",
            declaredType = "DOUBLE",
        )
        val property = doc.toProperty()
        assertEquals(ModelId("model-1"), property.modelId)
        assertEquals(io.github.ktwinx.core.hdt.model.property.PropertyName("temperature"), property.name)
    }

    @Test
    fun `toProperty reconstructs description`() {
        val doc = PropertyDocument(
            hdtId = hdtId,
            modelId = ModelId("m"),
            propertyId = io.github.ktwinx.core.hdt.model.property.PropertyId("p"),
            propertyName = io.github.ktwinx.core.hdt.model.property.PropertyName("weight"),
            description = "Body weight in kg",
            declaredType = "FLOAT",
        )
        assertEquals(PropertyDescription("Body weight in kg"), doc.toProperty().description)
    }

    @Test
    fun `toProperty reconstructs declaredType enum`() {
        for (type in PropertyValueType.entries) {
            val doc = PropertyDocument(
                hdtId = hdtId,
                modelId = ModelId("m"),
                propertyId = io.github.ktwinx.core.hdt.model.property.PropertyId("p"),
                propertyName = io.github.ktwinx.core.hdt.model.property.PropertyName("x"),
                description = "",
                declaredType = type.name,
            )
            assertEquals(type, doc.toProperty().declaredType)
        }
    }

    @Test
    fun `toProperty reconstructs tags`() {
        val tags = mapOf("unit" to "celsius", "domain" to "vital")
        val doc = PropertyDocument(
            hdtId = hdtId,
            modelId = ModelId("m"),
            propertyId = io.github.ktwinx.core.hdt.model.property.PropertyId("p"),
            propertyName = io.github.ktwinx.core.hdt.model.property.PropertyName("temp"),
            description = "",
            declaredType = "DOUBLE",
            tags = tags,
        )
        assertEquals(tags, doc.toProperty().tags)
    }

    @Test
    fun `toProperty reconstructs coding`() {
        val coding = Coding(system = "http://loinc.org", code = "8867-4")
        val doc = PropertyDocument(
            hdtId = hdtId,
            modelId = ModelId("m"),
            propertyId = io.github.ktwinx.core.hdt.model.property.PropertyId("p"),
            propertyName = io.github.ktwinx.core.hdt.model.property.PropertyName("heartRate"),
            description = "",
            declaredType = "INT",
            coding = coding,
        )
        assertEquals(coding, doc.toProperty().coding)
    }

    @Test
    fun `toProperty with null coding and empty tags`() {
        val doc = PropertyDocument(
            hdtId = hdtId,
            modelId = ModelId("m"),
            propertyId = io.github.ktwinx.core.hdt.model.property.PropertyId("p"),
            propertyName = io.github.ktwinx.core.hdt.model.property.PropertyName("status"),
            description = "Status",
            declaredType = "STRING",
        )
        val property = doc.toProperty()
        assertNull(property.coding)
        assertEquals(emptyMap(), property.tags)
    }

    @Test
    fun `fromktwinxProperty then toProperty round-trips with non-empty tags and coding`() {
        val original = Property(
            modelId = ModelId("model-roundtrip"),
            name = io.github.ktwinx.core.hdt.model.property.PropertyName("pulse"),
            description = PropertyDescription("Heart pulse rate"),
            declaredType = PropertyValueType.INT,
            initialValue = PropertyValue.IntPropertyValue(72),
            tags = mapOf("unit" to "bpm", "domain" to "cardiac"),
            coding = Coding(system = "http://loinc.org", code = "8867-4"),
        )

        val doc = PropertyDocument.fromktwinxProperty(hdtId, original)
        val restored = doc.toProperty()

        assertEquals(original.modelId, restored.modelId)
        assertEquals(original.name, restored.name)
        assertEquals(original.description, restored.description)
        assertEquals(original.declaredType, restored.declaredType)
        assertEquals(original.initialValue, restored.initialValue)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.coding, restored.coding)
    }
}
