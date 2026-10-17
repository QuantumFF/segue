package com.quantumff.segue.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.repository.AppUpdateRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val servers: ImmutableList<Server> = persistentListOf(),
    val activeServerId: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val downloads: DownloadPreferences = DownloadPreferences(),
    val updateCheck: UpdateCheck = UpdateCheck.Idle,
)

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

    fun switchServer(serverId: String) = launch { serverRepository.switchTo(serverId) }

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
