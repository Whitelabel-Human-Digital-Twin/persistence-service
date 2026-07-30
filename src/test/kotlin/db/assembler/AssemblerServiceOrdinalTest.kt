package db.assembler

import MongoIntegrationTest
import db.hdt.HdtService
import db.model.ModelService
import db.property.PropertyDocument
import db.property.PropertyObservationService
import db.property.PropertyService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.ModelDescription
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AssemblerServiceOrdinalTest : MongoIntegrationTest() {

    private lateinit var hdtService: HdtService
    private lateinit var modelService: ModelService
    private lateinit var propertyService: PropertyService
    private lateinit var observationService: PropertyObservationService
    private lateinit var assembler: AssemblerService

    private val hdtId = HdtId("hdt-ordinal-spec")
    private val modelId = ModelId("hdt-ordinal-spec:vitals")

    private fun property(name: String, ordinal: Int) = PropertyDocument(
        hdtId = hdtId,
        modelId = modelId,
        propertyId = PropertyId("hdt-ordinal-spec:vitals:$name"),
        propertyName = PropertyName(name),
        description = "test property $name",
        declaredType = "DOUBLE",
        ordinal = ordinal,
    )

    @BeforeAll
    fun setup() = runBlocking {
        hdtService = HdtService(database)
        modelService = ModelService(database)
        propertyService = PropertyService(database)
        observationService = PropertyObservationService(database)
        assembler = AssemblerService(hdtService, modelService, propertyService, observationService)

        hdtService.upsert(HumanDigitalTwin(hdtId = hdtId))
        modelService.upsert(
            Model(
                hdtId = hdtId,
                name = ModelName("vitals"),
                description = ModelDescription("Vitals"),
                properties = emptyList(),
            )
        )

        // Inserted deliberately out of ordinal order: spo2(2), temperature(0), unassigned "notes"(-1),
        // heartRate(1), "diagnosis" also unassigned (-1).
        propertyService.collection.insertMany(
            listOf(
                property("spo2", 2),
                property("temperature", 0),
                property("notes", -1),
                property("heartRate", 1),
                property("diagnosis", -1),
            ).map { it.toDocument() }
        )
        Unit
    }

    @Test
    fun `getFullSpec returns PropertySpecEntry ordinal matching the stored document`() = runBlocking {
        val result = assembler.getFullSpec(hdtId)
        val spec = (result as db.util.Ok).result

        val propsByName = spec.models.single().properties.associateBy { it.propertyName }
        assertEquals(0, propsByName.getValue("temperature").ordinal)
        assertEquals(1, propsByName.getValue("heartRate").ordinal)
        assertEquals(2, propsByName.getValue("spo2").ordinal)
        assertEquals(-1, propsByName.getValue("notes").ordinal)
        assertEquals(-1, propsByName.getValue("diagnosis").ordinal)
    }

    @Test
    fun `getSnapshot returns entries in ascending ordinal order regardless of insertion order`() = runBlocking {
        val result = assembler.getSnapshot(hdtId)
        val snapshot = (result as db.util.Ok).result

        val assignedNames = snapshot.map { it.propertyName }.filter { it != "notes" && it != "diagnosis" }
        assertEquals(listOf("temperature", "heartRate", "spo2"), assignedNames)
    }

    @Test
    fun `getSnapshot sorts unassigned properties last, alphabetically among themselves`() = runBlocking {
        val result = assembler.getSnapshot(hdtId)
        val snapshot = (result as db.util.Ok).result

        val names = snapshot.map { it.propertyName }
        // "diagnosis" and "notes" both carry the default -1 ordinal and must sort after every
        // assigned property, alphabetically ordered between themselves.
        assertEquals(listOf("temperature", "heartRate", "spo2", "diagnosis", "notes"), names)
    }
}
