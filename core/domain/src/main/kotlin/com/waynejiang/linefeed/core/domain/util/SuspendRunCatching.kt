package com.waynejiang.linefeed.core.domain.util

import kotlinx.coroutines.CancellationException

/**
 * Like `runCatching`, but re-throws [CancellationException] instead of wrapping it into a
 * [Result.failure]. Plain `runCatching` swallowing cancellation is a classic coroutines bug: it
 * breaks structured concurrency (a cancelled child looks like an ordinary failure to its caller).
 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
