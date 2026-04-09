package io.github.whdt.routing.query.event.values

import io.github.whdt.db.property.PropertyEventDocument
import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import kotlin.time.toJavaInstant

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyValuesRoutes(
    propertyEventService: PropertyEventService
) {
    route("/query/event/values") {
        post("/valuesById") {
            val reqs = call.receive<List<PropertyValuesRequest>>()
            val req = reqs.firstOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val values = propertyEventService.propertiesById(
                propertyId = req.propertyId!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }.describe {
            operationId = "query/event/values/byId"
            summary = "Query Events by Id"

            requestBody {
                schema = jsonSchema<List<PropertyValuesRequest>>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyEventDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "If an empty list is sent."
                }
            }
        }

        post("/valuesByName") {
            val reqs = call.receive<List<PropertyValuesRequest>>()
            val req = reqs.firstOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val values = propertyEventService.propertiesByName(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }.describe {
            operationId = "query/event/values/byName"
            summary = "Query Events by Name"

            requestBody {
                schema = jsonSchema<List<PropertyValuesRequest>>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyEventDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "If an empty list is sent."
                }
            }
        }

        post("/history") {
            val reqs = call.receive<List<PropertyValuesRequest>>()
            val req = reqs.firstOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val values = propertyEventService.propertyHistory(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
            )
            call.respond(HttpStatusCode.OK, values)
        }.describe {
            operationId = "query/event/values/history"
            summary = "Query Event history for a certain HDT"

            requestBody {
                schema = jsonSchema<List<PropertyValuesRequest>>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyEventDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "If an empty list is sent."
                }
            }
        }
    }
}