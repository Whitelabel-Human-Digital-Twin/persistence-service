package io.github.whdt.routing.query.event.comparison

import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlin.time.toJavaInstant

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
        }
    }
}