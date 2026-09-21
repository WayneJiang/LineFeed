package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.refresh.FeedRefresher
import com.waynejiang.linefeed.core.domain.refresh.RefreshReport
import com.waynejiang.linefeed.core.domain.refresh.RefreshStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeFeedRefresher : FeedRefresher {
    private val _status = MutableStateFlow(RefreshStatus())
    override val status: StateFlow<RefreshStatus> = _status

    val triggers: MutableList<RefreshTrigger> = mutableListOf()
    var nextReport: RefreshReport = RefreshReport(emptyMap())
    var gate: CompletableDeferred<Unit>? = null

    fun setStatus(status: RefreshStatus) {
        _status.value = status
    }

    override suspend fun refresh(trigger: RefreshTrigger): RefreshReport {
        triggers.add(trigger)
        gate?.await()
        return nextReport
    }
}
