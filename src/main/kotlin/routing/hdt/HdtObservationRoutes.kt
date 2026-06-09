package routing.hdt

import db.property.PropertyObservationDocument
import db.property.PropertyObservationService
import io.github.ktwinx.core.hdt.HdtId
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.hdtObservationRoutes(
    observationService: PropertyObservationService
) {
    route("/observations") {
        get {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
            val res = observationService.observationsByHdtId(HdtId(id))
            if (res.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, res)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }.describe {
            operationId = "hdts/{id}/observations"
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