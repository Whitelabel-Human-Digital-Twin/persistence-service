package io.github.whdt.routing.query.event.stats

import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.time.toJavaInstant

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
        }
    }
}