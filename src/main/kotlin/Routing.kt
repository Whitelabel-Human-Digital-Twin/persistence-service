package io.github.whdt

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.db.HdtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val mongoDatabase = connectToMongoDB()
    val hdtService = HdtService(mongoDatabase)
    routing {
        post("/api/hdts") {
            val hdt = call.receive<HumanDigitalTwin>()
            val id = hdtService.create(hdt)
            call.respond(HttpStatusCode.Created, id)
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
    }
}
