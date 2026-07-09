package routing.property

import db.property.PropertyService
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyNamesRoutes(
    propertyService: PropertyService,
) {
    route("/properties/names") {
        get {
            val names = propertyService.distinctPropertyNames()
            call.respond(HttpStatusCode.OK, names)
        }.describe {
            operationId = "properties/names"
            summary = "List distinct Property names"
            description = "Returns the distinct set of propertyName values across all Property specs"

            responses {
                HttpStatusCode.OK {
                    description = "Distinct property names"
                    schema = jsonSchema<List<PropertyName>>()
                }
            }
        }
    }
}
