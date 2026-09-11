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
            if (req.metadataFilters?.containsKey("task") == true) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Filter on 'task' via the top-level 'taskScope' field, not via metadataFilters."
                )
                return@post
            }
            val result = propertyEventService.hdtsByModel(
                modelNames = req.modelNames,
                match = req.match,
                metadataFilters = req.metadataFilters,
                taskScope = req.taskScope,
                from = req.from?.toJavaInstant(),
                to = req.to?.toJavaInstant(),
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
