package routing.query.availability

import db.property.PropertyObservationService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.time.toJavaInstant

@OptIn(ExperimentalKtorApi::class)
fun Route.availabilityRoutes(
    propertyEventService: PropertyObservationService
) {
    route("/query/hdts/by-model") {
        post {
            val req = call.receive<HdtsByModelRequestDto>()
            val result = propertyEventService.hdtsByModel(
                req.modelNames,
                req.match,
                req.metadataFilters,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant(),
            )
            call.respond(HttpStatusCode.OK, result)
        }.describe {
            operationId = "query/hdts/by-model"
            summary = "List HDTs having raw observation data for the given models"

            requestBody {
                schema = jsonSchema<HdtsByModelRequestDto>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<List<HdtModelAvailability>>()
                }
            }
        }
    }
}
