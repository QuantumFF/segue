package com.quantumff.segue.core.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Whether the device has a validated internet connection, from connectivity callbacks (no polling). */
interface NetworkConnectivity {
    val isConnected: StateFlow<Boolean>
}

@Singleton
internal class ConnectivityManagerNetworkConnectivity @Inject constructor(@ApplicationContext context: Context) :
    NetworkConnectivity {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val connected = MutableStateFlow(hasInternet(connectivity.getNetworkCapabilities(connectivity.activeNetwork)))

    override val isConnected: StateFlow<Boolean> = connected.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    connected.value = hasInternet(capabilities)
                }

                override fun onLost(network: Network) {
                    connected.value = false
                }
            },
        )
    }

    private fun hasInternet(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

/**
 * Offline mode: no network, or the user's manual "Offline mode" switch. While offline, browse
 * screens show downloaded content and play actions skip songs that are not downloaded.
 */
interface OfflineModeRepository {
    val isOffline: StateFlow<Boolean>
}

@Singleton
internal class DefaultOfflineModeRepository @Inject constructor(
    connectivity: NetworkConnectivity,
    preferences: DownloadPreferencesDataSource,
    @ApplicationScope scope: CoroutineScope,
) : OfflineModeRepository {
    override val isOffline: StateFlow<Boolean> =
        combine(connectivity.isConnected, preferences.preferences) { connected, prefs -> !connected || prefs.forceOffline }
            .stateIn(scope, SharingStarted.Eagerly, !connectivity.isConnected.value)
}
