package com.waynejiang.linefeed.core.domain.network

import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import kotlinx.coroutines.flow.Flow

/** Indirection over connectivity so refresh/freshness logic never touches `ConnectivityManager` directly. */
interface NetworkMonitor {
    val status: Flow<NetworkStatus>
}
