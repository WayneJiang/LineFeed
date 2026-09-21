package com.waynejiang.linefeed.core.domain.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures concurrent callers for the same [key] share one in-flight execution of [block] instead
 * of each triggering their own (e.g. a foreground event and a network-restored event landing at
 * the same time both wanting to refresh the same source). The work runs on [scope], so cancelling
 * one caller's coroutine does not cancel the shared work for the others still awaiting it.
 */
class SingleFlight<K : Any, V>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val (deferred, shouldRun) = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                val created = CompletableDeferred<V>()
                inFlight[key] = created
                created to true
            }
        }

        if (shouldRun) {
            scope.launch {
                try {
                    deferred.complete(block())
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                } finally {
                    mutex.withLock { inFlight.remove(key) }
                }
            }
        }

        return deferred.await()
    }
}
