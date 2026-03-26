package io.github.whdt

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyEventService
import io.github.whdt.request.PropertiesByComparisonsAggregateRequest
import io.github.whdt.routing.query.event.stats.PropertyStatsRequest
import io.github.whdt.routing.query.event.values.PropertyValuesRequest
import io.github.whdt.routing.hdt.humanDigitalTwinRoutes
import io.github.whdt.routing.model.modelsRoutes
import io.github.whdt.routing.property.propertyEventRoutes
import io.github.whdt.routing.query.queryRoutes
import io.ktor.http.*
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.time.toJavaInstant

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
