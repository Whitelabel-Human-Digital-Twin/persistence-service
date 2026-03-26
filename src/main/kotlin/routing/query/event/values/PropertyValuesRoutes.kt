package io.github.whdt.routing.query.event.values

import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.time.toJavaInstant

fun Route.propertyValuesRoutes(
    propertyEventService: PropertyEventService
) {
    route("/query/event/values") {
        post("/valuesById") {
            val req = call.receive<PropertyValuesRequest>()
            val values = propertyEventService.propertiesById(
                propertyId = req.propertyId!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }

        post("/valuesByName") {
            val req = call.receive<PropertyValuesRequest>()
            val values = propertyEventService.propertiesByName(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
                req.from!!.toJavaInstant(),
                req.to!!.toJavaInstant()
            )
            call.respond(HttpStatusCode.OK, values)
        }

        post("/history") {
            val req = call.receive<PropertyValuesRequest>()
            val values = propertyEventService.propertyHistory(
                hdtId = req.hdtId!!,
                propertyName = req.propertyName!!,
            )
            call.respond(HttpStatusCode.OK, values)
        }
    }
}