package com.waynejiang.linefeed.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.waynejiang.linefeed.core.data.di.ApplicationScope
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.refresh.FeedRefresher
import com.waynejiang.linefeed.core.domain.refresh.refreshTriggers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * The one place PLAN.md §3.3's refresh triggers (foreground/background, network restored) turn
 * into an actual [FeedRefresher.refresh] call — no background sync, only foreground-driven,
 * stale-while-revalidate (PLAN.md §0). Lives in `app` (not `core:data`) because it is the only
 * class that needs `ProcessLifecycleOwner`, an Android-application-process concept.
 */
@Singleton
class AppRefreshInitializer @Inject constructor(
    private val feedRefresher: FeedRefresher,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        refreshTriggers(isForeground = processForegroundFlow(), network = networkMonitor.status)
            .onEach { trigger -> feedRefresher.refresh(trigger) }
            .launchIn(scope)
    }

    private fun processForegroundFlow(): Flow<Boolean> = callbackFlow {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> trySend(true)
                Lifecycle.Event.ON_STOP -> trySend(false)
                else -> Unit
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        withContext(Dispatchers.Main.immediate) { lifecycle.addObserver(observer) }
        awaitClose {
            // `removeObserver` must also happen on the main thread; this scope may be cancelled
            // from any dispatcher, so hop back explicitly rather than assuming the caller's thread.
            kotlinx.coroutines.runBlocking(Dispatchers.Main.immediate) { lifecycle.removeObserver(observer) }
        }
    }
}
