package io.github.whdt.routing.query

import io.github.whdt.db.property.PropertyObservationService
import io.github.whdt.db.property.PropertyService
import io.github.whdt.routing.query.event.comparison.propertyComparisonRoutes
import io.github.whdt.routing.query.event.stats.propertyStatsRoutes
import io.github.whdt.routing.query.event.values.propertyValuesRoutes
import io.github.whdt.routing.query.property.propertyQueryRoutes
import io.ktor.server.routing.*

fun Route.queryRoutes(
    propertyEventService: PropertyObservationService,
    propertyService: PropertyService,
) {
    propertyValuesRoutes(propertyEventService)
    propertyStatsRoutes(propertyEventService)
    propertyComparisonRoutes(propertyEventService)
    route("/query") {
        propertyQueryRoutes(propertyService)
    }
}