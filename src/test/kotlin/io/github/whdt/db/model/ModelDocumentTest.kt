package io.github.whdt.db.model

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelDescription
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.Format
import io.github.whdt.core.hdt.model.WellKnownFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelDocumentTest {

    @Test
    fun `toDocument and fromDocument round-trip preserves tags and format defaults`() {
        val original = ModelDocument(
            hdtId = HdtId("hdt-1"),
            modelId = ModelId("hdt-1:vitals"),
            modelName = ModelName("vitals"),
            modelDescription = ModelDescription("Vital signs model"),
        )

        val bson = original.toDocument()
        val restored = ModelDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(original.hdtId, restored.hdtId)
        assertEquals(original.modelId, restored.modelId)
        assertEquals(original.modelName, restored.modelName)
        assertEquals(original.modelDescription, restored.modelDescription)
        assertEquals(emptyMap<String, String>(), restored.tags)
        assertEquals(WellKnownFormats.UNSPECIFIED, restored.format)
    }

    @Test
    fun `toDocument and fromDocument round-trip preserves non-default tags and format`() {
        val customFormat = Format("application/fhir+json")
        val original = ModelDocument(
            hdtId = HdtId("hdt-2"),
            modelId = ModelId("hdt-2:body"),
            modelName = ModelName("body"),
            modelDescription = ModelDescription("Body measurements"),
            tags = mapOf("domain" to "health", "version" to "1"),
            format = customFormat,
        )

        val bson = original.toDocument()
        val restored = ModelDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(original.tags, restored.tags)
        assertEquals(customFormat, restored.format)
    }
}
