package com.quantumff.segue.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.AddedSongs
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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

/**
 * Backs [AddToPlaylistSheet]: adds songs to an existing playlist or to a new one, then confirms it
 * with a snackbar whose View asks the app, through [playlistsToOpen], to open the playlist.
 */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val snackbars: Snackbars,
) : ViewModel() {
    private val local = MutableStateFlow(AddToPlaylistUiState())
    private val openRequests = Channel<String>(Channel.BUFFERED)

    val uiState: StateFlow<AddToPlaylistUiState> = combine(playlistRepository.playlists, local) { playlists, local ->
        local.copy(playlists = playlists.filterNot { it.readonly }.toImmutableList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddToPlaylistUiState())

    /** IDs of playlists whose confirmation's View was tapped, for the app to open. */
    val playlistsToOpen: Flow<String> = openRequests.receiveAsFlow()

    /** Songs already in [playlist] are skipped, and the confirmation says how many. */
    fun addTo(playlist: Playlist, songIds: List<String>) = save {
        confirm(playlist.id, playlist.name, playlistRepository.addSongs(playlist.id, songIds))
    }

    fun createWith(name: String, songIds: List<String>) = save {
        val id = playlistRepository.createPlaylist(name, songIds)
        confirm(id, name, AddedSongs(added = songIds.size, alreadyPresent = 0))
    }

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

    /** "Added 3 songs to Road trip", and how many were already there, with View. */
    private fun confirm(playlistId: String, name: String, result: AddedSongs) {
        val (added, skipped) = result
        val text = when {
            added == 0 -> SnackbarText.plural(R.plurals.feature_playlist_already_in, skipped, skipped, name)
            skipped == 0 -> SnackbarText.plural(R.plurals.feature_playlist_added_to, added, added, name)
            else -> SnackbarText.plural(R.plurals.feature_playlist_added_to_skipping, added, added, name, skipped)
        }
        snackbars.post(
            text,
            SnackbarAction(SnackbarText.of(R.string.feature_playlist_view)) {
                openRequests.trySend(playlistId)
            },
        )
    }
}
