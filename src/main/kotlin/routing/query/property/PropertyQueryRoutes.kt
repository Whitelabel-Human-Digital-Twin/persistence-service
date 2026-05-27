package io.github.whdt.routing.query.property

import io.github.whdt.core.hdt.query.TagPredicate
import io.github.whdt.db.property.PropertyDocument
import io.github.whdt.db.property.PropertyService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.propertyQueryRoutes(propertyService: PropertyService) {
    post("/property") {
        val predicate = try {
            call.receive<TagPredicate>()
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid TagPredicate body: ${e.message}")
        }
        val results = propertyService.findByTagPredicate(predicate)
        call.respond(HttpStatusCode.OK, results)
    }.describe {
        operationId = "query/property"
        summary = "Query Property specs by tag predicate"
        description = "Returns Property specs whose tags satisfy the given TagPredicate."
        responses {
            HttpStatusCode.OK {
                description = "Matching Property specs"
                schema = jsonSchema<List<PropertyDocument>>()
            }
            HttpStatusCode.BadRequest { description = "Malformed TagPredicate body" }
            HttpStatusCode.InternalServerError { description = "Query failed" }
        }
    }
}
