package routing.model

import MongoIntegrationTest
import configureSerialization
import db.model.ModelService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.ModelDescription
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.WellKnownFormats
import io.github.ktwinx.core.hdt.model.property.Property
import io.github.ktwinx.distributed.serde.Stub
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelRoutesTest : MongoIntegrationTest() {

    private lateinit var modelService: ModelService

    @BeforeAll
    fun setup() {
        modelService = ModelService(database)
    }

    private fun makeModel(hdtId: String, name: String) = Model(
        hdtId = HdtId(hdtId),
        name = ModelName(name),
        description = ModelDescription("Test model $name"),
        properties = listOf<Property>(),
    )

    @Test
    fun `GET models returns 200 with list`() = testApplication {
        application {
            configureSerialization()
            routing { modelsRoutes(modelService) }
        }
        val response = client.get("/models")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("["))
    }

    @Test
    fun `POST models creates a model and returns 201`() = testApplication {
        application {
            configureSerialization()
            routing { modelsRoutes(modelService) }
        }
        val model = makeModel("hdt-model-create", "body")
        val body = Stub.hdtJson.encodeToString(serializer<Model>(), model)

        val response = client.post("/models") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `PUT models upserts a model and returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { modelsRoutes(modelService) }
        }
        val model = makeModel("hdt-model-upsert", "vitals")
        val body = Stub.hdtJson.encodeToString(serializer<Model>(), model)

        val response = client.put("/models") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST models batch inserts a list of models and returns 201`() = testApplication {
        application {
            configureSerialization()
            routing { modelsRoutes(modelService) }
        }
        val models: List<Model> = listOf(
            makeModel("hdt-model-batch", "m1"),
            makeModel("hdt-model-batch", "m2"),
        )
        val body = Stub.hdtJson.encodeToString(ListSerializer(serializer<Model>()), models)

        val response = client.post("/models/batch") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `PUT models batch upserts a list of models and returns 200`() = testApplication {
        application {
            configureSerialization()
            routing { modelsRoutes(modelService) }
        }
        val models: List<Model> = listOf(
            makeModel("hdt-model-batch-upsert", "batch-upsert-m1"),
        )
        val body = Stub.hdtJson.encodeToString(ListSerializer(serializer<Model>()), models)

        val response = client.put("/models/batch") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
