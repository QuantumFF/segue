package com.quantumff.segue.core.playback.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The default network's type, from connectivity callbacks (no polling). */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val type = MutableStateFlow(typeOf(connectivity.getNetworkCapabilities(connectivity.activeNetwork)))

    val networkType: StateFlow<NetworkType> = type.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    type.value = typeOf(capabilities)
                }

                override fun onLost(network: Network) {
                    type.value = NetworkType.None
                }
            },
        )
    }

    fun current(): NetworkType = type.value

    private fun typeOf(capabilities: NetworkCapabilities?): NetworkType = when {
        capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
            NetworkType.None

        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED) ->
            NetworkType.Unmetered

        else -> NetworkType.Metered
    }
}
