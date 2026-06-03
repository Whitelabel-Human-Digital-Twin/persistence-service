package routing.query

import db.property.PropertyObservationService
import db.property.PropertyService
import routing.query.event.comparison.propertyComparisonRoutes
import routing.query.event.stats.propertyStatsRoutes
import routing.query.event.values.propertyValuesRoutes
import routing.query.property.propertyQueryRoutes
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