package routing.hdt

import MongoIntegrationTest
import configureSerialization
import db.hdt.HdtService
import db.model.ModelService
import db.property.PropertyObservationService
import db.property.PropertyService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.distributed.serde.Stub
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import db.assembler.AssemblerService
import kotlin.test.assertEquals

class HdtBatchRoutesTest : MongoIntegrationTest() {

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
    }

    @Test
    fun `POST hdts batch inserts HDTs and returns 200`() = testApplication {
        application {
            configureSerialization()
            routing {
                humanDigitalTwinRoutes(hdtService, modelService, observationService, propertyService, assemblerService)
            }
        }

        val hdts = listOf(
            HumanDigitalTwin(
                hdtId = HdtId("hdt-batch-insert-1"),
                physicalInterfaces = emptyList(),
                digitalInterfaces = emptyList(),
                storages = emptyList(),
                tags = emptyMap(),
                models = emptyList(),
            ),
            HumanDigitalTwin(
                hdtId = HdtId("hdt-batch-insert-2"),
                physicalInterfaces = emptyList(),
                digitalInterfaces = emptyList(),
                storages = emptyList(),
                tags = emptyMap(),
                models = emptyList(),
            ),
        )
        val body = Stub.hdtJson.encodeToString(
            ListSerializer(serializer<HumanDigitalTwin>()),
            hdts
        )

        val response = client.post("/hdts/batch") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PUT hdts batch upserts HDTs and returns 200`() = testApplication {
        application {
            configureSerialization()
            routing {
                humanDigitalTwinRoutes(hdtService, modelService, observationService, propertyService, assemblerService)
            }
        }

        val hdts = listOf(
            HumanDigitalTwin(
                hdtId = HdtId("hdt-batch-upsert-1"),
                physicalInterfaces = emptyList(),
                digitalInterfaces = emptyList(),
                storages = emptyList(),
                tags = mapOf("env" to "test"),
                models = emptyList(),
            ),
        )
        val body = Stub.hdtJson.encodeToString(
            ListSerializer(serializer<HumanDigitalTwin>()),
            hdts
        )

        val response = client.put("/hdts/batch") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
