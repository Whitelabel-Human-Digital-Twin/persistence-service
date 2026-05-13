package io.github.whdt.routing.query

import io.github.whdt.db.property.PropertyObservationService
import io.github.whdt.routing.query.event.comparison.propertyComparisonRoutes
import io.github.whdt.routing.query.event.stats.propertyStatsRoutes
import io.github.whdt.routing.query.event.values.propertyValuesRoutes
import io.ktor.server.routing.*

fun Route.queryRoutes(
    propertyEventService: PropertyObservationService
) {
    propertyValuesRoutes(propertyEventService)
    propertyStatsRoutes(propertyEventService)
    propertyComparisonRoutes(propertyEventService)
}