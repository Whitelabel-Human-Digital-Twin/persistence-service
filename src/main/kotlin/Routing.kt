package io.github.whdt

import io.github.whdt.db.assembler.AssemblerService
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyObservationService
import io.github.whdt.db.property.PropertyService
import io.github.whdt.routing.hdt.humanDigitalTwinRoutes
import io.github.whdt.routing.model.modelsRoutes
import io.github.whdt.routing.property.observationRoutes
import io.github.whdt.routing.property.propertyRoutes
import io.github.whdt.routing.query.queryRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.swagger.codegen.v3.generators.html.StaticHtmlCodegen

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting() {
    val mongoDatabase = connectToMongoDB()
    val hdtService = HdtService(mongoDatabase)
    val modelService = ModelService(mongoDatabase)
    val propertyObservationService = PropertyObservationService(mongoDatabase)
    val propertyService = PropertyService(mongoDatabase)
    val assemblerService = AssemblerService(hdtService, modelService, propertyService, propertyObservationService)


    routing {

        openAPI(path = "/openapi", swaggerFile = "openapi/openapi.yaml") {
            codegen = StaticHtmlCodegen()
        }

        swaggerUI(path = "/swaggerUI", swaggerFile = "openapi/openapi.yaml")

        get("/openapi.yaml") {
            val yaml = application.environment.classLoader
                .getResource("openapi/openapi.yaml")
                ?.readText()
                ?: error("openapi/openapi.yaml not found in resources")

            call.respondText(
                text = yaml,
                contentType = ContentType.parse("application/yaml")
            )
        }

        humanDigitalTwinRoutes(hdtService, modelService, propertyObservationService, propertyService, assemblerService)
        modelsRoutes(modelService)
        observationRoutes(propertyObservationService)
        propertyRoutes(propertyService)
        queryRoutes(propertyObservationService)
    }
}
