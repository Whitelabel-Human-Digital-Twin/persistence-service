package db.property

import MongoIntegrationTest
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import routing.query.availability.ModelMatchMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class PropertyObservationServiceAvailabilityTest : MongoIntegrationTest() {

    private lateinit var service: PropertyObservationService

    private val ts = Instant.parse("2026-03-01T00:00:00Z")

    private fun observation(
        hdtId: String,
        modelName: String,
        offsetSeconds: Long,
        task: String? = null,
    ) = PropertyObservation(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:$modelName"),
        modelName = ModelName(modelName),
        propertyId = PropertyId("$hdtId:$modelName:value"),
        propertyName = PropertyName("value"),
        value = PropertyValue.DoublePropertyValue(1.0),
        timestamp = ts.plus(offsetSeconds.seconds),
        metadata = if (task != null) mapOf("task" to task) else emptyMap(),
    )

    @BeforeAll
    fun setup() = runBlocking {
        service = PropertyObservationService(database)

        val observations = listOf(
            // hdt-avail-a: acc (x3, offsets 0/1/2) and gyro (x2, offsets 0/1)
            observation("hdt-avail-a", "acc", 0, task = "walking"),
            observation("hdt-avail-a", "acc", 1, task = "walking"),
            observation("hdt-avail-a", "acc", 2, task = "running"),
            observation("hdt-avail-a", "gyro", 0),
            observation("hdt-avail-a", "gyro", 1),

            // hdt-avail-b: acc only (x2, offsets 10/20), all "running"
            observation("hdt-avail-b", "acc", 10, task = "running"),
            observation("hdt-avail-b", "acc", 20, task = "running"),

            // hdt-avail-c: vitals only (x4)
            observation("hdt-avail-c", "vitals", 0),
            observation("hdt-avail-c", "vitals", 1),
            observation("hdt-avail-c", "vitals", 2),
            observation("hdt-avail-c", "vitals", 3),
        )
        service.insertMany(observations)
        Unit
    }

    @Test
    fun `hdtsByModel ANY mode returns exactly the HDTs having the requested model`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = listOf(ModelName("acc")),
            match = ModelMatchMode.ANY,
            metadataFilters = null,
            from = null,
            to = null,
        )

        assertEquals(listOf("hdt-avail-a", "hdt-avail-b"), result.map { it.hdtId.id }, "must be sorted by hdtId ascending")

        val rowA = result.first { it.hdtId.id == "hdt-avail-a" }
        assertEquals(1, rowA.models.size)
        val accA = rowA.models.single()
        assertEquals(ModelName("acc"), accA.modelName)
        assertEquals(3L, accA.observationCount)
        assertEquals(ts, accA.firstTimestamp)
        assertEquals(ts.plus(2.seconds), accA.lastTimestamp)

        val rowB = result.first { it.hdtId.id == "hdt-avail-b" }
        assertEquals(1, rowB.models.size)
        val accB = rowB.models.single()
        assertEquals(ModelName("acc"), accB.modelName)
        assertEquals(2L, accB.observationCount)
        assertEquals(ts.plus(10.seconds), accB.firstTimestamp)
        assertEquals(ts.plus(20.seconds), accB.lastTimestamp)
    }

    @Test
    fun `hdtsByModel with null modelNames returns the full availability matrix`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = null,
            match = ModelMatchMode.ANY,
            metadataFilters = null,
            from = null,
            to = null,
        )

        assertEquals(listOf("hdt-avail-a", "hdt-avail-b", "hdt-avail-c"), result.map { it.hdtId.id })

        val rowA = result.first { it.hdtId.id == "hdt-avail-a" }
        assertEquals(setOf("acc", "gyro"), rowA.models.map { it.modelName.value }.toSet())
    }

    @Test
    fun `hdtsByModel with empty modelNames list also returns the full availability matrix`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = emptyList(),
            match = ModelMatchMode.ANY,
            metadataFilters = null,
            from = null,
            to = null,
        )

        assertEquals(listOf("hdt-avail-a", "hdt-avail-b", "hdt-avail-c"), result.map { it.hdtId.id })
    }

    @Test
    fun `hdtsByModel metadataFilters narrows counts and excludes HDTs with no matching observation`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = listOf(ModelName("acc")),
            match = ModelMatchMode.ANY,
            metadataFilters = mapOf("task" to listOf("walking")),
            from = null,
            to = null,
        )

        // hdt-avail-b's acc observations are all "running" -> excluded entirely
        assertEquals(listOf("hdt-avail-a"), result.map { it.hdtId.id })
        val accA = result.single().models.single()
        assertEquals(2L, accA.observationCount)
        assertEquals(ts, accA.firstTimestamp)
        assertEquals(ts.plus(1.seconds), accA.lastTimestamp)
    }

    @Test
    fun `hdtsByModel time window narrows counts and shifts first and last timestamps`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = listOf(ModelName("acc")),
            match = ModelMatchMode.ANY,
            metadataFilters = null,
            from = ts.plus(1.seconds).toJavaInstant(),
            to = ts.plus(3.seconds).toJavaInstant(),
        )

        // Only hdt-avail-a's acc observations at offsets 1 and 2 fall inside [1s, 3s)
        assertEquals(listOf("hdt-avail-a"), result.map { it.hdtId.id })
        val accA = result.single().models.single()
        assertEquals(2L, accA.observationCount)
        assertEquals(ts.plus(1.seconds), accA.firstTimestamp)
        assertEquals(ts.plus(2.seconds), accA.lastTimestamp)
    }

    @Test
    fun `hdtsByModel ALL mode returns only HDTs having every requested model`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = listOf(ModelName("acc"), ModelName("gyro")),
            match = ModelMatchMode.ALL,
            metadataFilters = null,
            from = null,
            to = null,
        )

        assertEquals(listOf("hdt-avail-a"), result.map { it.hdtId.id })
        assertEquals(setOf("acc", "gyro"), result.single().models.map { it.modelName.value }.toSet())
    }

    @Test
    fun `hdtsByModel ANY mode with the same models returns both HDTs`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = listOf(ModelName("acc"), ModelName("gyro")),
            match = ModelMatchMode.ANY,
            metadataFilters = null,
            from = null,
            to = null,
        )

        assertEquals(listOf("hdt-avail-a", "hdt-avail-b"), result.map { it.hdtId.id })
    }

    @Test
    fun `hdtsByModel ALL mode with null modelNames behaves like ANY, not like every model in the system`() = runBlocking {
        val result = service.hdtsByModel(
            modelNames = null,
            match = ModelMatchMode.ALL,
            metadataFilters = null,
            from = null,
            to = null,
        )

        assertEquals(listOf("hdt-avail-a", "hdt-avail-b", "hdt-avail-c"), result.map { it.hdtId.id })
        assertNull(result.firstOrNull { it.models.size < 1 })
    }
}
