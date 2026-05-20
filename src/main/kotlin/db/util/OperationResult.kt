package io.github.whdt.db.util

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond

/**
 * Represents the result of an operation that can either succeed with a value of type [T]
 * or fail with an error.
 *
 * This is a lightweight alternative to exceptions, making failures explicit in the type system
 * and enabling safer composition of operations.
 *
 * @param T The type of the successful result.
 */
sealed class OperationResult<out T>

/**
 * Represents a successful operation result.
 *
 * @param result The value produced by the successful operation.
 * @param T The type of the result.
 */
data class Ok<out T>(val result: T) : OperationResult<T>()

/**
 * Represents a failed operation result.
 *
 * @param message A human-readable description of the error.
 * @param cause The optional underlying exception that caused the failure.
 */
data class Err(
    val message: String,
    val cause: Throwable? = null
) : OperationResult<Nothing>()

/**
 * Executes the given [block] and captures its result as an [OperationResult].
 *
 * If the block executes successfully, returns [Ok] with the result.
 * If an exception is thrown, returns [Err] containing the exception message and cause.
 *
 * This is useful to wrap exception-throwing code into a safer, composable result type.
 *
 * @param T The type of the result.
 * @param block The operation to execute.
 * @return An [OperationResult] representing success or failure.
 */
inline fun <T> runCatchingResult(block: () -> T): OperationResult<T> =
    try {
        Ok(block())
    } catch (e: Exception) {
        Err(e.message ?: "Unknown error", e)
    }

/**
 * Extracts the successful result or responds with an error using the provided [ApplicationCall].
 *
 * If this is [Ok], returns the contained result.
 * If this is [Err], invokes [onError] to handle the error (e.g., send an HTTP response)
 * and returns null to allow early exit from the calling context.
 *
 * This is particularly useful in Ktor routes to keep control flow flat and readable.
 *
 * @param call The current Ktor [ApplicationCall].
 * @param onError A suspend function to handle the error case (e.g., sending an HTTP response).
 * @return The result value if successful, or null if an error occurred.
 */
suspend inline fun <T> OperationResult<T>.getOrRespond(
    call: ApplicationCall,
    onError: suspend (Err) -> Unit
): T? {
    return when (this) {
        is Ok -> result
        is Err -> {
            onError(this)
            null
        }
    }
}

/**
 * Applies a suspend [block] to each element of the iterable, collecting results into a list.
 *
 * The operation is fail-fast:
 * - If all invocations succeed, returns [Ok] with the list of results.
 * - If any invocation returns [Err], the process stops immediately and that error is returned.
 *
 * This is useful for batch operations such as inserting multiple entities into a database.
 *
 * @param A The type of the input elements.
 * @param B The type of the result produced by [block].
 * @param block A suspend function applied to each element.
 * @return An [OperationResult] containing either the list of results or the first encountered error.
 */
suspend fun <A, B> Iterable<A>.sequenceResults(
    block: suspend (A) -> OperationResult<B>
): OperationResult<List<B>> {
    val results = mutableListOf<B>()

    for (item in this) {
        when (val res = block(item)) {
            is Ok -> results += res.result
            is Err -> return res
        }
    }

    return Ok(results)
}

/**
 * Handles an [OperationResult] by invoking [onSuccess] on the happy path, or responding with
 * a 500 Internal Server Error on failure.
 */
suspend inline fun <T> OperationResult<T>.andThen(
    call: ApplicationCall,
    onSuccess: suspend (T) -> Unit
) {
    when (this) {
        is Ok -> onSuccess(result)
        is Err -> {
            call.respond(HttpStatusCode.InternalServerError, message)
        }
    }
}

/**
 * Aggregates a successful [OperationResult] containing a list of integers into their sum.
 *
 * - If this is [Ok], returns a new [Ok] with the sum of the list.
 * - If this is [Err], propagates the error unchanged.
 *
 * This is commonly used after [sequenceResults] when each operation returns an integer
 * (e.g., number of inserted records) and a total is desired.
 *
 * @return An [OperationResult] containing the sum or the original error.
 */
fun OperationResult<List<Int>>.sum(): OperationResult<Int> =
    when (this) {
        is Ok -> Ok(result.sum())
        is Err -> this
    }