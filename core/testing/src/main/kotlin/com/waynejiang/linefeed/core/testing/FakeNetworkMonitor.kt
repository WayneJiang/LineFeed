package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeNetworkMonitor(initial: NetworkStatus = NetworkStatus.UNMETERED) : NetworkMonitor {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<NetworkStatus> = _status

    fun set(status: NetworkStatus) {
        _status.value = status
    }
}
