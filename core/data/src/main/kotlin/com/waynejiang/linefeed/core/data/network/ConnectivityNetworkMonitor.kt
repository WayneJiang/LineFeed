package com.waynejiang.linefeed.core.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * `registerDefaultNetworkCallback` (not `registerNetworkCallback` with a request) so this tracks
 * whatever network the system currently considers "the" active one, matching what an HTTP request
 * from this process would actually use. `NET_CAPABILITY_VALIDATED` (not just `NET_CAPABILITY_INTERNET`)
 * is required for "online": a captive portal / no-actual-internet Wi-Fi otherwise reads as reachable.
 */
internal class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext context: android.content.Context,
) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val status: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.toStatus())
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.OFFLINE)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        val initial = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
            ?.toStatus()
            ?: NetworkStatus.OFFLINE
        trySend(initial)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun NetworkCapabilities.toStatus(): NetworkStatus = when {
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkStatus.OFFLINE
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkStatus.UNMETERED
        else -> NetworkStatus.METERED
    }
}
