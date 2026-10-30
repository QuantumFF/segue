package com.quantumff.segue.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.repository.AppUpdateRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.StorageLimit
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val servers: ImmutableList<Server> = persistentListOf(),
    val activeServerId: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val downloads: DownloadPreferences = DownloadPreferences(),
    val updateCheck: UpdateCheck = UpdateCheck.Idle,
) {
    /** What removing [server] does to the active server: the first one left takes over, as in the accounts. */
    fun removalOf(server: Server): ServerRemoval {
        if (server.id != activeServerId) return ServerRemoval.KeepsActive
        val next = servers.firstOrNull { it.id != server.id } ?: return ServerRemoval.SignsOut
        return ServerRemoval.SwitchesTo(next)
    }
}

/** What the remove dialog says happens to the active server. */
sealed interface ServerRemoval {
    /** The removed server isn't the active one. */
    data object KeepsActive : ServerRemoval

    /** [server] becomes active in place of the removed one. */
    data class SwitchesTo(val server: Server) : ServerRemoval

    /** No server is left, so the user is signed out. */
    data object SignsOut : ServerRemoval
}

/** The "Check for updates" row, after a check from Settings. */
sealed interface UpdateCheck {
    data object Idle : UpdateCheck

    data object Checking : UpdateCheck

    data object UpToDate : UpdateCheck

    data class Available(val version: String) : UpdateCheck

    data object Failed : UpdateCheck
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val updateRepository: AppUpdateRepository,
    private val snackbars: Snackbars,
) : ViewModel() {
    private val updateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    private val releasesToOpen = Channel<String>(Channel.CONFLATED)

    /** The installed version, e.g. "0.1.1". */
    val versionName: String = updateRepository.installedVersion

    /** Release page URLs to open in the browser: a check found a newer version. */
    val openRelease: Flow<String> = releasesToOpen.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        serverRepository.servers,
        serverRepository.activeServer,
        preferencesRepository.preferences,
        downloadRepository.preferences,
        updateCheck,
    ) { servers, active, preferences, downloads, updateCheck ->
        SettingsUiState(
            servers = servers,
            activeServerId = active?.id,
            preferences = preferences,
            downloads = downloads,
            updateCheck = updateCheck,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    /**
     * Makes [serverId] active and says "Switched to <name>". Tapping the server that already is does
     * nothing, so it starts no sync.
     */
    fun switchServer(serverId: String) = launch {
        if (serverRepository.activeServer.first()?.id == serverId) return@launch
        val server = serverRepository.servers.first().firstOrNull { it.id == serverId } ?: return@launch
        serverRepository.switchTo(serverId)
        snackbars.post(SnackbarText.of(R.string.feature_settings_switched_to, server.name))
    }

    fun removeServer(serverId: String) = launch { serverRepository.remove(serverId) }

    /** e.g. `updatePreferences { it.copy(themeMode = ThemeMode.Dark) }`. */
    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) =
        launch { preferencesRepository.update(transform) }

    fun setDownloadQuality(quality: StreamingQuality) = launch { downloadRepository.setQuality(quality) }

    fun setDownloadWifiOnly(enabled: Boolean) = launch { downloadRepository.setWifiOnly(enabled) }

    fun setDownloadChargingOnly(enabled: Boolean) = launch { downloadRepository.setChargingOnly(enabled) }

    fun setOfflineMode(enabled: Boolean) = launch { downloadRepository.setForceOffline(enabled) }

    fun setStorageLimit(limit: StorageLimit) = launch { downloadRepository.setStorageLimit(limit) }

    /** Asks GitHub for the latest release and opens its page if it is newer than this app. */
    fun checkForUpdates() {
        if (updateCheck.value == UpdateCheck.Checking) return
        updateCheck.value = UpdateCheck.Checking
        launch {
            updateCheck.value = try {
                val release = updateRepository.check()
                if (release == null) {
                    UpdateCheck.UpToDate
                } else {
                    releasesToOpen.send(release.url)
                    UpdateCheck.Available(release.version)
                }
            } catch (_: RemoteException) {
                UpdateCheck.Failed
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Streaming quality choices: the original file, or a transcode Navidrome can produce. */
        val STREAMING_QUALITIES: ImmutableList<StreamingQuality> = persistentListOf(
            StreamingQuality.Original,
            StreamingQuality(maxBitRateKbps = 320, format = "mp3"),
            StreamingQuality(maxBitRateKbps = 192, format = "mp3"),
            StreamingQuality(maxBitRateKbps = 128, format = "mp3"),
            StreamingQuality(maxBitRateKbps = 192, format = "opus"),
            StreamingQuality(maxBitRateKbps = 128, format = "opus"),
            StreamingQuality(maxBitRateKbps = 96, format = "opus"),
        )
    }
}
