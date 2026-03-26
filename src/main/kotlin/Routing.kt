package io.github.whdt

import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyEventService
import io.github.whdt.routing.hdt.humanDigitalTwinRoutes
import io.github.whdt.routing.model.modelsRoutes
import io.github.whdt.routing.property.propertyEventRoutes
import io.github.whdt.routing.query.queryRoutes
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting() {
    val mongoDatabase = connectToMongoDB()
    val hdtService = HdtService(mongoDatabase)
    val modelService = ModelService(mongoDatabase)
    val propertyEventService = PropertyEventService(mongoDatabase)

    routing {

        openAPI("/openapi") {
            info = OpenApiInfo("My API", "1.0")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }

        get("/docs.json") {
            val doc = OpenApiDoc(
                info = OpenApiInfo("My API", "1.0.0")
            ) + call.application.routingRoot.descendants()
            call.respond(doc)
        }.hide()

        swaggerUI("/swaggerUI") {
            info = OpenApiInfo("My API", "1.0")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }

        humanDigitalTwinRoutes(hdtService, modelService, propertyEventService)
        modelsRoutes(modelService)
        propertyEventRoutes(propertyEventService)
        queryRoutes(propertyEventService)
    }
}
