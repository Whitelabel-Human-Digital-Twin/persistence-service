package io.github.whdt.routing.query.event.stats

import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.time.toJavaInstant

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyStatsRoutes(
    propertyEventService: PropertyEventService
) {
    route("query/event/stats") {
        post {
            val req = call.receive<PropertyStatsRequest>()
            val stats = propertyEventService.propertyAggregateStats(
                req.hdtIds,
                req.modelIds,
                req.modelNames,
                req.propertyName,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, stats)
        }.describe {
            operationId = "query/event/stats"
            summary = "Query Aggregate Stats"

            requestBody {
                schema = jsonSchema<PropertyStatsRequest>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<PropertyStatsPerHdt>>()
                }
            }
        }
    }
}