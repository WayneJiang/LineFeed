package com.waynejiang.linefeed.core.domain.refresh

import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** One raw input event: either a foreground/background transition, or a network status change. */
private sealed interface RawEvent {
    data class Foreground(val isForeground: Boolean) : RawEvent
    data class Network(val status: NetworkStatus) : RawEvent
}

/**
 * Turns raw foreground/network signals into the discrete moments PLAN.md §3.3 says should trigger
 * a refresh evaluation. Pure `Flow` logic (no Android types) so it's testable with Turbine alone:
 * - first `true` on [isForeground] -> [RefreshTrigger.COLD_START]; every later false->true -> [RefreshTrigger.FOREGROUND]
 * - while foreground, `OFFLINE` -> `METERED`/`UNMETERED` -> [RefreshTrigger.NETWORK_RESTORED]
 * - background network changes, and METERED<->UNMETERED transitions, never emit anything
 */
fun refreshTriggers(
    isForeground: Flow<Boolean>,
    network: Flow<NetworkStatus>,
): Flow<RefreshTrigger> = flow {
    var everForeground = false
    var currentlyForeground = false
    var lastNetwork: NetworkStatus? = null

    val merged: Flow<RawEvent> = merge(
        isForeground.distinctUntilChanged().map { RawEvent.Foreground(it) },
        network.distinctUntilChanged().map { RawEvent.Network(it) },
    )

    merged.collect { event ->
        when (event) {
            is RawEvent.Foreground -> {
                val wasForeground = currentlyForeground
                currentlyForeground = event.isForeground
                if (event.isForeground && !wasForeground) {
                    if (!everForeground) {
                        everForeground = true
                        emit(RefreshTrigger.COLD_START)
                    } else {
                        emit(RefreshTrigger.FOREGROUND)
                    }
                }
            }

            is RawEvent.Network -> {
                val previous = lastNetwork
                lastNetwork = event.status
                if (currentlyForeground && previous == NetworkStatus.OFFLINE && event.status != NetworkStatus.OFFLINE) {
                    emit(RefreshTrigger.NETWORK_RESTORED)
                }
            }
        }
    }
}
