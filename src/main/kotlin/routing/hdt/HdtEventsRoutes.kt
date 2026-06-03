package routing.hdt

import io.github.ktwinx.core.hdt.HdtId
import db.property.PropertyObservationDocument
import db.property.PropertyObservationService
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.hdtEventsRoute(
    propertyService: PropertyObservationService,
) {
    route("/events") {
        get {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            val res = propertyService.observationsByHdtId(HdtId(id))
            if (res.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, res)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }.describe {
            operationId = "hdts/{id}/events"
            description = "Get all Observations of the specified HDT"
            summary  = "Get HDT's [Observations]"

            responses {
                HttpStatusCode.OK {
                    description = "Human Digital Twin Observations"
                    schema = jsonSchema<List<PropertyObservationDocument>>()
                }
                HttpStatusCode.NotFound {
                    description = "Human Digital Twin or Observations not found"
                }
            }
        }
    }
}