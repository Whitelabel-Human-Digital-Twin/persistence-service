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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `taskScope` is the promoted, top-level acquisition-scope filter (see issue #44): it must apply
 * to the aggregation match AND to every model-presence check, and it must fail closed -- an
 * observation with no `task` key is excluded by a non-null taskScope rather than passed through.
 *
 * `hdt-t-b` is inserted with property observations carrying no `task` key at all. Production data
 * is not expected to contain such a document (every ingestion path is required to stamp `task`),
 * but the fixture pins the fail-closed behaviour regardless of whether that invariant holds.
 */
class PropertyObservationServiceTaskScopeTest : MongoIntegrationTest() {

    private lateinit var service: PropertyObservationService

    private val ts = Instant.parse("2026-07-01T00:00:00Z")

    private fun property(
        hdtId: String,
        propertyName: String,
        value: Double,
        task: String? = null,
    ) = PropertyObservation(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:vitals"),
        modelName = ModelName("vitals"),
        propertyId = PropertyId("$hdtId:vitals:$propertyName"),
        propertyName = PropertyName(propertyName),
        value = PropertyValue.DoublePropertyValue(value),
        timestamp = ts,
        metadata = if (task != null) mapOf("task" to task) else emptyMap(),
    )

    private fun sensor(
        hdtId: String,
        modelName: String,
        task: String,
    ) = PropertyObservation(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:$modelName"),
        modelName = ModelName(modelName),
        propertyId = PropertyId("$hdtId:$modelName:value"),
        propertyName = PropertyName("value"),
        value = PropertyValue.DoublePropertyValue(1.0),
        timestamp = ts,
        metadata = mapOf("task" to task),
    )

    @BeforeAll
    fun setup() = runBlocking {
        service = PropertyObservationService(database)

        val observations = mutableListOf<PropertyObservation>()

        // --- Step 1 fixture: aggregation-path taskScope ---
        // hdt-t-a: p1 observations under both NW and TUG.
        observations += property("hdt-t-a", "p1", 10.0, task = "NW")
        observations += property("hdt-t-a", "p1", 20.0, task = "NW")
        observations += property("hdt-t-a", "p1", 999.0, task = "TUG")
        // hdt-t-b: p1 observation with NO task key -- a state the database is not supposed to
        // contain, inserted solely to pin the fail-closed behaviour.
        observations += property("hdt-t-b", "p1", 50.0, task = null)

        // --- Step 2 fixture: presence-gate taskScope ---
        // hdt-t-c: p4 passes under NW (so it remains a candidate when taskScope=["NW"]), but its
        // only "acc" observation is tagged TUG.
        observations += property("hdt-t-c", "p4", 100.0, task = "NW")
        observations += sensor("hdt-t-c", "acc", task = "TUG")

        service.insertMany(observations)
        Unit
    }

    private val p1Comparisons = listOf(
        PropertyComparison(PropertyName("p1"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(0.0))
    )
    private val p4Comparisons = listOf(
        PropertyComparison(PropertyName("p4"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(0.0))
    )

    @Test
    fun `cohortExplore with taskScope aggregates only the observations under that task`() = runBlocking {
        val scoped = service.cohortExplore(comparisons = p1Comparisons, taskScope = listOf("NW"))
        val unscoped = service.cohortExplore(comparisons = p1Comparisons, taskScope = null)

        val scopedRowA = scoped.rows.single { it.hdtId.id == "hdt-t-a" }
        val unscopedRowA = unscoped.rows.single { it.hdtId.id == "hdt-t-a" }
        val scopedCountA = scopedRowA.properties.single { it.propertyName.value == "p1" }.count
        val unscopedCountA = unscopedRowA.properties.single { it.propertyName.value == "p1" }.count

        assertEquals(2L, scopedCountA, "only the two NW observations should be aggregated")
        assertEquals(3L, unscopedCountA, "all three observations should be aggregated without a taskScope")
    }

    @Test
    fun `observations without a task key are excluded by taskScope (fail closed)`() = runBlocking {
        val scoped = service.cohortExplore(comparisons = p1Comparisons, taskScope = listOf("NW"))
        val unscoped = service.cohortExplore(comparisons = p1Comparisons, taskScope = null)

        assertFalse("hdt-t-b" in scoped.rows.map { it.hdtId.id }, "hdt-t-b's task-less observation must be excluded under taskScope")
        assertTrue("hdt-t-b" in unscoped.rows.map { it.hdtId.id }, "hdt-t-b must be present without a taskScope")
    }

    @Test
    fun `taskScope on the presence gate excludes a twin whose only acc data is under a different task`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = p4Comparisons,
            taskScope = listOf("NW"),
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
        )
        assertFalse("hdt-t-c" in result.matches.map { it.hdtId.id }, "hdt-t-c's only acc observation is tagged TUG, not NW")
    }

    @Test
    fun `the same query without taskScope returns the twin`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = p4Comparisons,
            taskScope = null,
            modelPresence = listOf(ModelPresenceFilterDto(ModelName("acc"), ModelPresenceMode.HAS)),
        )
        assertTrue("hdt-t-c" in result.matches.map { it.hdtId.id }, "without taskScope, hdt-t-c's TUG acc observation counts")
    }
}
