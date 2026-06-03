package routing.hdt

import io.github.ktwinx.core.hdt.HdtId
import db.assembler.AssemblerService
import db.assembler.HdtSpecResponse
import db.assembler.PropertySnapshotEntry
import db.util.getOrRespond
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.hdtAssemblerRoutes(
    assemblerService: AssemblerService,
) {
    get("/spec") {
        val hdtId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing HDT id")
        assemblerService.getFullSpec(HdtId(hdtId)).getOrRespond(call) {
            call.respond(HttpStatusCode.InternalServerError, it.message)
        }?.let { call.respond(HttpStatusCode.OK, it) }
    }.describe {
        operationId = "hdts/{id}/spec"
        summary = "Get full HDT spec"
        description = "Assembles a full HDT spec from hdt, models, and properties collections"

        responses {
            HttpStatusCode.OK {
                description = "Full HDT spec"
                schema = jsonSchema<HdtSpecResponse>()
            }
            HttpStatusCode.BadRequest {
                description = "Missing HDT id"
            }
            HttpStatusCode.InternalServerError {
                description = "HDT or associated data not found"
            }
        }
    }

    get("/snapshot") {
        val hdtId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing HDT id")
        assemblerService.getSnapshot(HdtId(hdtId)).getOrRespond(call) {
            call.respond(HttpStatusCode.InternalServerError, it.message)
        }?.let { call.respond(HttpStatusCode.OK, it) }
    }.describe {
        operationId = "hdts/{id}/snapshot"
        summary = "Get HDT snapshot"
        description = "Returns current value per property: latest observation or initialValue fallback"

        responses {
            HttpStatusCode.OK {
                description = "Property snapshot"
                schema = jsonSchema<List<PropertySnapshotEntry>>()
            }
            HttpStatusCode.BadRequest {
                description = "Missing HDT id"
            }
            HttpStatusCode.InternalServerError {
                description = "HDT or property specs not found"
            }
        }
    }
}
