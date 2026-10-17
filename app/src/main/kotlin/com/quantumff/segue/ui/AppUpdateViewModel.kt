package com.quantumff.segue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.repository.AppUpdateRepository
import com.quantumff.segue.core.model.AppRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The automatic update check on app start ([AppUpdateRepository.checkAutomatically]). */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(updates: AppUpdateRepository) : ViewModel() {
    private val offer = MutableStateFlow<AppRelease?>(null)

    /**
     * A newer release for the shell to offer in a snackbar. It stays until [onOffered], so a snackbar
     * cut short by a configuration change shows again.
     */
    val availableUpdate: StateFlow<AppRelease?> = offer.asStateFlow()

    init {
        viewModelScope.launch { offer.value = updates.checkAutomatically() }
    }

    /** The snackbar was acted on, dismissed, or timed out. */
    fun onOffered() {
        offer.value = null
    }
}
