package io.github.whdt.routing.property

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.db.property.PropertyEventService
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
    propertyEventService: PropertyEventService
) {
    route("/properties/batch") {
        post {
            val hdt = call.receive<HumanDigitalTwin>()
            val hdtId = hdt.hdtId
            val props = hdt.models.flatMap { it.properties }
            propertyEventService
                .insertMany(hdtId, props)
                .getOrRespond(call) {
                    call.respond(HttpStatusCode.InternalServerError, it.message)
                } ?: return@post

            call.respond(HttpStatusCode.Created)
        }.describe {
            operationId = "properties/insert"
            description = "Batch insert HDT's [Property] as Events"

            requestBody {
                description = "A Human Digital Twin"
                schema = jsonSchema<HumanDigitalTwin>()
            }

            responses {
                HttpStatusCode.Created {
                    description = "HDT's [Property] created as Events"
                }
                HttpStatusCode.InternalServerError {
                    description = "Error creating Events"
                }
            }
        }
    }
}