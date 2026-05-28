package io.github.whdt.routing.view

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.view.View
import io.github.whdt.core.hdt.view.ViewName
import io.github.whdt.core.hdt.view.ViewResult
import io.github.whdt.db.hdt.HdtService
import io.github.whdt.db.property.PropertyService
import io.github.whdt.db.view.ViewDocument
import io.github.whdt.db.view.ViewService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable

@Serializable
data class ExecuteScopeRequest(val hdtIds: List<String> = emptyList())

@OptIn(ExperimentalKtorApi::class)
fun Route.viewRoutes(
    viewService: ViewService,
    hdtService: HdtService,
    propertyService: PropertyService,
) {
    route("/views") {
        get {
            val views = viewService.findAll()
            call.respond(HttpStatusCode.OK, views)
        }.describe {
            operationId = "views/get"
            summary = "List Views"
            description = "Returns all stored Views"

            responses {
                HttpStatusCode.OK {
                    description = "All Views"
                    schema = jsonSchema<List<ViewDocument>>()
                }
            }
        }

        post {
            val view = call.receive<View>()
            val created = viewService.upsert(view)
            call.respond(HttpStatusCode.Created, created)
        }.describe {
            operationId = "views/post"
            summary = "Create View"
            description = "Creates or replaces a View by name"

            requestBody {
                description = "The View spec to store"
                content {
                    schema = jsonSchema<View>()
                }
            }

            responses {
                HttpStatusCode.Created {
                    description = "View created"
                    schema = jsonSchema<ViewDocument>()
                }
            }
        }

        route("/{name}") {
            get {
                val name = ViewName(call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing name"))
                val view = viewService.findByName(name)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "View not found: ${name.value}")
                call.respond(HttpStatusCode.OK, view)
            }.describe {
                operationId = "views/{name}/get"
                summary = "Get View"
                description = "Get a View by name"

                responses {
                    HttpStatusCode.OK {
                        description = "View found"
                        schema = jsonSchema<ViewDocument>()
                    }
                    HttpStatusCode.NotFound {
                        description = "View not found"
                    }
                }
            }

            put {
                val name = ViewName(call.parameters["name"] ?: return@put call.respond(HttpStatusCode.BadRequest, "missing name"))
                val view = call.receive<View>()
                val nameMatchingView = View(name = name, predicate = view.predicate, groupByKeys = view.groupByKeys)
                val updated = viewService.upsert(nameMatchingView)
                call.respond(HttpStatusCode.OK, updated)
            }.describe {
                operationId = "views/{name}/put"
                summary = "Upsert View"
                description = "Create or replace a View by name (path name takes precedence)"

                requestBody {
                    description = "The updated View spec"
                    content {
                        schema = jsonSchema<View>()
                    }
                }

                responses {
                    HttpStatusCode.OK {
                        description = "View upserted"
                        schema = jsonSchema<ViewDocument>()
                    }
                }
            }

            delete {
                val name = ViewName(call.parameters["name"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "missing name"))
                val deleted = viewService.delete(name)
                if (deleted) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, "View not found: ${name.value}")
            }.describe {
                operationId = "views/{name}/delete"
                summary = "Delete View"
                description = "Delete a View by name"

                responses {
                    HttpStatusCode.NoContent {
                        description = "View deleted"
                    }
                    HttpStatusCode.NotFound {
                        description = "View not found"
                    }
                }
            }

            post("/execute") {
                val name = ViewName(call.parameters["name"] ?: return@post call.respond(HttpStatusCode.BadRequest, "missing name"))
                val scope = runCatching { call.receiveNullable<ExecuteScopeRequest>() }.getOrNull() ?: ExecuteScopeRequest()
                val view = viewService.findByName(name)?.toView()
                    ?: return@post call.respond(HttpStatusCode.NotFound, "View not found: ${name.value}")

                val targetIds: List<String> = scope.hdtIds.ifEmpty {
                    hdtService.findAll().map { it.hdtId.id }
                }

                val result: Map<String, ViewResult> = targetIds.associate { idStr ->
                    val hdtId = HdtId(idStr)
                    val props = propertyService.findByHdtId(hdtId).map { it.toProperty() }
                    idStr to view.execute(props)
                }
                call.respond(HttpStatusCode.OK, result)
            }.describe {
                operationId = "views/{name}/execute"
                summary = "Execute View"
                description = "Run a stored View against HDTs. Empty or absent hdtIds executes against all HDTs."

                requestBody {
                    description = "Optional scope — which HDT IDs to execute against"
                    content {
                        schema = jsonSchema<ExecuteScopeRequest>()
                    }
                }

                responses {
                    HttpStatusCode.OK {
                        description = "ViewResult per HDT id"
                    }
                    HttpStatusCode.NotFound {
                        description = "View not found"
                    }
                }
            }
        }
    }
}
