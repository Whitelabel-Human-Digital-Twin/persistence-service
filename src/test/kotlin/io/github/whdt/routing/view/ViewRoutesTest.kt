package io.github.whdt.routing.view

import io.github.whdt.MongoIntegrationTest
import io.github.whdt.configureSerialization
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.hdt.HumanDigitalTwinDocument
import io.github.whdt.db.property.PropertyDocument
import io.github.whdt.db.property.PropertyService
import io.github.whdt.db.view.ViewService
import io.ktor.client.request.delete
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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewRoutesTest : MongoIntegrationTest() {

    private lateinit var viewService: ViewService
    private lateinit var hdtService: HdtService
    private lateinit var propertyService: PropertyService

    @BeforeAll
    fun setup() {
        viewService = ViewService(database)
        hdtService = HdtService(database)
        propertyService = PropertyService(database)

        // Insert two test HDTs
        val hdt1 = HumanDigitalTwinDocument(
            hdtId = HdtId("route-hdt-1"),
            physicalInterfaces = emptyList(),
            digitalInterfaces = emptyList(),
            storages = emptyList(),
            tags = emptyMap(),
        )
        val hdt2 = HumanDigitalTwinDocument(
            hdtId = HdtId("route-hdt-2"),
            physicalInterfaces = emptyList(),
            digitalInterfaces = emptyList(),
            storages = emptyList(),
            tags = emptyMap(),
        )
        hdtService.collection.insertOne(hdt1.toDocument())
        hdtService.collection.insertOne(hdt2.toDocument())

        // Insert properties for both HDTs
        val propDoc1 = PropertyDocument(
            hdtId = HdtId("route-hdt-1"),
            modelId = ModelId("model-route"),
            propertyId = PropertyId("rp1"),
            propertyName = PropertyName("temperature"),
            description = "Temperature sensor",
            declaredType = "DOUBLE",
            tags = mapOf("domain" to "thermal"),
        )
        val propDoc2 = PropertyDocument(
            hdtId = HdtId("route-hdt-2"),
            modelId = ModelId("model-route"),
            propertyId = PropertyId("rp2"),
            propertyName = PropertyName("pressure"),
            description = "Pressure sensor",
            declaredType = "DOUBLE",
            tags = mapOf("domain" to "mechanical"),
        )
        propertyService.collection.insertOne(propDoc1.toDocument())
        propertyService.collection.insertOne(propDoc2.toDocument())
    }

    private fun app() = testApplication {
        application {
            configureSerialization()
            routing {
                viewRoutes(viewService, hdtService, propertyService)
            }
        }
    }

    @Test
    fun `GET views returns 200 with list`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        val response = client.get("/views")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("["))
    }

    @Test
    fun `POST views creates a view and returns 201`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        val response = client.post("/views") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"route-create-test","predicate":null,"groupByKeys":[]}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("route-create-test"))
    }

    @Test
    fun `GET views by name returns 200 for existing view`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        // Create it first
        client.post("/views") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"route-get-test","predicate":null,"groupByKeys":[]}""")
        }
        val response = client.get("/views/route-get-test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("route-get-test"))
    }

    @Test
    fun `GET views by name returns 404 for unknown view`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        val response = client.get("/views/nonexistent-view-xyz")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT views by name upserts the view`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        val response = client.put("/views/route-put-test") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"route-put-test","predicate":null,"groupByKeys":["domain"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("domain"))
    }

    @Test
    fun `DELETE views by name returns 204 then 404`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        // Create then delete
        client.post("/views") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"route-delete-test","predicate":null,"groupByKeys":[]}""")
        }
        val deleteResponse = client.delete("/views/route-delete-test")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val secondDelete = client.delete("/views/route-delete-test")
        assertEquals(HttpStatusCode.NotFound, secondDelete.status)
    }

    @Test
    fun `POST execute with non-existent view returns 404`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        val response = client.post("/views/no-such-view/execute") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST execute with no scope body runs against all HDTs`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        // Create the view to execute
        client.post("/views") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"exec-all-test","predicate":null,"groupByKeys":[]}""")
        }
        val response = client.post("/views/exec-all-test/execute") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Response is a JSON object keyed by HDT id
        assertTrue(body.startsWith("{"))
        assertTrue(body.contains("route-hdt-1"))
        assertTrue(body.contains("route-hdt-2"))
    }

    @Test
    fun `POST execute with scoped hdtIds runs against only that subset`() = testApplication {
        application {
            configureSerialization()
            routing { viewRoutes(viewService, hdtService, propertyService) }
        }
        client.post("/views") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"exec-scope-test","predicate":null,"groupByKeys":[]}""")
        }
        val response = client.post("/views/exec-scope-test/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"hdtIds":["route-hdt-1"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("route-hdt-1"))
        assertFalse(body.contains("route-hdt-2"))
    }
}
