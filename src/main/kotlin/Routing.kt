package io.github.whdt

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyService
import io.github.whdt.query.FindByNameResponse
import io.github.whdt.query.PropertyComparisonRequest
import io.github.whdt.query.PropertyComparisonResponse
import io.github.whdt.util.unwrapAndStringify
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val mongoDatabase = connectToMongoDB()
    val hdtService = HdtService(mongoDatabase)
    val modelService = ModelService(mongoDatabase)
    val propertyService = PropertyService(mongoDatabase)
    routing {
        post("/api/hdts") {
            val hdt = call.receive<HumanDigitalTwin>()
            // Create HumanDigitalTwin
            val id = hdtService.create(hdt)
            hdt.models.forEach { modelService.create(it) }
            // Create Properties
            hdt.models.flatMap { it.properties }.forEach { propertyService.create(hdt.hdtId, it) }
            // Respond
            call.respond(HttpStatusCode.Created, id)
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
    }
}
