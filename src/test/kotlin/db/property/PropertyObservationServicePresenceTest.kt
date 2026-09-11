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
import routing.query.availability.ModelPresenceFilterDto
import routing.query.availability.ModelPresenceMode
import routing.query.event.comparison.ComparisonOperator
import routing.query.event.comparison.PropertyComparison
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PropertyObservationServicePresenceTest : MongoIntegrationTest() {

    private lateinit var service: PropertyObservationService

    private val ts = Instant.parse("2026-06-01T00:00:00Z")

    private fun property(
        hdtId: String,
        propertyName: String,
        value: Double,
        metadata: Map<String, String> = emptyMap(),
    ) = PropertyObservation(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:vitals"),
        modelName = ModelName("vitals"),
        propertyId = PropertyId("$hdtId:vitals:$propertyName"),
        propertyName = PropertyName(propertyName),
        value = PropertyValue.DoublePropertyValue(value),
        timestamp = ts,
        metadata = metadata,
    )

    private fun sensor(
        hdtId: String,
        modelName: String,
        timestamp: Instant,
        metadata: Map<String, String> = emptyMap(),
    ) = PropertyObservation(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:$modelName"),
        modelName = ModelName(modelName),
        propertyId = PropertyId("$hdtId:$modelName:value"),
        propertyName = PropertyName("value"),
        value = PropertyValue.DoublePropertyValue(1.0),
        timestamp = timestamp,
        metadata = metadata,
    )

    @BeforeAll
    fun setup() = runBlocking {
        service = PropertyObservationService(database)

        val g1Ts = ts
        val observations = mutableListOf<PropertyObservation>()

        // --- Group 1: HAS / HAS_NOT / presence-only (steps 2, 3, 6) ---
        // hdt-p-a: p1 passes, has acc
        observations += property("hdt-p-a", "p1", 100.0)
        observations += sensor("hdt-p-a", "acc", g1Ts)
        // hdt-p-b: p1 passes, no acc
        observations += property("hdt-p-b", "p1", 100.0)
        // hdt-p-c: acc only, no p1 at all
        observations += sensor("hdt-p-c", "acc", g1Ts)
        // hdt-p-d: no observations at all (added to universe manually, not inserted)

        // --- Group 2: independent scoping (step 4) ---
        // All three have p2 passing (task=nw, so taskScope=["nw"] keeps them as candidates) with
        // sex=F metadata; acc observations vary by task, exercising the presence-side taskScope gate.
        val gTs = ts.plus(1000.seconds)
        val iTs = ts.plus(2000.seconds)
        observations += property("hdt-p-g", "p2", 100.0, metadata = mapOf("sex" to "F", "task" to "nw"))
        observations += sensor("hdt-p-g", "acc", gTs, metadata = mapOf("task" to "nw"))
        observations += property("hdt-p-h", "p2", 100.0, metadata = mapOf("sex" to "F", "task" to "nw"))
        observations += sensor("hdt-p-h", "acc", gTs, metadata = mapOf("task" to "tug"))
        observations += property("hdt-p-i", "p2", 100.0, metadata = mapOf("sex" to "F", "task" to "nw"))
        observations += sensor("hdt-p-i", "acc", iTs, metadata = mapOf("task" to "nw"))

        // --- Group 3: multiple filters, conjunctive + scope batching (step 5) ---
        observations += property("hdt-p-k", "p3", 100.0)
        observations += sensor("hdt-p-k", "acc", g1Ts)
        observations += property("hdt-p-l", "p3", 100.0)
        observations += sensor("hdt-p-l", "gyro", g1Ts)
        observations += property("hdt-p-m", "p3", 100.0)
        observations += sensor("hdt-p-m", "acc", g1Ts)
        observations += sensor("hdt-p-m", "gyro", g1Ts)
        observations += property("hdt-p-n", "p3", 100.0)

        service.insertMany(observations)
        Unit
    }

    private val p1Comparisons = listOf(
        PropertyComparison(PropertyName("p1"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(50.0))
    )
    private val p2Comparisons = listOf(
        PropertyComparison(PropertyName("p2"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(50.0))
    )
    private val p3Comparisons = listOf(
        PropertyComparison(PropertyName("p3"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(50.0))
    )

    @Test
    fun `HAS acc narrows matched comparisons to the twin also having acc data`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = p1Comparisons,
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
        )
        assertEquals(listOf("hdt-p-a"), result.matches.map { it.hdtId.id })
    }

    @Test
    fun `HAS_NOT acc narrows matched comparisons to the twin lacking acc data`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = p1Comparisons,
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS_NOT)),
        )
        assertEquals(listOf("hdt-p-b"), result.matches.map { it.hdtId.id })
    }

    @Test
    fun `enclosing metadataFilters does not leak into the presence check`() = runBlocking {
        val result = service.cohortExplore(
            comparisons = p2Comparisons,
            metadataFilters = mapOf("sex" to listOf("F")),
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
        )
        val resultIds = result.rows.map { it.hdtId.id }.toSet()
        // if the outer sex=F filter leaked into the presence check, no acc observation (which
        // carries no "sex" key) would ever match, and every twin would be excluded.
        assertEquals(setOf("hdt-p-g", "hdt-p-h", "hdt-p-i"), resultIds)
    }

    @Test
    fun `taskScope excludes a twin whose acc observations carry a different task, applied to both aggregation and presence`() = runBlocking {
        val result = service.cohortExplore(
            comparisons = p2Comparisons,
            taskScope = listOf("nw"),
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
        )
        val resultIds = result.rows.map { it.hdtId.id }.toSet()
        // hdt-p-h's only acc observation carries task=tug -- excluded despite otherwise qualifying.
        assertEquals(setOf("hdt-p-g", "hdt-p-i"), resultIds)
    }

    @Test
    fun `multiple presence filters are conjunctive`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = p3Comparisons,
            modelPresence = listOf(
                ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS),
                ModelPresenceFilterDto(ModelName("gyro"), ModelPresenceMode.HAS_NOT),
            ),
        )
        assertEquals(listOf("hdt-p-k"), result.matches.map { it.hdtId.id })
    }

    @Test
    fun `two HAS filters resolved in one shared call return the intersection`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = p3Comparisons,
            modelPresence = listOf(
                ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS),
                ModelPresenceFilterDto(ModelName("gyro"), ModelPresenceMode.HAS),
            ),
        )
        assertEquals(listOf("hdt-p-m"), result.matches.map { it.hdtId.id })
    }

    @Test
    fun `presence-only HAS search returns twins having the model, evaluated over the supplied universe`() = runBlocking {
        val universe = listOf("hdt-p-a", "hdt-p-b", "hdt-p-c", "hdt-p-d").map { HdtId(it) }
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = emptyList(),
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
            universe = universe,
        )
        assertEquals(setOf("hdt-p-a", "hdt-p-c"), result.matches.map { it.hdtId.id }.toSet())
    }

    @Test
    fun `presence-only HAS_NOT search includes a twin with zero observations at all`() = runBlocking {
        val universe = listOf("hdt-p-a", "hdt-p-b", "hdt-p-c", "hdt-p-d").map { HdtId(it) }
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = emptyList(),
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS_NOT)),
            universe = universe,
        )
        assertEquals(setOf("hdt-p-b", "hdt-p-d"), result.matches.map { it.hdtId.id }.toSet())
    }

    @Test
    fun `presence-only search with a null universe throws`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            service.observationsByComparisonsAggregate(
                propertyComparisons = emptyList(),
                modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
                universe = null,
            )
        }
    }

    @Test
    fun `cohortExplore applies the presence gate and computes populationStats over the gated set only`() = runBlocking {
        val result = service.cohortExplore(
            comparisons = p1Comparisons,
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
        )
        assertEquals(listOf("hdt-p-a"), result.rows.map { it.hdtId.id })
        val p1Stats = result.populationStats.single { it.propertyName.value == "p1" }
        // only hdt-p-a's single p1 reading contributes; if hdt-p-b leaked in, count would be 2.
        assertEquals(1L, p1Stats.count)
    }
}
