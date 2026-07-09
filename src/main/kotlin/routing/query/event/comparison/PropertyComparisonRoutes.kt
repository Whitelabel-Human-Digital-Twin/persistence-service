package routing.query.event.comparison

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
fun Route.propertyComparisonRoutes(
    propertyEventService: PropertyObservationService
) {
    route("/query/event/comparison") {
        post {
            val req = call.receive<PropertiesByComparisonsRequestDto>()
            val domainComparisons = req.comparisons.map { dto ->
                val inferredType = inferPropertyType(dto.value)

                dto.toDomain(inferredType)
            }
            val stats = propertyEventService.observationsByComparisonsAggregate(
                domainComparisons,
                req.modelNames,
                req.from?.toJavaInstant(),
                req.to?.toJavaInstant(),
                req.metadataFilters
            )
            call.respond(HttpStatusCode.OK, stats)
        }.describe {
            operationId = "query/event/comparison"
            summary = "Query Events by comparisons"

            requestBody {
                schema = jsonSchema<PropertiesByComparisonsRequestDto>()
            }

            responses {
                HttpStatusCode.OK {
                    schema = jsonSchema<ComparisonSearchResult>()
                }
            }
        }
    }
}