package routing.property

import MongoIntegrationTest
import configureSerialization
import db.property.PropertyDocument
import db.property.PropertyService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.ktor.client.request.put
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

class PropertyTagRoutesTest : MongoIntegrationTest() {

    private lateinit var propertyService: PropertyService

    @BeforeAll
    fun setup() {
        propertyService = PropertyService(database)
        val doc = PropertyDocument(
            hdtId = HdtId("tag-test-hdt"),
            modelId = ModelId("tag-test-model"),
            propertyId = PropertyId("tag-test-prop"),
            propertyName = PropertyName("heartRate"),
            description = "Heart rate",
            declaredType = "INT",
            tags = mapOf("domain" to "motor"),
        )
        propertyService.collection.insertOne(doc.toDocument())
    }

    @Test
    fun `PUT properties-propertyId-tags replaces tags and returns 200`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("hdts") {
                    route("/{id}") {
                        propertySpecRoutes(propertyService)
                    }
                }
            }
        }

        val response = client.put("/hdts/tag-test-hdt/properties/tag-test-prop/tags") {
            contentType(ContentType.Application.Json)
            setBody("""{"unit":"bpm","domain":"cardio"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("unit"), "Response body should contain unit key")
        assertTrue(body.contains("bpm"), "Response body should contain bpm value")
        assertTrue(body.contains("domain"), "Response body should contain domain key")
        assertTrue(body.contains("cardio"), "Response body should contain cardio value")

        // Follow-up read to verify stored map equals the request map exactly (no merge residue)
        val storedProps = propertyService.findByHdtId(HdtId("tag-test-hdt"))
        val stored = storedProps.first { it.propertyId == PropertyId("tag-test-prop") }
        assertEquals(mapOf("unit" to "bpm", "domain" to "cardio"), stored.tags)
    }

    @Test
    fun `PUT properties-propertyId-tags returns 404 for unknown propertyId`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("hdts") {
                    route("/{id}") {
                        propertySpecRoutes(propertyService)
                    }
                }
            }
        }

        val response = client.put("/hdts/tag-test-hdt/properties/non-existent-prop/tags") {
            contentType(ContentType.Application.Json)
            setBody("""{"foo":"bar"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT properties-propertyId-tags with empty map clears all tags`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("hdts") {
                    route("/{id}") {
                        propertySpecRoutes(propertyService)
                    }
                }
            }
        }

        val response = client.put("/hdts/tag-test-hdt/properties/tag-test-prop/tags") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val storedProps = propertyService.findByHdtId(HdtId("tag-test-hdt"))
        val stored = storedProps.first { it.propertyId == PropertyId("tag-test-prop") }
        assertEquals(emptyMap(), stored.tags)
    }

    @Test
    fun `PUT properties-propertyId-tags returns 400 for malformed body`() = testApplication {
        application {
            configureSerialization()
            routing {
                route("hdts") {
                    route("/{id}") {
                        propertySpecRoutes(propertyService)
                    }
                }
            }
        }

        val response = client.put("/hdts/tag-test-hdt/properties/tag-test-prop/tags") {
            contentType(ContentType.Application.Json)
            setBody("""{"oops":""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
