package io.github.whdt.routing.hdt

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.db.property.PropertyEventDocument
import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.hdtEventsRoute(
    propertyService: PropertyEventService,
) {
    route("/events") {
        get {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            val res = propertyService.propertiesByHdtId(HdtId(id))
            if (res.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, res)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }.describe {
            operationId = "hdts/{id}/events"
            description = "Get all Events of the specified HDT"
            summary  = "Get HDT's [Events]"

            responses {
                HttpStatusCode.OK {
                    description = "Human Digital Twin Events"
                    schema = jsonSchema<List<PropertyEventDocument>>()
                }
                HttpStatusCode.NotFound {
                    description = "Human Digital Twin or Events not found"
                }
            }
        }
    }
}