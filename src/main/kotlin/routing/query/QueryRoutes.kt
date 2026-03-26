package io.github.whdt.routing.query

import io.github.whdt.db.property.PropertyEventService
import io.github.whdt.routing.query.event.comparison.propertyComparisonRoutes
import io.github.whdt.routing.query.event.stats.propertyStatsRoutes
import io.github.whdt.routing.query.event.values.propertyValuesRoutes
import io.ktor.server.routing.*

fun Route.queryRoutes(
    propertyEventService: PropertyEventService
) {
    propertyValuesRoutes(propertyEventService)
    propertyStatsRoutes(propertyEventService)
    propertyComparisonRoutes(propertyEventService)
}