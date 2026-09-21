package com.waynejiang.linefeed.core.domain.refresh

import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates refreshing all content sources under one freshness policy. This is the *only* way
 * anything gets refreshed — individual repositories never decide on their own to hit the network.
 */
interface FeedRefresher {
    val status: StateFlow<RefreshStatus>
    suspend fun refresh(trigger: RefreshTrigger): RefreshReport
}
