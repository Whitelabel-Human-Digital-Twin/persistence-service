package io.github.whdt.routing.property

import io.github.whdt.core.hdt.model.property.PropertyObservation
import io.github.whdt.db.property.PropertyObservationService
import io.github.whdt.db.util.getOrRespond
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyEventRoutes(
    propertyObservationService: PropertyObservationService
) {
    route("/observations/batch") {
        post {
            val observations = call.receive<List<PropertyObservation>>()
            propertyObservationService
                .insertMany(observations)
                .getOrRespond(call) {
                    call.respond(HttpStatusCode.InternalServerError, it.message)
                } ?: return@post

            call.respond(HttpStatusCode.Created)
        }.describe {
            operationId = "observations/batch/insert"
            description = "Batch insert [PropertyObservation]"

            requestBody {
                description = "A list of PropertyObservation"
                schema = jsonSchema<List<PropertyObservation>>()
            }

            responses {
                HttpStatusCode.Created {
                    description = "Observations created"
                }
                HttpStatusCode.InternalServerError {
                    description = "Error creating observations"
                }
            }
        }
    }
}
