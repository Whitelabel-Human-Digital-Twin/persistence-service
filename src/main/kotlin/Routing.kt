package io.github.whdt

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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

        /* OLD API
        get("/api/hdts/findByPropertyName/{propertyName}") {
            val propertyNameRaw = call.parameters["propertyName"] ?: throw IllegalArgumentException("No property name found")
            val hdts = propertyService.findByName(PropertyName(propertyNameRaw)).map { FindByNameResponse(it.hdtId, it.propertyName, it.value.toString()) }
            call.respond(HttpStatusCode.OK, hdts)
        }

        post("/api/hdts/findByPropertyComparison") {
            val request = call.receive<PropertyComparisonRequest>()
            val result = propertyService.findByComparison(
                    request.propertyName,
                    request.value,
                    request.operator,
                ).map {
                val value = it.value
                PropertyComparisonResponse(it.hdtId, it.propertyName, value.unwrapAndStringify())
            }
            call.respond(HttpStatusCode.OK, result)
        }
         */
    }
}
