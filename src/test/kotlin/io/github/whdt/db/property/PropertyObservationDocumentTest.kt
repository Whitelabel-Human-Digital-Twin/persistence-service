package io.github.whdt.db.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyObservation
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class PropertyObservationDocumentTest {

    @Test
    fun `fromObservation produces document with expected metaField and value`() {
        val now = Clock.System.now()
        val observation = PropertyObservation(
            hdtId = HdtId("hdt-1"),
            modelId = ModelId("hdt-1:body"),
            modelName = ModelName("body"),
            propertyId = PropertyId("hdt-1:body:heartRate"),
            propertyName = PropertyName("heartRate"),
            value = PropertyValue.IntPropertyValue(72),
            timestamp = now,
            metadata = mapOf("source" to "sensor"),
        )

        val doc = PropertyObservationDocument.fromObservation(observation)

        assertEquals(HdtId("hdt-1"), doc.metaField.hdtId)
        assertEquals(ModelId("hdt-1:body"), doc.metaField.modelId)
        assertEquals(ModelName("body"), doc.metaField.modelName)
        assertEquals(PropertyName("heartRate"), doc.metaField.propertyName)
        assertEquals(PropertyId("hdt-1:body:heartRate"), doc.metaField.propertyId)
        assertEquals(PropertyValue.IntPropertyValue(72), doc.value)
        assertEquals(now, doc.timeField)
        assertEquals(mapOf("source" to "sensor"), doc.metadata)
    }

    @Test
    fun `toDocument and fromDocument round-trip preserves all fields`() {
        val now = Clock.System.now()
        val observation = PropertyObservation(
            hdtId = HdtId("hdt-1"),
            modelId = ModelId("hdt-1:vitals"),
            modelName = ModelName("vitals"),
            propertyId = PropertyId("hdt-1:vitals:p-42"),
            propertyName = PropertyName("p-42"),
            value = PropertyValue.DoublePropertyValue(36.6),
            timestamp = now,
            metadata = emptyMap(),
        )

        val original = PropertyObservationDocument.fromObservation(observation)
        val bson = original.toDocument()
        val restored = PropertyObservationDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(original.metaField.hdtId, restored.metaField.hdtId)
        assertEquals(original.metaField.propertyId, restored.metaField.propertyId)
        assertEquals(original.value, restored.value)
    }
}
