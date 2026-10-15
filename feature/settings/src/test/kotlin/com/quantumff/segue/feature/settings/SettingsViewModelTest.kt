package com.quantumff.segue.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val servers = FakeServerRepository()
    private val preferences = FakeUserPreferencesRepository()
    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { SettingsViewModel(servers, preferences) }

    private val home = TestData.server.copy(id = "home", name = "Home")

    @Test
    fun stateShowsServersAndTheActiveOne() = runTest {
        servers.serversState.value = listOf(TestData.server, home)
        servers.activeId.value = "home"

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.servers.map { it.id }).containsExactly(TestData.server.id, "home").inOrder()
            assertThat(state.activeServerId).isEqualTo("home")
        }
    }

    @Test
    fun appearanceSettersReachTheRepository() = runTest {
        viewModel.setThemeMode(ThemeMode.Black)
        viewModel.setAccentSource(AccentSource.AlbumArt)
        viewModel.setAccentPreset(AccentPreset.Teal)

        val prefs = preferences.preferences.value
        assertThat(prefs.themeMode).isEqualTo(ThemeMode.Black)
        assertThat(prefs.accentSource).isEqualTo(AccentSource.AlbumArt)
        assertThat(prefs.accentPreset).isEqualTo(AccentPreset.Teal)
    }

    @Test
    fun playbackStreamingAndQueueSettersReachTheRepository() = runTest {
        val opus = StreamingQuality(maxBitRateKbps = 128, format = "opus")
        viewModel.setReplayGainMode(ReplayGainMode.Album)
        viewModel.setWifiQuality(StreamingQuality.Original)
        viewModel.setCellularQuality(opus)
        viewModel.setAudioCacheSize(2048)
        viewModel.setQueueSyncEnabled(false)
        viewModel.setAdoptRemoteQueue(true)

        val prefs = preferences.preferences.value
        assertThat(prefs.replayGainMode).isEqualTo(ReplayGainMode.Album)
        assertThat(prefs.wifiStreamingQuality).isEqualTo(StreamingQuality.Original)
        assertThat(prefs.cellularStreamingQuality).isEqualTo(opus)
        assertThat(prefs.audioCacheSizeMb).isEqualTo(2048)
        assertThat(prefs.queueSyncEnabled).isFalse()
        assertThat(prefs.adoptRemoteQueueAutomatically).isTrue()
    }

    @Test
    fun preampSnapsToHalfDecibelsAndClamps() = runTest {
        viewModel.setReplayGainPreamp(3.3f)
        assertThat(preferences.preferences.value.replayGainPreampDb).isEqualTo(3.5f)

        viewModel.setReplayGainPreamp(42f)
        assertThat(preferences.preferences.value.replayGainPreampDb).isEqualTo(15f)

        viewModel.setReplayGainPreamp(-20f)
        assertThat(preferences.preferences.value.replayGainPreampDb).isEqualTo(-15f)
    }

    @Test
    fun switchAndRemoveServer() = runTest {
        servers.serversState.value = listOf(TestData.server, home)
        servers.activeId.value = TestData.server.id

        viewModel.switchServer("home")
        assertThat(servers.activeId.value).isEqualTo("home")

        viewModel.removeServer("home")
        assertThat(servers.serversState.value.map { it.id }).containsExactly(TestData.server.id)
        assertThat(servers.activeId.value).isEqualTo(TestData.server.id)
    }

    @Test
    fun preampLabelShowsSignAndOneDecimal() {
        assertThat(preampLabel(3f)).isEqualTo("+3.0 dB")
        assertThat(preampLabel(0f)).isEqualTo("0.0 dB")
        assertThat(preampLabel(-1.5f)).isEqualTo("−1.5 dB")
    }
}
