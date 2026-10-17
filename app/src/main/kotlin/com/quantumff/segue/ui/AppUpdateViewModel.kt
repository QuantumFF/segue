package com.quantumff.segue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.repository.AppUpdateRepository
import com.quantumff.segue.core.model.AppRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The automatic update check on app start ([AppUpdateRepository.checkAutomatically]). */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(updates: AppUpdateRepository) : ViewModel() {
    private val found = Channel<AppRelease>(Channel.CONFLATED)

    /** A newer release, at most once per check; the shell offers it in a snackbar. */
    val availableUpdate: Flow<AppRelease> = found.receiveAsFlow()

    init {
        viewModelScope.launch { updates.checkAutomatically()?.let { found.send(it) } }
    }
}
