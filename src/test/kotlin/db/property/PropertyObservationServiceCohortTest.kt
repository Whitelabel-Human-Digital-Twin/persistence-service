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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class PropertyObservationServiceCohortTest : MongoIntegrationTest() {

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
            // hdt-cohort-a: matches both comparisons; has temperature, heartRate AND spo2
            observation("hdt-cohort-a", "temperature", 36.5, 0),
            observation("hdt-cohort-a", "temperature", 37.0, 1), // latest
            observation("hdt-cohort-a", "heartRate", 65.0, 0),
            observation("hdt-cohort-a", "heartRate", 70.0, 1), // latest
            observation("hdt-cohort-a", "spo2", 98.0, 0), // only hdt-cohort-a has spo2 -> sparse column

            // hdt-cohort-b: matches both comparisons; has temperature and heartRate only
            observation("hdt-cohort-b", "temperature", 36.2, 0), // only + latest
            observation("hdt-cohort-b", "heartRate", 62.0, 0),
            observation("hdt-cohort-b", "heartRate", 80.0, 1), // latest

            // hdt-cohort-c: never satisfies the temperature comparison -> excluded entirely
            observation("hdt-cohort-c", "temperature", 10.0, 0),
            observation("hdt-cohort-c", "heartRate", 65.0, 0),
        )
        service.insertMany(observations)
        Unit
    }

    private val comparisons = listOf(
        PropertyComparison(PropertyName("temperature"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(36.0)),
        PropertyComparison(PropertyName("heartRate"), ComparisonOperator.GTE, PropertyValue.DoublePropertyValue(60.0)),
    )

    @Test
    fun `cohortExplore returns one row per matched DT with sparse per-property stats and a population summary`() = runBlocking {
        val result = service.cohortExplore(
            comparisons = comparisons,
            from = Instant.parse("2026-01-01T00:00:00Z").toJavaInstant(),
            to = Instant.parse("2026-03-01T00:00:00Z").toJavaInstant(),
        )

        val rowsByHdt = result.rows.associateBy { it.hdtId.id }
        assertEquals(setOf("hdt-cohort-a", "hdt-cohort-b"), rowsByHdt.keys)

        // hdt-cohort-a: dense row, has temperature, heartRate AND spo2
        val rowA = rowsByHdt.getValue("hdt-cohort-a")
        val cellsA = rowA.properties.associateBy { it.propertyName.value }
        assertEquals(setOf("temperature", "heartRate", "spo2"), cellsA.keys)

        val temperatureA = cellsA.getValue("temperature")
        assertEquals(PropertyValue.DoublePropertyValue(37.0), temperatureA.value) // latest-in-window
        assertEquals(2L, temperatureA.count)
        assertNotNull(temperatureA.avg)
        assertEquals(36.75, temperatureA.avg!!, 1e-6)
        assertEquals(36.5, temperatureA.min)
        assertEquals(37.0, temperatureA.max)
        assertNotNull(temperatureA.median)
        assertNotNull(temperatureA.p25)
        assertNotNull(temperatureA.p75)
        assertTrue(temperatureA.p25!! <= temperatureA.median!!)
        assertTrue(temperatureA.median!! <= temperatureA.p75!!)

        val heartRateA = cellsA.getValue("heartRate")
        assertEquals(PropertyValue.DoublePropertyValue(70.0), heartRateA.value)
        assertEquals(2L, heartRateA.count)
        assertEquals(67.5, heartRateA.avg!!, 1e-6)

        val spo2A = cellsA.getValue("spo2")
        assertEquals(PropertyValue.DoublePropertyValue(98.0), spo2A.value)
        assertEquals(1L, spo2A.count)
        assertEquals(98.0, spo2A.avg)
        assertEquals(98.0, spo2A.min)
        assertEquals(98.0, spo2A.max)

        // hdt-cohort-b: sparse row, no spo2 cell at all
        val rowB = rowsByHdt.getValue("hdt-cohort-b")
        val cellsB = rowB.properties.associateBy { it.propertyName.value }
        assertEquals(setOf("temperature", "heartRate"), cellsB.keys)
        assertNull(cellsB["spo2"])

        val temperatureB = cellsB.getValue("temperature")
        assertEquals(PropertyValue.DoublePropertyValue(36.2), temperatureB.value)
        assertEquals(1L, temperatureB.count)
        assertEquals(36.2, temperatureB.avg!!, 1e-6)

        val heartRateB = cellsB.getValue("heartRate")
        assertEquals(PropertyValue.DoublePropertyValue(80.0), heartRateB.value)
        assertEquals(2L, heartRateB.count)
        assertEquals(71.0, heartRateB.avg!!, 1e-6)

        // Population summary: canonical column set across the whole matched cohort
        val statsByProperty = result.populationStats.associateBy { it.propertyName.value }
        assertEquals(setOf("temperature", "heartRate", "spo2"), statsByProperty.keys)

        val temperaturePop = statsByProperty.getValue("temperature")
        // 36.5, 37.0 (a) + 36.2 (b) -- hdt-cohort-c's 10.0 must NOT leak in
        assertEquals(3L, temperaturePop.count)
        assertNotNull(temperaturePop.avg)
        assertEquals((36.5 + 37.0 + 36.2) / 3.0, temperaturePop.avg!!, 1e-6)
        assertEquals(36.2, temperaturePop.min)
        assertEquals(37.0, temperaturePop.max)

        val heartRatePop = statsByProperty.getValue("heartRate")
        // 65, 70 (a) + 62, 80 (b) -- hdt-cohort-c's 65.0 must NOT leak in
        assertEquals(4L, heartRatePop.count)
        assertEquals((65.0 + 70.0 + 62.0 + 80.0) / 4.0, heartRatePop.avg!!, 1e-6)

        val spo2Pop = statsByProperty.getValue("spo2")
        assertEquals(1L, spo2Pop.count)
        assertEquals(98.0, spo2Pop.avg)
    }

    @Test
    fun `observationsByComparisonsAggregate is unaffected by the matchedHdtIds extraction`() = runBlocking {
        val result = service.observationsByComparisonsAggregate(
            propertyComparisons = comparisons,
            from = Instant.parse("2026-01-01T00:00:00Z").toJavaInstant(),
            to = Instant.parse("2026-03-01T00:00:00Z").toJavaInstant(),
        )

        val matchedHdtIds = result.matches.map { it.hdtId.id }.toSet()
        assertEquals(setOf("hdt-cohort-a", "hdt-cohort-b"), matchedHdtIds)

        val statsByProperty = result.populationStats.associateBy { it.propertyName.value }
        assertEquals(setOf("temperature", "heartRate"), statsByProperty.keys)
        assertEquals(3L, statsByProperty.getValue("temperature").count)
        assertEquals(4L, statsByProperty.getValue("heartRate").count)
    }
}
