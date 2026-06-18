# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

`persistence-service` is one of several services in the `whdt-monitor` project. Sibling services include:
- **hdt-creation-service** — ingests HDT specs from CSV/YAML and calls this service to persist them
- **whdt-monitor-frontend** — UI layer
- **API gateway** — routes external traffic to the services

This service owns all persistence of Human Digital Twin (HDT) data: creating/updating HDTs, models, properties, and recording property events for time-series querying and analytics.

## Commands

```bash
./gradlew run           # Run the app locally (port 8081)
./gradlew build         # Full build including tests
./gradlew test          # Run tests only
./gradlew buildFatJar   # Build self-contained JAR
./gradlew buildImage    # Build Docker image tarball
./gradlew publishImageToLocalRegistry  # Push image to local Docker registry
```

## Environment & Configuration

All configuration lives in `src/main/resources/application.yaml`. The MongoDB connection currently targets a remote Atlas cluster — there is no local/Docker MongoDB setup yet (planned future task).

Credentials are injected via environment variables:
```
MONGO_DB_NAME    # database name
MONGO_USER       # MongoDB username
MONGO_PASSWORD   # MongoDB password
MONGO_URI        # MongoDB uri
```

## Architecture

### Layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| Routing | `routing/` | HTTP endpoints, OpenAPI docs, request/response DTOs |
| Service | `db/*/` | Business logic, MongoDB operations |
| Documents | `db/*/*Document.kt` | MongoDB data classes; bidirectional `.toDocument()` / `fromDocument()` conversion |
| Utilities | `db/util/`, `util/` | `OperationResult`, value conversion |

Wiring happens in `Routing.kt` at startup: services are instantiated there, given a MongoDB database handle from `Databases.kt`, and injected into route functions.

### Key Pattern: OperationResult

All service methods return `OperationResult<T>` — a sealed class (`Ok<T>` / `Err`) — instead of throwing exceptions. Routes use extension functions like `getOrRespond()` and `sequenceResults()` to compose results and short-circuit on errors. **Do not revert to exception-based control flow.**

### Key Pattern: Coroutines + Dispatchers.IO

All database calls are `suspend` functions wrapped in `withContext(Dispatchers.IO)` because the MongoDB driver is blocking. Keep this pattern for any new database operations.

### MongoDB Specifics

- **Time-series collection** (`observations`) — uses MongoDB time-series with a meta field for HDT/model/property metadata and a time field for timestamps. Queried via aggregation pipelines.
- **Bulk operations** — HDTs, models, and property events all support batch insert/upsert via `bulkWrite()`.
- **No transactions** — cross-collection consistency (e.g., inserting an HDT alongside its models) relies on sequential writes, not MongoDB multi-document transactions.

### Polymorphic PropertyValue

`PropertyValue` is a sealed class (Int, Long, Float, Double, String, Boolean, Empty). Use `Any.pv()` to convert raw values and `PropertyValue.toBsonValue()` / `unwrapAndStringify()` when writing to or reading from BSON.

### OpenApI
All routes used the `.describe { }` DSL to document request/response shapes. This is now deprecated due to issues with the Ktor swagger component. All edits/additions/deletions to routes need to be followed by a manual edit of the `openapi/openapi.yaml` file.
The spec is served at `/swaggerUI`.

## WHDT Dependency Updates

The service depends on `whdt-core` and `whdt-distributed`, published from the `whdt` repository to GitHub Package Registry. These modules are not yet stable, so version bumps are expected frequently.

**Convention:** dependency updates go on a `feat/update-whdt-dependency` branch. When types or utilities change in those libraries, update all callers in this service on the same branch before merging.

## Known Debt

- **Testing** — No tests exist yet. This is a critical gap. New features must ship with tests; an important ongoing task is identifying what needs coverage and back-filling it. Integration tests against a real MongoDB instance are the intended approach (there is no in-memory MongoDB mock).
- **CORS** — `HTTP.kt` currently allows all origins. This must be restricted to known hosts before any production hardening.
- **Local MongoDB** — Development currently requires credentials for the remote Atlas cluster. Setting up a Docker-based local MongoDB is a planned task.
