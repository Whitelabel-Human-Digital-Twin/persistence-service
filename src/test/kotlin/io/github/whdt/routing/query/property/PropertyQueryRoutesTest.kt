package io.github.whdt.routing.query.property

import io.github.whdt.MongoIntegrationTest
import io.github.whdt.configureSerialization
import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.db.property.PropertyDocument
import io.github.whdt.db.property.PropertyService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertyQueryRoutesTest : MongoIntegrationTest() {

    private lateinit var propertyService: PropertyService

    @BeforeAll
    fun setup() {
        propertyService = PropertyService(database)
        val doc = PropertyDocument(
            hdtId = HdtId("hdt-route-test"),
            modelId = ModelId("model-route-test"),
            propertyId = PropertyId("rp1"),
            propertyName = PropertyName("rp1"),
            description = "test",
            declaredType = "STRING",
            tags = mapOf("domain" to "motor"),
        )
        propertyService.collection.insertOne(doc.toDocument())
    }

    @Test
    fun `POST query-property returns 200 with matching array for valid predicate`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("/query") {
                    propertyQueryRoutes(propertyService)
                }
            }
        }

        val response = client.post("/query/property") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"eq","key":"domain","value":"motor"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("rp1"), "Expected propertyId 'rp1' in response body")
    }

    @Test
    fun `POST query-property returns 400 on malformed body`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("/query") {
                    propertyQueryRoutes(propertyService)
                }
            }
        }

        val response = client.post("/query/property") {
            contentType(ContentType.Application.Json)
            setBody("""{"notValid": true}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
