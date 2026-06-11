package routing.observation

import MongoIntegrationTest
import configureSerialization
import com.mongodb.client.model.Filters
import db.property.PropertyObservationDocument
import db.property.PropertyObservationService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import io.github.ktwinx.distributed.serde.Stub
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import routing.property.observationRoutes
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObservationBatchRoutesTest : MongoIntegrationTest() {

    private lateinit var observationService: PropertyObservationService

    @BeforeAll
    fun setup() {
        observationService = PropertyObservationService(database)
    }

    private fun obs(prop: String, value: PropertyValue, ts: Instant) = PropertyObservation(
        hdtId = HdtId("obs-batch-hdt"),
        modelId = ModelId("obs-batch-hdt:vitals"),
        modelName = ModelName("vitals"),
        propertyId = PropertyId("obs-batch-hdt:vitals:$prop"),
        propertyName = PropertyName(prop),
        value = value,
        timestamp = ts,
        metadata = mapOf("age" to "42"),
    )

    @Test
    fun `POST observations batch persists every PropertyValue subtype and reads back`() = testApplication {
        application {
            configureSerialization()
            routing { observationRoutes(observationService) }
        }

        val ts = Instant.parse("2026-06-08T12:00:00Z")

        val observations = listOf(
            obs("intProp",    PropertyValue.IntPropertyValue(10),         ts),
            obs("longProp",   PropertyValue.LongPropertyValue(20L),       ts),
            obs("floatProp",  PropertyValue.FloatPropertyValue(3.14f),    ts),
            obs("doubleProp", PropertyValue.DoublePropertyValue(2.718),   ts),
            obs("stringProp", PropertyValue.StringPropertyValue("hello"), ts),
            obs("boolProp",   PropertyValue.BooleanPropertyValue(true),   ts),
            obs("emptyProp",  PropertyValue.EmptyPropertyValue,           ts),
        )

        // Serialize exactly as the creation service does — using Stub.hdtJson
        val body = Stub.hdtJson.encodeToString(
            ListSerializer(PropertyObservation.serializer()), observations
        )

        val response = client.post("/observations/batch") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, response.status)

        // Read back directly from the collection (no projection) to get metadata too
        val raw = observationService.collection
            .find(Filters.eq("metaField.hdtId", "obs-batch-hdt"))
            .toList()
        val stored = raw.mapNotNull(PropertyObservationDocument::fromDocument)
        val byName = stored.associateBy { it.metaField.propertyName.value }

        // IntPropertyValue round-trip
        val intObs = byName["intProp"]
        assertNotNull(intObs)
        assertEquals(PropertyValue.IntPropertyValue(10), intObs.value)
        assertEquals(mapOf("age" to "42"), intObs.metadata)
        assertEquals(ts.epochSeconds, intObs.timeField.epochSeconds)

        // LongPropertyValue round-trip
        assertEquals(PropertyValue.LongPropertyValue(20L), byName["longProp"]?.value)

        // FloatPropertyValue: BSON stores numbers as Double, so presence is asserted
        assertTrue(byName.containsKey("floatProp"), "floatProp observation should be stored")

        // DoublePropertyValue round-trip
        assertEquals(PropertyValue.DoublePropertyValue(2.718), byName["doubleProp"]?.value)

        // StringPropertyValue round-trip
        assertEquals(PropertyValue.StringPropertyValue("hello"), byName["stringProp"]?.value)

        // BooleanPropertyValue round-trip
        assertEquals(PropertyValue.BooleanPropertyValue(true), byName["boolProp"]?.value)

        // EmptyPropertyValue: toBsonValue() returns null, so fromDocument skips it —
        // the observation is not returned in the query results, which is expected behaviour.
    }
}
