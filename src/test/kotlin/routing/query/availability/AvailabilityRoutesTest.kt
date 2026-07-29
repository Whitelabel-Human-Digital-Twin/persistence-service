package routing.query.availability

import MongoIntegrationTest
import configureSerialization
import db.property.PropertyObservationService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AvailabilityRoutesTest : MongoIntegrationTest() {

    private lateinit var observationService: PropertyObservationService

    @BeforeAll
    fun setup() = runBlocking {
        observationService = PropertyObservationService(database)

        val ts = Instant.parse("2026-04-01T00:00:00Z")
        val observations = listOf(
            PropertyObservation(
                hdtId = HdtId("route-avail-hdt"),
                modelId = ModelId("route-avail-hdt:acc"),
                modelName = ModelName("acc"),
                propertyId = PropertyId("route-avail-hdt:acc:value"),
                propertyName = PropertyName("value"),
                value = PropertyValue.DoublePropertyValue(1.0),
                timestamp = ts,
                metadata = emptyMap(),
            ),
        )
        observationService.insertMany(observations)
        Unit
    }

    @Test
    fun `POST query hdts by-model returns 200 with availability data`() = testApplication {
        application {
            configureSerialization()
            routing { availabilityRoutes(observationService) }
        }
        val response = client.post("/query/hdts/by-model") {
            contentType(ContentType.Application.Json)
            setBody("""{"modelNames":["acc"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"hdtId\""), "Response should contain hdtId")
        assertTrue(body.contains("\"modelName\""), "Response should contain modelName")
        assertTrue(body.contains("\"observationCount\""), "Response should contain observationCount")
        assertTrue(body.contains("\"firstTimestamp\""), "Response should contain firstTimestamp")
        assertTrue(body.contains("route-avail-hdt"), "Response should contain the seeded HDT id")
    }

    @Test
    fun `POST query hdts by-model with a minimal empty body returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { availabilityRoutes(observationService) }
        }
        val response = client.post("/query/hdts/by-model") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("route-avail-hdt"))
    }
}
