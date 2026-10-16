package com.quantumff.segue.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.model.StorageLimit
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
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
    private val downloads = FakeDownloadRepository()
    private val viewModel by lazy { SettingsViewModel(servers, preferences, downloads) }

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
    fun preferenceUpdatesReachTheRepositoryAndShowInState() = runTest {
        viewModel.updatePreferences { it.copy(themeMode = ThemeMode.Black) }
        viewModel.updatePreferences { it.copy(replayGainTarget = ReplayGainTarget.Ebu) }

        val expected = UserPreferences(themeMode = ThemeMode.Black, replayGainTarget = ReplayGainTarget.Ebu)
        assertThat(preferences.preferences.value).isEqualTo(expected)
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().preferences).isEqualTo(expected)
        }
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
    fun downloadSettersReachTheRepositoryAndShowInState() = runTest {
        val mp3 = StreamingQuality(maxBitRateKbps = 192, format = "mp3")
        viewModel.setDownloadQuality(mp3)
        viewModel.setDownloadWifiOnly(false)
        viewModel.setDownloadChargingOnly(true)
        viewModel.setOfflineMode(true)
        viewModel.setStorageLimit(StorageLimit.Gb10)

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().downloads).isEqualTo(
                DownloadPreferences(
                    quality = mp3,
                    wifiOnly = false,
                    chargingOnly = true,
                    forceOffline = true,
                    storageLimit = StorageLimit.Gb10,
                ),
            )
        }
    }
}
