package com.quantumff.segue.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.repository.FakeAppUpdateRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Material You is only offered where it works: Android 12 (API 31) and later. */
@RunWith(RobolectricTestRunner::class)
class SettingsAccentSourceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()

    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy {
        SettingsViewModel(
            FakeServerRepository(),
            preferences,
            FakeDownloadRepository(),
            FakeAppUpdateRepository(),
            Snackbars(),
        )
    }

    @Test
    @Config(sdk = [30])
    fun android11LeavesOutMaterialYou() = runTest {
        viewModel.uiState.test {
            val offered = expectMostRecentItem().accentSources
            assertThat(offered).containsExactly(AccentSource.Fixed, AccentSource.AlbumArt).inOrder()
        }
    }

    @Test
    @Config(sdk = [30])
    fun android11ReadsAStoredMaterialYouAsFixed() = runTest {
        preferences.preferences.value = UserPreferences(accentSource = AccentSource.MaterialYou)

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().preferences.accentSource).isEqualTo(AccentSource.Fixed)
        }
        assertThat(preferences.preferences.value.accentSource).isEqualTo(AccentSource.MaterialYou)
    }

    @Test
    @Config(sdk = [31])
    fun android12OffersMaterialYou() = runTest {
        preferences.preferences.value = UserPreferences(accentSource = AccentSource.MaterialYou)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            val all = listOf(AccentSource.Fixed, AccentSource.MaterialYou, AccentSource.AlbumArt)
            assertThat(state.accentSources).containsExactlyElementsIn(all).inOrder()
            assertThat(state.preferences.accentSource).isEqualTo(AccentSource.MaterialYou)
        }
    }
}
