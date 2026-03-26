package io.github.whdt.routing.model

import io.github.whdt.core.hdt.model.Model
import io.github.whdt.db.model.ModelDocument
import io.github.whdt.db.model.ModelService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.modelsRoutes(
    modelService: ModelService,
) {
    route("/models") {
        get {
            val models = modelService.findAll()
            call.respond(HttpStatusCode.OK, models)
        }.describe {
            operationId = "models/get"
            summary = "List Models"
            description = "Return all Models"

            responses {
                HttpStatusCode.OK {
                    description = "All Models"
                    schema = jsonSchema<List<ModelDocument>>()
                }
            }
        }

        post {
            val model = call.receive<Model>()
            val id = modelService.create(model)
            call.respond(HttpStatusCode.Created, id)
        }.describe {
            operationId = "models/create"
            summary = "Create Model"
            description = "Creates a new model"

            requestBody {
                description = "The representation of an HDT's Model"
                schema = jsonSchema<Model>()
            }

            responses {
                HttpStatusCode.Created {
                    description = "Created Successfully"
                    schema = jsonSchema<ModelDocument>()
                }
            }
        }

        put {
            val model = call.receive<Model>()
            val res = modelService.upsert(model)
            if (res) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }.describe {
            operationId = "models/upsert"
            summary = "Create or Update Model"
            description = "Creates a new Model or updates it if already exists"

            requestBody {
                description = "The updated Model"
                schema = jsonSchema<Model>()
            }

            responses {
                HttpStatusCode.OK {
                    description = "Upserted Successfully"
                }
                HttpStatusCode.InternalServerError {
                    description = "Failed to upsert Model"
                }
            }
        }

        route("/batch") {
            post {
                val models = call.receive<List<Model>>()
                val res = modelService.insertMany(models)
                if (res) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }.describe {
                operationId = "models/batch/insert"
                summary = "Batch insert [Model]"
                description = "Insert a list of HDT Models"

                requestBody {
                    description = "The list of HDT Models"
                    schema = jsonSchema<List<Model>>()
                }

                responses {
                    HttpStatusCode.Created {
                        description = "Created Successfully"
                    }
                    HttpStatusCode.InternalServerError {
                        description = "Failed to create Models"
                    }
                }
            }

            put {
                val models = call.receive<List<Model>>()
                val res = modelService.upsertMany(models)
                if (res) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }.describe {
                operationId = "models/batch/upsert"
                summary = "Batch upsert [Model]"
                description = "Insert or update a list of HDT Models"

                requestBody {
                    description = "The list of HDT Models"
                    schema = jsonSchema<List<Model>>()
                }

                responses {
                    HttpStatusCode.OK {
                        description = "Upserted Successfully"
                    }
                    HttpStatusCode.InternalServerError {
                        description = "Failed to upsert Models"
                    }
                }
            }
        }
    }
}