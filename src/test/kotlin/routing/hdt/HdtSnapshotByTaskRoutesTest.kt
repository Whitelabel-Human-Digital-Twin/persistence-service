package routing.hdt

import MongoIntegrationTest
import configureSerialization
import db.assembler.AssemblerService
import db.hdt.HdtService
import db.model.ModelService
import db.property.PropertyObservationDocument
import db.property.PropertyObservationService
import db.property.PropertyService
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalKtorApi::class)
class HdtSnapshotByTaskRoutesTest : MongoIntegrationTest() {

    private lateinit var hdtService: HdtService
    private lateinit var modelService: ModelService
    private lateinit var propertyService: PropertyService
    private lateinit var observationService: PropertyObservationService
    private lateinit var assemblerService: AssemblerService

    private val hdtId = "snapshot-by-task-hdt"

    @BeforeAll
    fun setup() {
        hdtService = HdtService(database)
        modelService = ModelService(database)
        propertyService = PropertyService(database)
        observationService = PropertyObservationService(database)
        assemblerService = AssemblerService(hdtService, modelService, propertyService, observationService)

        val t0 = Instant.parse("2026-01-01T10:00:00Z")
        val t1 = Instant.parse("2026-01-01T11:00:00Z")

        fun obs(task: String?, value: Int, ts: Instant): PropertyObservation {
            val metadata = if (task != null) mapOf("task" to task) else emptyMap()
            return PropertyObservation(
                hdtId = HdtId(hdtId),
                modelId = ModelId("$hdtId:vitals"),
                modelName = ModelName("vitals"),
                propertyId = PropertyId("$hdtId:vitals:hr"),
                propertyName = PropertyName("hr"),
                value = PropertyValue.IntPropertyValue(value),
                timestamp = ts,
                metadata = metadata,
            )
        }

        // (hr, task=nw) value 70 at t0
        observationService.collection.insertOne(PropertyObservationDocument.fromObservation(obs("nw", 70, t0)).toDocument())
        // (hr, task=tw) value 120 at t0
        observationService.collection.insertOne(PropertyObservationDocument.fromObservation(obs("tw", 120, t0)).toDocument())
        // (hr, task=nw) value 75 at t1 — later, should win for nw
        observationService.collection.insertOne(PropertyObservationDocument.fromObservation(obs("nw", 75, t1)).toDocument())
        // (hr, no task) value 99 at t0 — untagged bucket
        observationService.collection.insertOne(PropertyObservationDocument.fromObservation(obs(null, 99, t0)).toDocument())
    }

    @Test
    fun `GET snapshot by-task returns 200 with three entries for distinct task buckets`() = testApplication {
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

        val response = client.get("/hdts/$hdtId/snapshot/by-task")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()

        // Three distinct buckets: nw, tw, null
        val entryCount = body.split("\"propertyId\"").size - 1
        assertEquals(3, entryCount, "Expected exactly 3 entries (nw, tw, null). Body: $body")

        // task=nw: latest value is 75 (not 70)
        assertTrue(body.contains("\"nw\""), "Expected task 'nw' in response")
        assertTrue(body.contains("75"), "Expected value 75 for task nw (latest wins)")
        assertFalse(body.contains("70"), "Value 70 should be superseded by 75 for task nw")

        // task=tw: value 120
        assertTrue(body.contains("\"tw\""), "Expected task 'tw' in response")
        assertTrue(body.contains("120"), "Expected value 120 for task tw")

        // untagged bucket: value 99, task=null
        assertTrue(body.contains("99"), "Expected value 99 for untagged bucket")
        assertTrue(body.contains("\"hr\""), "Expected property name 'hr' in response")
    }

    @Test
    fun `GET snapshot by-task returns 200 with empty list for HDT with no observations`() = testApplication {
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

        val response = client.get("/hdts/unknown-hdt-xyz/snapshot/by-task")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText().trim())
    }
}
