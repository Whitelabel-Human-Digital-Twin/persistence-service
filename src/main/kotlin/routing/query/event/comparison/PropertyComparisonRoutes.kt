package io.github.whdt.routing.query.event.comparison

import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.time.toJavaInstant

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyComparisonRoutes(
    propertyEventService: PropertyEventService
) {
    route("/query/event/comparison") {
        post {
            val req = call.receive<PropertiesByComparisonsAggregateRequest>()
            val stats = propertyEventService.propertiesByComparisonsAggregate(
                req.comparisons,
                req.modelNames,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, stats)
        }.describe {
            operationId = "query/event/comparison"
            summary = "Query Events by comparisons"

            requestBody {
                schema = jsonSchema<PropertiesByComparisonsAggregateRequest>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<PropertiesByComparisonsAggregateResponse>()
                }
            }
        }
    }
}