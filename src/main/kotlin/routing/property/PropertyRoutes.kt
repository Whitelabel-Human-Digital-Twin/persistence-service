package io.github.whdt.routing.property

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.db.property.PropertyDocument
import io.github.whdt.db.property.PropertyService
import io.github.whdt.db.util.getOrRespond
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyRoutes(
    propertyService: PropertyService,
) {
    route("/properties") {
        get {
            val hdtId = call.request.queryParameters["hdtId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing required query parameter: hdtId")
            val modelId = call.request.queryParameters["modelId"]
            val results = propertyService.findByHdtId(
                HdtId(hdtId),
                modelId?.let { ModelId(it) }
            )
            call.respond(HttpStatusCode.OK, results)
        }.describe {
            operationId = "properties/get"
            summary = "List Property specs"
            description = "Return property specs filtered by hdtId and optional modelId"

            responses {
                HttpStatusCode.OK {
                    description = "Property specs"
                    schema = jsonSchema<List<PropertyDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "Missing hdtId query parameter"
                }
            }
        }

        route("/batch") {
            post {
                val hdtIdParam = call.request.queryParameters["hdtId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing required query parameter: hdtId")
                val properties = call.receive<List<Property>>()
                propertyService.batchUpsert(HdtId(hdtIdParam), properties)
                    .getOrRespond(call) {
                        call.respond(HttpStatusCode.InternalServerError, it.message)
                    } ?: return@post
                call.respond(HttpStatusCode.Created)
            }.describe {
                operationId = "properties/batch/upsert"
                summary = "Batch upsert Property specs"
                description = "Insert or update a list of Property specs for a given HDT"

                requestBody {
                    description = "List of Property specs"
                    schema = jsonSchema<List<Property>>()
                }

                responses {
                    HttpStatusCode.Created {
                        description = "Property specs upserted successfully"
                    }
                    HttpStatusCode.BadRequest {
                        description = "Missing hdtId query parameter"
                    }
                    HttpStatusCode.InternalServerError {
                        description = "Failed to upsert property specs"
                    }
                }
            }
        }
    }
}
