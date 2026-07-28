package routing.query.event

import MongoIntegrationTest
import configureSerialization
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import routing.query.event.values.propertyValuesRoutes
import routing.query.event.stats.propertyStatsRoutes
import routing.query.event.comparison.propertyComparisonRoutes
import routing.query.event.cohort.cohortRoutes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObservationQueryRoutesTest : MongoIntegrationTest() {

    private lateinit var observationService: PropertyObservationService

    @BeforeAll
    fun setup() {
        observationService = PropertyObservationService(database)

        // Seed observations for query tests
        val ts = Instant.parse("2026-01-15T10:00:00Z")
        val observations = listOf(
            PropertyObservation(
                hdtId = HdtId("query-hdt"),
                modelId = ModelId("query-hdt:vitals"),
                modelName = ModelName("vitals"),
                propertyId = PropertyId("query-hdt:vitals:temperature"),
                propertyName = PropertyName("temperature"),
                value = PropertyValue.DoublePropertyValue(36.6),
                timestamp = ts,
                metadata = mapOf("source" to "sensor", "age" to "34", "task" to "walking"),
            ),
            PropertyObservation(
                hdtId = HdtId("query-hdt"),
                modelId = ModelId("query-hdt:vitals"),
                modelName = ModelName("vitals"),
                propertyId = PropertyId("query-hdt:vitals:temperature"),
                propertyName = PropertyName("temperature"),
                value = PropertyValue.DoublePropertyValue(37.1),
                timestamp = Instant.parse("2026-01-15T11:00:00Z"),
                metadata = mapOf("source" to "sensor", "age" to "34", "task" to "running"),
            ),
        )
        val docs = observations
            .map { PropertyObservationDocument.fromObservation(it) }
            .map(PropertyObservationDocument::toDocument)
        observationService.collection.insertMany(docs)
    }

    @Test
    fun `POST query event values valuesById returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { propertyValuesRoutes(observationService) }
        }
        val response = client.post("/query/event/values/valuesById") {
            contentType(ContentType.Application.Json)
            setBody("""[{"propertyId":"query-hdt:vitals:temperature","from":"2026-01-15T00:00:00Z","to":"2026-01-16T00:00:00Z"}]""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("temperature"), "Response should contain property name")
        assertTrue(body.contains("\"age\""), "Response should include the stored age metadata key")
        assertTrue(body.contains("\"walking\""), "Response should include the stored task metadata value")
    }

    @Test
    fun `POST query event values valuesByName returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { propertyValuesRoutes(observationService) }
        }
        val response = client.post("/query/event/values/valuesByName") {
            contentType(ContentType.Application.Json)
            setBody("""[{"hdtId":"query-hdt","propertyName":"temperature","from":"2026-01-15T00:00:00Z","to":"2026-01-16T00:00:00Z"}]""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("temperature"), "Response should contain property name")
        assertTrue(body.contains("\"age\""), "Response should include the stored age metadata key")
    }

    @Test
    fun `POST query event values history returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { propertyValuesRoutes(observationService) }
        }
        val response = client.post("/query/event/values/history") {
            contentType(ContentType.Application.Json)
            setBody("""[{"hdtId":"query-hdt","propertyName":"temperature"}]""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("temperature"), "Response should include history for the property")
        assertTrue(body.contains("\"age\""), "History response should include the stored age metadata key")
        assertTrue(body.contains("\"walking\""), "History response should include the stored task metadata value")
        assertTrue(body.contains("\"running\""), "History response should include the stored task metadata value")
    }

    @Test
    fun `POST query event stats returns 200 with aggregate data`() = testApplication {
        application {
            configureSerialization()
            routing { propertyStatsRoutes(observationService) }
        }
        val response = client.post("/query/event/stats") {
            contentType(ContentType.Application.Json)
            setBody("""{"hdtIds":["query-hdt"],"modelIds":[],"modelNames":[],"propertyName":"temperature"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("query-hdt"), "Response should contain the HDT id")
    }

    @Test
    fun `POST query event comparison returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { propertyComparisonRoutes(observationService) }
        }
        val response = client.post("/query/event/comparison") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[{"propertyName":"temperature","comparison":"GTE","value":36.0}],"modelNames":null}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"populationStats\""), "Response should contain populationStats")
        assertTrue(body.contains("\"temperature\""), "populationStats should include the temperature property")
        assertTrue(body.contains("\"median\""), "populationStats entries should include median")
        assertTrue(body.contains("\"p25\""), "populationStats entries should include p25")
        assertTrue(body.contains("\"p75\""), "populationStats entries should include p75")
    }

    @Test
    fun `POST query cohort returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { cohortRoutes(observationService) }
        }
        val response = client.post("/query/cohort") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[{"propertyName":"temperature","comparison":"GTE","value":30.0}],"modelNames":null}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"rows\""), "Response should contain rows")
        assertTrue(body.contains("\"populationStats\""), "Response should contain populationStats")
        assertTrue(body.contains("\"value\""), "rows should contain the latest value per property")
        assertTrue(body.contains("\"median\""), "rows should contain median")
        assertTrue(body.contains("\"p25\""), "rows should contain p25")
        assertTrue(body.contains("\"p75\""), "rows should contain p75")
    }

    @Test
    fun `POST query cohort with a same-property range excludes a DT with no single observation inside the interval`() = testApplication {
        application {
            configureSerialization()
            routing { cohortRoutes(observationService) }
        }
        // query-hdt's readings are 36.6 and 37.1 -- both satisfy `temperature < 40` individually
        // (which the pre-fix OR-based gate would accept), but neither satisfies
        // `temperature > 38 AND temperature < 40` on a single document, so a correct
        // document-level range filter must exclude query-hdt here.
        val response = client.post("/query/cohort") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"comparisons":[{"propertyName":"temperature","comparison":"GT","value":38.0},{"propertyName":"temperature","comparison":"LT","value":40.0}],"modelNames":null}"""
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(!body.contains("query-hdt"), "no single temperature reading falls inside (38.0, 40.0), so query-hdt must not match")
    }
}
