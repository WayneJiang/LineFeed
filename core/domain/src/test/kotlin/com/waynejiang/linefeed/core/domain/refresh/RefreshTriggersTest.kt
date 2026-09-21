package com.waynejiang.linefeed.core.domain.refresh

import app.cash.turbine.test
import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RefreshTriggersTest {
    @Test
    fun `first time foreground is COLD_START, later foreground transitions are FOREGROUND`() = runTest {
        val isForeground = MutableStateFlow(false)
        val network = MutableStateFlow(NetworkStatus.UNMETERED)

        refreshTriggers(isForeground, network).test {
            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.COLD_START)

            isForeground.value = false
            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.FOREGROUND)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network restored while foreground emits NETWORK_RESTORED`() = runTest {
        val isForeground = MutableStateFlow(false)
        val network = MutableStateFlow(NetworkStatus.UNMETERED)

        refreshTriggers(isForeground, network).test {
            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.COLD_START)

            network.value = NetworkStatus.OFFLINE
            network.value = NetworkStatus.UNMETERED
            assert(awaitItem() == RefreshTrigger.NETWORK_RESTORED)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network recovery in background does not emit, later foreground emits once`() = runTest {
        val isForeground = MutableStateFlow(false)
        val network = MutableStateFlow(NetworkStatus.UNMETERED)

        refreshTriggers(isForeground, network).test {
            network.value = NetworkStatus.OFFLINE
            network.value = NetworkStatus.UNMETERED
            expectNoEvents()

            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.COLD_START)

            isForeground.value = false
            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.FOREGROUND)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `metered and unmetered transitions never emit`() = runTest {
        val isForeground = MutableStateFlow(false)
        val network = MutableStateFlow(NetworkStatus.UNMETERED)

        refreshTriggers(isForeground, network).test {
            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.COLD_START)

            network.value = NetworkStatus.METERED
            network.value = NetworkStatus.UNMETERED
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeated foreground true does not re-emit`() = runTest {
        val isForeground = MutableStateFlow(false)
        val network = MutableStateFlow(NetworkStatus.UNMETERED)

        refreshTriggers(isForeground, network).test {
            isForeground.value = true
            assert(awaitItem() == RefreshTrigger.COLD_START)

            isForeground.value = true
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
