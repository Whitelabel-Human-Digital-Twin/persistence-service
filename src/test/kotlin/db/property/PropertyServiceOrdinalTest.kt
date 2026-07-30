package db.property

import MongoIntegrationTest
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyServiceOrdinalTest : MongoIntegrationTest() {

    private lateinit var service: PropertyService

    private fun makeProperty(hdtId: String, name: String, ordinal: Int) = PropertyDocument(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:vitals"),
        propertyId = PropertyId("$hdtId:vitals:$name"),
        propertyName = PropertyName(name),
        description = "test",
        declaredType = "DOUBLE",
        ordinal = ordinal,
    )

    @BeforeAll
    fun setup() {
        service = PropertyService(database)
        val docs = listOf(
            // "weight" declared at different ordinals across two HDTs -> canonical is the minimum.
            makeProperty("hdt-a", "weight", 2),
            makeProperty("hdt-b", "weight", 5),
            // "heartRate" and "spo2" only ever declared by one HDT each.
            makeProperty("hdt-a", "heartRate", 0),
            makeProperty("hdt-b", "spo2", 1),
            // "notes" is declared, but only ever with the unassigned default.
            makeProperty("hdt-a", "notes", -1),
            makeProperty("hdt-b", "notes", -1),
        )
        service.collection.insertMany(docs.map { it.toDocument() })
    }

    @Test
    fun `canonicalPropertyOrder resolves the minimum assigned ordinal across HDTs`() = runBlocking {
        val order = service.canonicalPropertyOrder()
        assertEquals(2, order[PropertyName("weight")])
    }

    @Test
    fun `canonicalPropertyOrder omits names with no assigned ordinal anywhere`() = runBlocking {
        val order = service.canonicalPropertyOrder()
        assertFalse(order.containsKey(PropertyName("notes")))
    }

    @Test
    fun `canonicalPropertyOrder covers every distinct name with at least one assigned ordinal`() = runBlocking {
        val order = service.canonicalPropertyOrder()
        assertEquals(setOf("weight", "heartRate", "spo2"), order.keys.map { it.value }.toSet())
    }

    @Test
    fun `distinctPropertyNames returns names in canonical order with unassigned last, alphabetical tiebreak`() = runBlocking {
        val names = service.distinctPropertyNames()
        // heartRate=0, spo2=1, weight=2 (min across hdt-a/hdt-b), then unassigned "notes" last.
        assertEquals(listOf("heartRate", "spo2", "weight", "notes"), names.map { it.value })
    }

    @Test
    fun `distinctPropertyNames still returns each name exactly once`() = runBlocking {
        val names = service.distinctPropertyNames()
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.map { it.value }.containsAll(listOf("weight", "heartRate", "spo2", "notes")))
    }
}
