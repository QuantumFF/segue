package com.quantumff.segue.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddToPlaylistUiState(
    /** Playlists the user can add to: their own, not read-only ones. */
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val isSaving: Boolean = false,
    val failed: Boolean = false,
    /** The songs were added; the sheet closes. */
    val isDone: Boolean = false,
)

/** Backs [AddToPlaylistSheet]: adds songs to an existing playlist or to a new one. */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(private val playlistRepository: PlaylistRepository) : ViewModel() {
    private val local = MutableStateFlow(AddToPlaylistUiState())

    val uiState: StateFlow<AddToPlaylistUiState> = combine(playlistRepository.playlists, local) { playlists, local ->
        local.copy(playlists = playlists.filterNot { it.readonly }.toImmutableList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddToPlaylistUiState())

    fun addTo(playlistId: String, songIds: List<String>) = save { playlistRepository.addSongs(playlistId, songIds) }

    fun createWith(name: String, songIds: List<String>) = save { playlistRepository.createPlaylist(name, songIds) }

    /**
     * Clears saving results when the sheet closes. The app may host the sheet outside a navigation
     * entry, so this ViewModel can outlive one showing of the sheet.
     */
    fun reset() = local.update { AddToPlaylistUiState() }

    private fun save(block: suspend () -> Unit) {
        if (local.value.isSaving) return
        local.update { it.copy(isSaving = true, failed = false) }
        viewModelScope.launch {
            val failed = remoteOutcome { block() }.isFailure
            local.update { it.copy(isSaving = false, failed = failed, isDone = !failed) }
        }
    }
}
