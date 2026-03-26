package io.github.whdt.routing.hdt

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.db.model.ModelDocument
import io.github.whdt.db.model.ModelService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.hdtModelsRoute(
    modelService: ModelService
) {
    get("/models") {
        val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
        val res = modelService.findByHdtId(HdtId(id))
        if (res.isNotEmpty()) {
            call.respond(HttpStatusCode.OK, res)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }.describe {
        operationId = "hdts/{id}/models"
        summary = "Get HDT's Models"
        description = "Get all Models of the specified HDT"

        responses {
            HttpStatusCode.OK {
                description = "Human Digital Twin Models"
                schema = jsonSchema<List<ModelDocument>>()
            }
            HttpStatusCode.NotFound {
                description = "Human Digital Twin not found"
            }
        }
    }
}