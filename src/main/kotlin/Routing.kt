import db.assembler.AssemblerService
import db.hdt.HdtService
import db.model.ModelService
import db.property.PropertyObservationService
import db.property.PropertyService
import db.view.ViewService
import routing.hdt.humanDigitalTwinRoutes
import routing.model.modelsRoutes
import routing.property.observationRoutes
import routing.property.propertyNamesRoutes
import routing.query.queryRoutes
import routing.view.viewRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.swagger.codegen.v3.generators.html.StaticHtmlCodegen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting() {
    val mongoDatabase = connectToMongoDB()
    val hdtService = HdtService(mongoDatabase)
    val modelService = ModelService(mongoDatabase)
    val propertyObservationService = PropertyObservationService(mongoDatabase)
    val propertyService = PropertyService(mongoDatabase)
    val viewService = ViewService(mongoDatabase)
    val assemblerService = AssemblerService(hdtService, modelService, propertyService, propertyObservationService)


    routing {

        get("/health") {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    mongoDatabase.runCommand(Document("ping", 1))
                }
            }.isSuccess
            if (ok) call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "degraded"))
        }

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
        propertyNamesRoutes(propertyService)
        queryRoutes(propertyObservationService, propertyService)
        viewRoutes(viewService, hdtService, propertyService)
    }
}
