package routing.query.event

import MongoIntegrationTest
import configureSerialization
import db.hdt.HdtService
import db.property.PropertyObservationService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
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
import routing.query.event.cohort.cohortRoutes
import routing.query.event.comparison.propertyComparisonRoutes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ModelPresenceRoutesTest : MongoIntegrationTest() {

    private lateinit var observationService: PropertyObservationService
    private lateinit var hdtService: HdtService

    @BeforeAll
    fun setup() = runBlocking {
        observationService = PropertyObservationService(database)
        hdtService = HdtService(database)

        val ts = Instant.parse("2026-07-01T00:00:00Z")
        observationService.insertMany(
            listOf(
                PropertyObservation(
                    hdtId = HdtId("route-presence-a"),
                    modelId = ModelId("route-presence-a:acc"),
                    modelName = ModelName("acc"),
                    propertyId = PropertyId("route-presence-a:acc:value"),
                    propertyName = PropertyName("value"),
                    value = PropertyValue.DoublePropertyValue(1.0),
                    timestamp = ts,
                    metadata = emptyMap(),
                ),
                // route-presence-b has no acc data, but does have other observations so it can
                // still surface as a cohort row.
                PropertyObservation(
                    hdtId = HdtId("route-presence-b"),
                    modelId = ModelId("route-presence-b:vitals"),
                    modelName = ModelName("vitals"),
                    propertyId = PropertyId("route-presence-b:vitals:value"),
                    propertyName = PropertyName("value"),
                    value = PropertyValue.DoublePropertyValue(1.0),
                    timestamp = ts,
                    metadata = emptyMap(),
                ),
            )
        )

        hdtService.create(HumanDigitalTwin(hdtId = HdtId("route-presence-a")))
        hdtService.create(HumanDigitalTwin(hdtId = HdtId("route-presence-b")))
        Unit
    }

    @Test
    fun `POST query event comparison with modelPresence and no comparisons returns 200 with the gated result`() = testApplication {
        application {
            configureSerialization()
            routing { propertyComparisonRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/event/comparison") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[],"modelPresence":[{"modelName":"acc","mode":"HAS"}]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("route-presence-a"), "twin with acc data must be present")
        assertTrue(!body.contains("route-presence-b"), "twin without acc data must be excluded")
    }

    @Test
    fun `POST query event comparison with neither comparisons nor modelPresence returns 400`() = testApplication {
        application {
            configureSerialization()
            routing { propertyComparisonRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/event/comparison") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST query cohort with modelPresence and no comparisons returns 200 with the gated result`() = testApplication {
        application {
            configureSerialization()
            routing { cohortRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/cohort") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[],"modelPresence":[{"modelName":"acc","mode":"HAS_NOT"}]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("route-presence-b"), "twin without acc data must be present")
        assertTrue(!body.contains("route-presence-a"), "twin with acc data must be excluded")
    }

    @Test
    fun `POST query cohort with neither comparisons nor modelPresence returns 400`() = testApplication {
        application {
            configureSerialization()
            routing { cohortRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/cohort") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST query event comparison with a task key in metadataFilters returns 400 naming taskScope`() = testApplication {
        application {
            configureSerialization()
            routing { propertyComparisonRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/event/comparison") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[],"modelPresence":[{"modelName":"acc","mode":"HAS"}],"metadataFilters":{"task":["NW"]}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("taskScope"), "error message must point at taskScope")
    }

    @Test
    fun `POST query event comparison with taskScope returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { propertyComparisonRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/event/comparison") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[],"modelPresence":[{"modelName":"acc","mode":"HAS"}],"taskScope":["NW"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST query cohort with a task key in metadataFilters returns 400 naming taskScope`() = testApplication {
        application {
            configureSerialization()
            routing { cohortRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/cohort") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[],"modelPresence":[{"modelName":"acc","mode":"HAS"}],"metadataFilters":{"task":["NW"]}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("taskScope"), "error message must point at taskScope")
    }

    @Test
    fun `POST query cohort with taskScope returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { cohortRoutes(observationService, hdtService) }
        }
        val response = client.post("/query/cohort") {
            contentType(ContentType.Application.Json)
            setBody("""{"comparisons":[],"modelPresence":[{"modelName":"acc","mode":"HAS"}],"taskScope":["NW"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
