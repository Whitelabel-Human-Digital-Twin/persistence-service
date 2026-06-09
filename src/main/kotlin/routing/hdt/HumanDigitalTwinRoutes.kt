package routing.hdt

import db.assembler.AssemblerService
import db.hdt.HdtService
import db.hdt.HumanDigitalTwinDocument
import db.model.ModelService
import db.property.PropertyObservationDocument
import db.property.PropertyObservationService
import db.property.PropertyService
import db.util.getOrRespond
import db.util.sequenceResults
import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import routing.property.observationRoutes
import routing.property.propertySpecRoutes

@OptIn(ExperimentalKtorApi::class)
fun Route.humanDigitalTwinRoutes(
    hdtService: HdtService,
    modelService: ModelService,
    observationService: PropertyObservationService,
    specService: PropertyService,
    assemblerService: AssemblerService,
) {
    route("hdts") {
        get {
            val hdts = hdtService.findAll()
            call.respond(HttpStatusCode.OK, hdts)
        }.describe {
            operationId = "hdts/get"
            summary = "List HDTs"
            description = "Returns all Human Digital Twins"

            responses {
                HttpStatusCode.OK {
                    description = "All Human Digital Twins"
                    schema = jsonSchema<List<HumanDigitalTwinDocument>>()
                }
            }
        }

        post {
            val hdt = call.receive<HumanDigitalTwin>()
            val hdtDoc = hdtService.create(hdt)

            modelService.insertMany(hdt.models)

            val properties = hdt.models.flatMap { it.properties }
            specService.batchInsert(hdt.hdtId, properties).getOrRespond(call) {
                call.respond(HttpStatusCode.InternalServerError, it.message)
            } ?: return@post

            call.respond(HttpStatusCode.Created, hdtDoc)
        }.describe {
            operationId = "hdts/post"
            summary = "Create HDT"
            description = "Creates a new Human Digital Twin"

            requestBody {
                description = "The Representation of a Human Digital Twin"
                content {
                    schema = jsonSchema<HumanDigitalTwin>()
                }
            }

            responses {
                HttpStatusCode.Created {
                    description = "HumanDigitalTwin created"
                    schema = jsonSchema<HumanDigitalTwinDocument>()
                }
            }
        }

        put {
            val hdt = call.receive<HumanDigitalTwin>()
            val hdtDoc = hdtService.upsert(hdt)

            modelService.upsertMany(hdt.models)

            val properties = hdt.models.flatMap { it.properties }
            specService.batchUpsert(hdt.hdtId, properties).getOrRespond(call) {
                call.respond(HttpStatusCode.InternalServerError, it.message)
            } ?: return@put

            call.respond(HttpStatusCode.OK, hdtDoc)
        }.describe {
            operationId = "hdts/put"
            summary = "Create or Update HDT"
            description = "Creates a new Human Digital Twin or updates an existing one"

            requestBody {
                description = "The Representation of a Human Digital Twin"
                content {
                    schema = jsonSchema<HumanDigitalTwin>()
                }
            }

            responses {
                HttpStatusCode.OK {
                    description = "HumanDigitalTwin created or updated"
                    schema = jsonSchema<HumanDigitalTwinDocument>()
                }
            }
        }

        route("/{id}") {
            get {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
                hdtService.read(id)?.let { hdt ->
                    call.respond(HttpStatusCode.OK, hdt)
                } ?: call.respond(HttpStatusCode.NotFound)
            }.describe {
                operationId = "hdts/{id}/get"
                summary = "Get HDT"
                description = "Get a Human Digital Twin by its ID"

                responses {
                    HttpStatusCode.OK {
                        description = "Human Digital Twin found"
                        schema = jsonSchema<HumanDigitalTwinDocument>()
                    }
                    HttpStatusCode.NotFound {
                        description = "Human Digital Twin not found"
                    }
                }
            }

            put {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
                val hdt = call.receive<HumanDigitalTwin>()
                hdtService.update(id, hdt)?.let {
                    call.respond(HttpStatusCode.OK, it)
                } ?: call.respond(HttpStatusCode.NotFound)
            }.describe {
                operationId = "hdts/{id}/update"
                summary = "Update HDT"
                description = "Update a Human Digital Twin"

                requestBody {
                    description = "The updated Human Digital Twin"
                    content {
                        schema = jsonSchema<HumanDigitalTwin>()
                    }
                }

                responses {
                    HttpStatusCode.OK {
                        description = "Human Digital Twin updated"
                        schema = jsonSchema<HumanDigitalTwinDocument>()
                    }
                    HttpStatusCode.NotFound {
                        description = "Human Digital Twin to update not found"
                    }
                }
            }

            delete {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
                hdtService.delete(id)?.let {
                    call.respond(HttpStatusCode.OK)
                } ?: call.respond(HttpStatusCode.NotFound)
            }.describe {
                operationId = "hdts/{id}/remove"
                summary = "Remove HDT"
                description = "Remove a Human Digital Twin"

                responses {
                    HttpStatusCode.OK {
                        description = "Human Digital Twin removed"
                    }
                    HttpStatusCode.NotFound {
                        description = "Human Digital Twin to remove not found"
                    }
                }
            }
            propertySpecRoutes(specService)
            hdtObservationRoutes(observationService)
            hdtModelsRoute(modelService)
            hdtAssemblerRoutes(assemblerService)
        }

        route("/batch") {
            post {
                val hdts = call.receive<List<HumanDigitalTwin>>()
                hdtService.insertMany(hdts).getOrRespond(call) {
                    call.respond(HttpStatusCode.InternalServerError, it.message)
                } ?: return@post

                hdts.sequenceResults { hdt ->
                    val models = hdt.models
                    modelService.insertMany(models)

                    val props = hdt.models.flatMap { it.properties }
                    specService.batchInsert(hdt.hdtId, props)
                }.getOrRespond(call) {
                    call.respond(HttpStatusCode.InternalServerError, it.message)
                } ?: return@post

                call.respond(HttpStatusCode.OK)
            }.describe {
                operationId = "hdts/batch/insert"
                summary = "Batch insert [HDT]"
                description = "Insert a list of Human Digital Twins"

                requestBody {
                    schema = jsonSchema<List<HumanDigitalTwin>>()
                }

                responses {
                    HttpStatusCode.OK {
                        description = "Human Digital Twins inserted"
                    }
                    HttpStatusCode.InternalServerError {
                        description = "Insertion failed"
                    }
                }
            }

            put {
                val hdts = call.receive<List<HumanDigitalTwin>>()
                hdtService.upsertMany(hdts).getOrRespond(call) {
                    call.respond(HttpStatusCode.InternalServerError, it.message)
                } ?: return@put

                hdts.sequenceResults { hdt ->
                    val models = hdt.models
                    modelService.upsertMany(models)

                    val props = models.flatMap { it.properties }
                    specService.batchUpsert(hdt.hdtId, props)
                }.getOrRespond(call) {
                    call.respond(HttpStatusCode.InternalServerError, it.message)
                } ?: return@put

                call.respond(HttpStatusCode.OK)
            }.describe {
                operationId = "hdts/batch/update"
                summary = "Batch update [HDT]"
                description = "Update a list of Human Digital Twins"

                requestBody {
                    schema = jsonSchema<List<HumanDigitalTwin>>()
                }

                responses {
                    HttpStatusCode.OK {
                        description = "Human Digital Twins updated"
                    }
                    HttpStatusCode.InternalServerError {
                        description = "Update failed"
                    }
                }
            }
        }
    }
}
