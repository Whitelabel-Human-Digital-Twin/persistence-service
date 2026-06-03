package db.hdt

import io.github.ktwinx.core.hdt.HdtId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HumanDigitalTwinDocumentTest {

    @Test
    fun `toDocument and fromDocument round-trip preserves tags`() {
        val original = HumanDigitalTwinDocument(
            hdtId = HdtId("hdt-1"),
            physicalInterfaces = emptyList(),
            digitalInterfaces = emptyList(),
            storages = emptyList(),
            tags = mapOf("owner" to "alice", "env" to "test"),
        )

        val bson = original.toDocument()
        val restored = HumanDigitalTwinDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(original.hdtId, restored.hdtId)
        assertEquals(original.tags, restored.tags)
    }

    @Test
    fun `toDocument and fromDocument round-trip with empty tags`() {
        val original = HumanDigitalTwinDocument(
            hdtId = HdtId("hdt-2"),
            physicalInterfaces = emptyList(),
            digitalInterfaces = emptyList(),
            storages = emptyList(),
            tags = emptyMap(),
        )

        val bson = original.toDocument()
        val restored = HumanDigitalTwinDocument.fromDocument(bson)

        assertNotNull(restored)
        assertEquals(emptyMap<String, String>(), restored.tags)
    }
}
