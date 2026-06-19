package routing.property

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.property.Property
import db.property.PropertyDocument
import db.property.PropertyService
import db.util.getOrRespond
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.github.ktwinx.core.hdt.model.property.PropertyId
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.propertySpecRoutes(
    propertyService: PropertyService,
) {
    route("/properties") {
        get {
            val hdtId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing HDT id")
            val modelId = call.request.queryParameters["modelId"]
            val results = propertyService.findByHdtId(
                HdtId(hdtId),
                modelId?.let { ModelId(it) }
            )
            call.respond(HttpStatusCode.OK, results)
        }.describe {
            operationId = "properties/get"
            summary = "List Property specs"
            description = "Return property specs for the specified HDT, optionally filtered by modelId"

            responses {
                HttpStatusCode.OK {
                    description = "Property specs"
                    schema = jsonSchema<List<PropertyDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "Missing HDT id"
                }
            }
        }

        route("/{propertyId}/tags") {
            put {
                val hdtId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing HDT id")
                val propertyId = call.parameters["propertyId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing propertyId")
                val tags = call.receive<Map<String, String>>()
                val matched = propertyService
                    .replaceTags(HdtId(hdtId), PropertyId(propertyId), tags)
                    .getOrRespond(call) { call.respond(HttpStatusCode.InternalServerError, it.message) }
                    ?: return@put
                if (matched) call.respond(HttpStatusCode.OK, tags)
                else call.respond(HttpStatusCode.NotFound, "Property not found: $propertyId")
            }
        }

        route("/batch") {
            post {
                val hdtIdParam = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing HDT id")
                val properties = call.receive<List<Property>>()
                propertyService.batchUpsert(HdtId(hdtIdParam), properties)
                    .getOrRespond(call) {
                        call.respond(HttpStatusCode.InternalServerError, it.message)
                    } ?: return@post
                call.respond(HttpStatusCode.Created)
            }.describe {
                operationId = "properties/batch/upsert"
                summary = "Batch upsert Property specs"
                description = "Insert or update a list of Property specs for the specified HDT"

                requestBody {
                    description = "List of Property specs"
                    schema = jsonSchema<List<Property>>()
                }

                responses {
                    HttpStatusCode.Created {
                        description = "Property specs upserted successfully"
                    }
                    HttpStatusCode.BadRequest {
                        description = "Missing HDT id"
                    }
                    HttpStatusCode.InternalServerError {
                        description = "Failed to upsert property specs"
                    }
                }
            }
        }
    }
}
