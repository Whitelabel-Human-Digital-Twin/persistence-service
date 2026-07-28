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
import routing.query.event.comparison.ComparisonOperator
import routing.query.event.comparison.PropertyComparison
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class PropertyObservationServiceRangeComparisonTest : MongoIntegrationTest() {

    private lateinit var service: PropertyObservationService

    private val ts = Instant.parse("2026-02-01T00:00:00Z")

    private fun observation(
        hdtId: String,
        propertyName: String,
        value: Double,
        offsetSeconds: Long,
    ) = PropertyObservation(
        hdtId = HdtId(hdtId),
        modelId = ModelId("$hdtId:vitals"),
        modelName = ModelName("vitals"),
        propertyId = PropertyId("$hdtId:vitals:$propertyName"),
        propertyName = PropertyName(propertyName),
        value = PropertyValue.DoublePropertyValue(value),
        timestamp = ts.plus(kotlin.time.Duration.parse("${offsetSeconds}s")),
        metadata = emptyMap(),
    )

    @BeforeAll
    fun setup() = runBlocking {
        service = PropertyObservationService(database)

        val observations = listOf(
            observation("hdt-range-in", "age", 85.0, 0),
            observation("hdt-range-above", "age", 95.0, 1),
            observation("hdt-range-below", "age", 20.0, 2),
            observation("hdt-range-split", "age", 95.0, 3),
            observation("hdt-range-split", "age", 20.0, 4),
            observation("hdt-range-multi", "age", 95.0, 5),
            observation("hdt-range-multi", "age", 85.0, 6),
            observation("hdt-cross", "temperature", 36.5, 7),
            observation("hdt-cross", "heartRate", 65.0, 8),
        )
        service.insertMany(observations)
        Unit
    }

    private val rangeComparisons = listOf(
        PropertyComparison(PropertyName("age"), ComparisonOperator.GT, PropertyValue.DoublePropertyValue(80.0)),
        PropertyComparison(PropertyName("age"), ComparisonOperator.LT, PropertyValue.DoublePropertyValue(90.0)),
    )

    private val from = Instant.parse("2026-01-01T00:00:00Z").toJavaInstant()
    private val to = Instant.parse("2026-03-01T00:00:00Z").toJavaInstant()

    @Test
    fun `range filter includes a DT whose observation falls inside the interval`() = runBlocking {
        val result = service.cohortExplore(comparisons = rangeComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assert(matched.contains("hdt-range-in"))
    }

    @Test
    fun `range filter excludes a DT whose only observation is above the interval`() = runBlocking {
        val result = service.cohortExplore(comparisons = rangeComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assert(!matched.contains("hdt-range-above"))
    }

    @Test
    fun `range filter excludes a DT whose only observation is below the interval`() = runBlocking {
        val result = service.cohortExplore(comparisons = rangeComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assert(!matched.contains("hdt-range-below"))
    }

    @Test
    fun `range filter excludes a DT satisfying the two bounds on different observations`() = runBlocking {
        val result = service.cohortExplore(comparisons = rangeComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assert(!matched.contains("hdt-range-split"))
    }

    @Test
    fun `range filter includes a DT with at least one in-range observation among out-of-range ones`() = runBlocking {
        val result = service.cohortExplore(comparisons = rangeComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assert(matched.contains("hdt-range-multi"))
    }

    @Test
    fun `range filter matches exactly the expected DT set`() = runBlocking {
        val result = service.cohortExplore(comparisons = rangeComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assertEquals(setOf("hdt-range-in", "hdt-range-multi"), matched)
    }

    @Test
    fun `cross-property comparisons still match across different observations`() = runBlocking {
        val crossComparisons = listOf(
            PropertyComparison(PropertyName("temperature"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(36.0)),
            PropertyComparison(PropertyName("heartRate"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(60.0)),
        )
        val result = service.cohortExplore(comparisons = crossComparisons, from = from, to = to)
        val matched = result.rows.map { it.hdtId.id }.toSet()
        assert(matched.contains("hdt-cross"))
    }

    @Test
    fun `matchedEvents excludes observations outside the range`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = rangeComparisons,
            from = from,
            to = to,
        )
        val multi = result.matches.first { it.hdtId.id == "hdt-range-multi" }
        assertEquals(1, multi.matchedEvents.size)
        assertEquals(PropertyValue.DoublePropertyValue(85.0), multi.matchedEvents.first().value)
    }
}
