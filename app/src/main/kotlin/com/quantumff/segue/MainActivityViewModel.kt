package com.quantumff.segue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.designsystem.theme.AccentPreset
import com.quantumff.segue.core.designsystem.theme.AccentSource
import com.quantumff.segue.core.designsystem.theme.SegueThemeConfig
import com.quantumff.segue.core.designsystem.theme.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface MainActivityUiState {
    /** DataStore has not answered yet: draw only the background. */
    data object Loading : MainActivityUiState

    data class Ready(val theme: SegueThemeConfig, val hasServer: Boolean) : MainActivityUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(preferences: UserPreferencesRepository, servers: ServerRepository) :
    ViewModel() {
    val uiState: StateFlow<MainActivityUiState> =
        combine(preferences.preferences, servers.activeServer) { prefs, server ->
            MainActivityUiState.Ready(theme = prefs.toThemeConfig(), hasServer = server != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainActivityUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The design system has its own theme enums; the stored settings map onto them by name. */
internal fun UserPreferences.toThemeConfig() = SegueThemeConfig(
    themeMode = ThemeMode.valueOf(themeMode.name),
    accentSource = AccentSource.valueOf(accentSource.name),
    accentPreset = AccentPreset.valueOf(accentPreset.name),
)
