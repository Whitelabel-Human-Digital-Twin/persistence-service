package io.github.whdt.routing.query.event.values

import io.github.whdt.db.property.PropertyObservationDocument
import io.github.whdt.db.property.PropertyObservationService
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
    propertyEventService: PropertyObservationService
) {
    route("/query/event/values") {
        post("/valuesById") {
            val reqs = call.receive<List<PropertyValuesRequest>>()
            val req = reqs.firstOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val values = propertyEventService.observationsById(
                propertyId = req.propertyId!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }.describe {
            operationId = "query/event/values/byId"
            summary = "Query Observations by Id"

            requestBody {
                schema = jsonSchema<List<PropertyValuesRequest>>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyObservationDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "If an empty list is sent."
                }
            }
        }

        post("/valuesByName") {
            val reqs = call.receive<List<PropertyValuesRequest>>()
            val req = reqs.firstOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val values = propertyEventService.observationsByName(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }.describe {
            operationId = "query/event/values/byName"
            summary = "Query Observations by Name"

            requestBody {
                schema = jsonSchema<List<PropertyValuesRequest>>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyObservationDocument>>()
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
            val values = propertyEventService.observationHistory(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
            )
            call.respond(HttpStatusCode.OK, values)
        }.describe {
            operationId = "query/event/values/history"
            summary = "Query Observation history for a certain HDT"

            requestBody {
                schema = jsonSchema<List<PropertyValuesRequest>>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyObservationDocument>>()
                }
                HttpStatusCode.BadRequest {
                    description = "If an empty list is sent."
                }
            }
        }
    }
}
