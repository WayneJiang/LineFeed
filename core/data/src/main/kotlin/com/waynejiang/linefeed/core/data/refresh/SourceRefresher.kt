package com.waynejiang.linefeed.core.data.refresh

import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.refresh.SourceResult

/**
 * Data-layer-only refresh hook for sources that are a plain "replace the cache with the latest
 * snapshot" (weather, service cards). Kept `internal` and out of `core:domain`: whether a source
 * refreshes itself this way, or (like articles) via a `RemoteMediator`, is an implementation
 * detail the domain layer never needs to see — it only ever calls [FeedRefresher.refresh].
 */
internal interface SourceRefresher {
    val source: ContentSource
    suspend fun refresh(): SourceResult
}
