package com.quantumff.segue.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val servers: ImmutableList<Server> = persistentListOf(),
    val activeServerId: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val downloads: DownloadPreferences = DownloadPreferences(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        serverRepository.servers,
        serverRepository.activeServer,
        preferencesRepository.preferences,
        downloadRepository.preferences,
    ) { servers, active, preferences, downloads ->
        SettingsUiState(
            servers = servers,
            activeServerId = active?.id,
            preferences = preferences,
            downloads = downloads,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun switchServer(serverId: String) = launch { serverRepository.switchTo(serverId) }

    fun removeServer(serverId: String) = launch { serverRepository.remove(serverId) }

    fun setThemeMode(mode: ThemeMode) = launch { preferencesRepository.setThemeMode(mode) }

    fun setAccentSource(source: AccentSource) = launch { preferencesRepository.setAccentSource(source) }

    fun setAccentPreset(preset: AccentPreset) = launch { preferencesRepository.setAccentPreset(preset) }

    fun setReplayGainMode(mode: ReplayGainMode) = launch { preferencesRepository.setReplayGainMode(mode) }

    /** Snaps to [PREAMP_STEP_DB] and clamps to [UserPreferences.REPLAY_GAIN_PREAMP_RANGE]. */
    fun setReplayGainPreamp(db: Float) = launch {
        val snapped = (db / PREAMP_STEP_DB).roundToInt() * PREAMP_STEP_DB
        preferencesRepository.setReplayGainPreampDb(snapped.coerceIn(UserPreferences.REPLAY_GAIN_PREAMP_RANGE))
    }

    fun setWifiQuality(quality: StreamingQuality) = launch { preferencesRepository.setWifiStreamingQuality(quality) }

    fun setCellularQuality(quality: StreamingQuality) =
        launch { preferencesRepository.setCellularStreamingQuality(quality) }

    fun setStreamCacheSize(megabytes: Int) = launch { preferencesRepository.setStreamCacheSizeMb(megabytes) }

    fun setStopWhenPaused(minutes: Int) = launch { preferencesRepository.setStopWhenPausedMinutes(minutes) }

    fun setDownloadQuality(quality: StreamingQuality) = launch { downloadRepository.setQuality(quality) }

    fun setDownloadWifiOnly(enabled: Boolean) = launch { downloadRepository.setWifiOnly(enabled) }

    fun setDownloadChargingOnly(enabled: Boolean) = launch { downloadRepository.setChargingOnly(enabled) }

    fun setOfflineMode(enabled: Boolean) = launch { downloadRepository.setForceOffline(enabled) }

    fun setQueueSyncEnabled(enabled: Boolean) = launch { preferencesRepository.setQueueSyncEnabled(enabled) }

    fun setAdoptRemoteQueue(enabled: Boolean) =
        launch { preferencesRepository.setAdoptRemoteQueueAutomatically(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val PREAMP_STEP_DB = 0.5f
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
