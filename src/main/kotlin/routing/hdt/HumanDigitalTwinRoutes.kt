package io.github.whdt.routing.hdt

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.hdt.HumanDigitalTwinDocument
import io.github.whdt.db.model.ModelService
import io.github.whdt.db.property.PropertyEventService
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*

suspend fun mapPropertiesFromHdts(hdts: List<HumanDigitalTwin>, mapping: suspend (HdtId, List<Property>) -> Boolean): Boolean {
    return hdts.map {
        val properties = it.models.flatMap { m -> m.properties }
        mapping(it.hdtId, properties)
    }.foldRight(true){ a, b -> a&&b }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.humanDigitalTwinRoutes(
    hdtService: HdtService,
    modelService: ModelService,
    propertyService: PropertyEventService
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
                    description = "HumanDigitalTwin created"
                    schema = jsonSchema<List<HumanDigitalTwinDocument>>()
                }
            }
        }

        post {
            val hdt = call.receive<HumanDigitalTwin>()
            // Create HumanDigitalTwin
            val hdtDoc = hdtService.create(hdt)
            // Create Models
            modelService.insertMany(hdt.models)
            // Create Property Events
            val properties = hdt.models.flatMap { it.properties }
            propertyService.insertMany(hdt.hdtId, properties)
            // Respond
            call.respond<HumanDigitalTwinDocument>(HttpStatusCode.Created, hdtDoc)
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
                    call.respond(HttpStatusCode.OK)
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
        }

        route("/batch") {
            post {
                val hdts = call.receive<List<HumanDigitalTwin>>()
                val resHdt = hdtService.insertMany(hdts)
                if (!resHdt) return@post call.respond(HttpStatusCode.InternalServerError)

                val models = hdts.flatMap { it.models }
                val resModel = modelService.insertMany(models)
                if (!resModel) return@post call.respond(HttpStatusCode.InternalServerError)

                val resProperty = mapPropertiesFromHdts(hdts) { id, p ->
                    propertyService.insertMany(id, p)
                }
                if (!resProperty) return@post call.respond(HttpStatusCode.InternalServerError)

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
                val resHdt = hdtService.upsertMany(hdts)
                if (!resHdt) return@put call.respond(HttpStatusCode.InternalServerError)

                val models = hdts.flatMap { it.models }
                val resModel = modelService.upsertMany(models)
                if (!resModel) return@put call.respond(HttpStatusCode.InternalServerError)

                val resProperty = mapPropertiesFromHdts(hdts) { id, p ->
                    propertyService.insertMany(id, p)
                }
                if (!resProperty) return@put call.respond(HttpStatusCode.InternalServerError)

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