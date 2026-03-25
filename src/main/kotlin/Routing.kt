package io.github.whdt

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyEventService
import io.github.whdt.request.PropertiesByComparisonsAggregateRequest
import io.github.whdt.request.PropertyStatsRequest
import io.github.whdt.request.PropertyValuesRequest
import io.ktor.http.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlin.time.toJavaInstant

fun Application.configureRouting() {
    val mongoDatabase = connectToMongoDB()
    val hdtService = HdtService(mongoDatabase)
    val modelService = ModelService(mongoDatabase)
    val propertyEventService = PropertyEventService(mongoDatabase)

    suspend fun mapPropertiesFromHdts(hdts: List<HumanDigitalTwin>, mapping: suspend (HdtId, List<Property>) -> Boolean): Boolean {
        return hdts.map {
            val properties = it.models.flatMap { m -> m.properties }
            mapping(it.hdtId, properties)
        }.foldRight(true){ a, b -> a&&b }
    }

    routing {

        openAPI("/api/openapi") {
            info = OpenApiInfo("My API", "1.0")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }

        post("/api/hdts") {
            val hdt = call.receive<HumanDigitalTwin>()
            // Create HumanDigitalTwin
            val id = hdtService.create(hdt)
            // Create Models
            modelService.insertMany(hdt.models)
            // Create Property Events
            val properties = hdt.models.flatMap { it.properties }
            propertyEventService.insertMany(hdt.hdtId, properties)
            // Respond
            call.respond(HttpStatusCode.Created, id)
        }

        post("/api/hdts/many") {
            val hdts = call.receive<List<HumanDigitalTwin>>()
            val resHdt = hdtService.insertMany(hdts)
            if (!resHdt) return@post call.respond(HttpStatusCode.InternalServerError)

            val models = hdts.flatMap { it.models }
            val resModel = modelService.insertMany(models)
            if (!resModel) return@post call.respond(HttpStatusCode.InternalServerError)

            val resProperty = mapPropertiesFromHdts(hdts) { id, p ->
                propertyEventService.insertMany(id, p)
            }
            if (!resProperty) return@post call.respond(HttpStatusCode.InternalServerError)

            call.respond(HttpStatusCode.OK)
        }

        put("/api/hdts/many") {
            val hdts = call.receive<List<HumanDigitalTwin>>()
            val resHdt = hdtService.upsertMany(hdts)
            if (!resHdt) return@put call.respond(HttpStatusCode.InternalServerError)

            val models = hdts.flatMap { it.models }
            val resModel = modelService.upsertMany(models)
            if (!resModel) return@put call.respond(HttpStatusCode.InternalServerError)

            val resProperty = mapPropertiesFromHdts(hdts) { id, p ->
                propertyEventService.insertMany(id, p)
            }
            if (!resProperty) return@put call.respond(HttpStatusCode.InternalServerError)

            call.respond(HttpStatusCode.OK)
        }

        get("/api/hdts") {
            val hdts = hdtService.findAll()
            call.respond(HttpStatusCode.OK, hdts)
        }

        get("/api/hdts/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            hdtService.read(id)?.let { hdt ->
                call.respond(HttpStatusCode.OK, hdt)
            } ?: call.respond(HttpStatusCode.NotFound)
        }

        put("/api/hdts/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            val hdt = call.receive<HumanDigitalTwin>()
            hdtService.update(id, hdt)?.let {
                call.respond(HttpStatusCode.OK)
            } ?: call.respond(HttpStatusCode.NotFound)
        }

        delete("/api/hdts/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            hdtService.delete(id)?.let {
                call.respond(HttpStatusCode.OK)
            } ?: call.respond(HttpStatusCode.NotFound)
        }

        get("/api/hdts/models") {
            val models = modelService.findAll()
            call.respond(HttpStatusCode.OK, models)
        }

        post("/api/hdts/models") {
            val model = call.receive<Model>()
            val id = modelService.create(model)
            call.respond(HttpStatusCode.Created, id)
        }

        put("/api/hdts/models") {
            val model = call.receive<Model>()
            val res = modelService.upsert(model)
            if (res) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        post("/api/hdts/models/many") {
            val models = call.receive<List<Model>>()
            val res = modelService.insertMany(models)
            if (res) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        put("/api/hdts/models/many") {
            val models = call.receive<List<Model>>()
            val res = modelService.upsertMany(models)
            if (res) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        get("/api/hdts/{id}/models") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            val res = modelService.findByHdtId(HdtId(id))
            if (res.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, res)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/api/hdts/{id}/events") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            val res = propertyEventService.propertiesByHdtId(HdtId(id))
            if (res.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, res)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/api/hdts/events") {
            val hdt = call.receive<HumanDigitalTwin>()
            val hdtId = hdt.hdtId
            val res = propertyEventService.insertMany(hdtId, hdt.models.flatMap { it.properties })
            if (res) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        post("/api/hdts/events/propertyValuesById") {
            val req = call.receive<PropertyValuesRequest>()
            val values = propertyEventService.propertiesById(
                propertyId = req.propertyId!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }

        post("/api/hdts/events/propertyValuesByName") {
            val req = call.receive<PropertyValuesRequest>()
            val values = propertyEventService.propertiesByName(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }

        post("/api/hdts/events/propertyHistory") {
            val req = call.receive<PropertyValuesRequest>()
            val values = propertyEventService.propertyHistory(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
            )
            call.respond(HttpStatusCode.OK, values)
        }

        post("/api/hdts/events/aggregate") {
            val req = call.receive<PropertyStatsRequest>()
            val stats = propertyEventService.propertyAggregateStats(
                req.hdtIds,
                req.modelIds,
                req.modelNames,
                req.propertyName,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, stats)
        }

        post("/api/hdts/aggregate") {
            val req = call.receive<PropertiesByComparisonsAggregateRequest>()
            val stats = propertyEventService.propertiesByComparisonsAggregate(
                req.comparisons,
                req.modelNames,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, stats)
        }
    }
}
