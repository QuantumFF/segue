package com.quantumff.segue.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A one-off message for the snackbar. */
enum class PlaylistMessage {
    /** A server call failed. */
    Failed,

    /** Saving playlist edits failed; the edits are kept. */
    SaveFailed,
}

data class PlaylistsUiState(
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val serverName: String? = null,
    /** True until Room's first emission, so the empty state does not flash. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val message: PlaylistMessage? = null,
)

/** The Playlists tab: Room's playlists, refreshed on entry and on pull-to-refresh, plus CRUD. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    serverRepository: ServerRepository,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PlaylistsUiState> = combine(
        playlistRepository.playlists,
        serverRepository.activeServer,
        local,
    ) { playlists, server, local ->
        PlaylistsUiState(
            playlists = playlists,
            serverName = server?.name,
            isLoading = false,
            isRefreshing = local.refreshing,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistsUiState())

    init {
        // On entry a failure stays silent: the cached list is shown (offline is first-class).
        viewModelScope.launch { runRemote { playlistRepository.refreshPlaylists() } }
    }

    /** Pull-to-refresh. Reports a failure, since the user asked for it. */
    fun refresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val failed = runRemote { playlistRepository.refreshPlaylists() }
            local.update { it.copy(refreshing = false, message = if (failed) PlaylistMessage.Failed else it.message) }
        }
    }

    fun create(name: String) = write { playlistRepository.createPlaylist(name) }

    fun rename(playlist: Playlist, name: String) = write { playlistRepository.updatePlaylist(playlist.id, name = name) }

    fun delete(playlist: Playlist) = write { playlistRepository.deletePlaylist(playlist.id) }

    /** Loads the playlist's songs (fetching them when not cached yet) and hands them to [onLoaded]. */
    fun loadSongs(playlistId: String, onLoaded: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val cached = playlistRepository.playlistSongs(playlistId).first()
            val songs = cached.ifEmpty {
                if (runRemote { playlistRepository.refreshPlaylist(playlistId) }) {
                    local.update { it.copy(message = PlaylistMessage.Failed) }
                    return@launch
                }
                playlistRepository.playlistSongs(playlistId).first()
            }
            if (songs.isNotEmpty()) onLoaded(songs)
        }
    }

    fun onMessageDismiss() = local.update { it.copy(message = null) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (runRemote(block)) local.update { it.copy(message = PlaylistMessage.Failed) }
        }
    }

    private data class LocalState(val refreshing: Boolean = false, val message: PlaylistMessage? = null)
}

/** Runs a server call; returns true when it failed with a [RemoteException]. */
internal suspend fun runRemote(block: suspend () -> Unit): Boolean = try {
    block()
    false
} catch (_: RemoteException) {
    true
}

internal const val STOP_TIMEOUT_MS = 5_000L
