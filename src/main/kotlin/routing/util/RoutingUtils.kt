package routing.util

import db.util.Err
import db.util.OperationResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall

suspend fun respondWithError(failurePrologue: String, call: RoutingCall, operationError: Err) {
    /*
    val detail = operationError.message
    call.application.log.error(failurePrologue + ": ${response.status} — $detail")
    return call.respond(HttpStatusCode.InternalServerError, failurePrologue + ": ${response.status} — $detail")
     */
}