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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.toJavaInstant
import kotlin.time.Instant

class PropertyObservationServiceComparisonTest : MongoIntegrationTest() {

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
            // hdt-pop-a: qualifies for both comparisons
            observation("hdt-pop-a", "temperature", 36.5, 0),
            observation("hdt-pop-a", "temperature", 37.0, 1),
            observation("hdt-pop-a", "temperature", 35.0, 2), // below threshold, still counts in population
            observation("hdt-pop-a", "heartRate", 65.0, 0),
            observation("hdt-pop-a", "heartRate", 70.0, 1),
            // hdt-pop-b: qualifies for both comparisons
            observation("hdt-pop-b", "temperature", 36.2, 0),
            observation("hdt-pop-b", "heartRate", 62.0, 0),
            observation("hdt-pop-b", "heartRate", 80.0, 1),
            // hdt-pop-c: never satisfies the temperature comparison -> excluded from matches
            // and therefore excluded from population stats too
            observation("hdt-pop-c", "temperature", 10.0, 0),
            observation("hdt-pop-c", "heartRate", 65.0, 0),
        )
        service.insertMany(observations)
    }

    @Test
    fun `population stats cover all observations of matched DTs, not just comparison-satisfying ones`() = runBlocking {
        val comparisons = listOf(
            PropertyComparison(PropertyName("temperature"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(36.0)),
            PropertyComparison(PropertyName("heartRate"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(60.0)),
        )

        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = comparisons,
            from = Instant.parse("2026-01-01T00:00:00Z").toJavaInstant(),
            to = Instant.parse("2026-03-01T00:00:00Z").toJavaInstant(),
        )

        val matchedHdtIds = result.matches.map { it.hdtId.id }.toSet()
        assertEquals(setOf("hdt-pop-a", "hdt-pop-b"), matchedHdtIds)

        val statsByProperty = result.populationStats.associateBy { it.propertyName.value }
        assertEquals(setOf("temperature", "heartRate"), statsByProperty.keys)

        val temperature = statsByProperty.getValue("temperature")
        // 35.0, 36.2, 36.5, 37.0 -- includes the 35.0 reading that failed the comparison
        assertEquals(4L, temperature.count)
        assertNotNull(temperature.avg)
        assertEquals(36.175, temperature.avg!!, 1e-6)
        assertEquals(35.0, temperature.min)
        assertEquals(37.0, temperature.max)
        assertNotNull(temperature.median)
        assertNotNull(temperature.p25)
        assertNotNull(temperature.p75)
        assertTrue(temperature.p25!! <= temperature.median!!)
        assertTrue(temperature.median!! <= temperature.p75!!)
        assertTrue(temperature.min!! <= temperature.p25!!)
        assertTrue(temperature.p75!! <= temperature.max!!)

        val heartRate = statsByProperty.getValue("heartRate")
        // 62.0, 65.0, 70.0, 80.0
        assertEquals(4L, heartRate.count)
        assertNotNull(heartRate.avg)
        assertEquals(69.25, heartRate.avg!!, 1e-6)
        assertEquals(62.0, heartRate.min)
        assertEquals(80.0, heartRate.max)
        assertNotNull(heartRate.median)
    }
}
