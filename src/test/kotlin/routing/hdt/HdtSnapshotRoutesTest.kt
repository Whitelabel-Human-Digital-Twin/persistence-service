package routing.hdt

import MongoIntegrationTest
import configureSerialization
import db.assembler.AssemblerService
import db.hdt.HdtService
import db.model.ModelService
import db.property.PropertyDocument
import db.property.PropertyObservationService
import db.property.PropertyService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalKtorApi::class)
class HdtSnapshotRoutesTest : MongoIntegrationTest() {

    private lateinit var hdtService: HdtService
    private lateinit var modelService: ModelService
    private lateinit var propertyService: PropertyService
    private lateinit var observationService: PropertyObservationService
    private lateinit var assemblerService: AssemblerService

    @BeforeAll
    fun setup() {
        hdtService = HdtService(database)
        modelService = ModelService(database)
        propertyService = PropertyService(database)
        observationService = PropertyObservationService(database)
        assemblerService = AssemblerService(hdtService, modelService, propertyService, observationService)

        // Insert a property spec so the snapshot endpoint has data to return
        val prop = PropertyDocument(
            hdtId = HdtId("snapshot-hdt"),
            modelId = ModelId("snapshot-hdt:vitals"),
            propertyId = PropertyId("snapshot-hdt:vitals:temp"),
            propertyName = PropertyName("temperature"),
            description = "Body temperature",
            declaredType = "DOUBLE",
            tags = emptyMap(),
        )
        propertyService.collection.insertOne(prop.toDocument())
    }

    @Test
    fun `GET hdts snapshot returns 200 with property snapshot`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("hdts") {
                    route("/{id}") {
                        hdtAssemblerRoutes(assemblerService)
                    }
                }
            }
        }

        val response = client.get("/hdts/snapshot-hdt/snapshot")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("temperature"), "Response should contain property name 'temperature'")
        assertTrue(body.contains("snapshot-hdt:vitals:temp"), "Response should contain propertyId")
    }

    @Test
    fun `GET hdts snapshot returns 500 when HDT has no property specs`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("hdts") {
                    route("/{id}") {
                        hdtAssemblerRoutes(assemblerService)
                    }
                }
            }
        }

        val response = client.get("/hdts/unknown-hdt-xyz/snapshot")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
