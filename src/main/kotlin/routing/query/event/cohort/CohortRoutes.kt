package routing.query.event.cohort

import db.hdt.HdtService
import db.property.PropertyObservationService
import routing.query.event.comparison.dto.PropertiesByComparisonsRequestDto
import routing.query.event.comparison.dto.inferPropertyType
import routing.query.event.comparison.dto.toDomain
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.time.toJavaInstant

@OptIn(ExperimentalKtorApi::class)
fun Route.cohortRoutes(
    propertyEventService: PropertyObservationService,
    hdtService: HdtService,
) {
    route("/query/cohort") {
        post {
            val req = call.receive<PropertiesByComparisonsRequestDto>()
            if (req.comparisons.isEmpty() && req.modelPresence.isNullOrEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "At least one comparison or model presence filter is required"
                )
                return@post
            }
            if (req.metadataFilters?.containsKey("task") == true) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Filter on 'task' via the top-level 'taskScope' field, not via metadataFilters."
                )
                return@post
            }
            val domainComparisons = req.comparisons.map { dto ->
                val inferredType = inferPropertyType(dto.value)

                dto.toDomain(inferredType)
            }
            val universe = if (req.comparisons.isEmpty()) hdtService.findAll().map { it.hdtId } else null
            val result = propertyEventService.cohortExplore(
                domainComparisons,
                req.modelNames,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant(),
                req.metadataFilters,
                req.modelPresence,
                universe,
                req.taskScope,
            )
            call.respond(HttpStatusCode.OK, result)
        }.describe {
            operationId = "query/cohort"
            summary = "Query cohort frame (per-DT values + stats + population summary) by comparisons"

            requestBody {
                schema = jsonSchema<PropertiesByComparisonsRequestDto>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<CohortResult>()
                }
            }
        }
    }
}
