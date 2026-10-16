package com.quantumff.segue.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    /** Pin status of pinned playlists by ID. */
    val downloads: ImmutableMap<String, DownloadStatus> = persistentMapOf(),
    /** Offline: only pinned playlists are listed. */
    val isOffline: Boolean = false,
)

/** The Playlists tab: Room's playlists, refreshed on entry and on pull-to-refresh, plus CRUD. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    serverRepository: ServerRepository,
    offlineView: OfflineLibraryView,
    private val mediaActions: MediaActionHandler,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PlaylistsUiState> = combine(
        offlineView.playlists,
        serverRepository.activeServer,
        local,
        offlineView.pinStatuses.map { it[PinKind.Playlist] },
        offlineView.isOffline,
    ) { playlists, server, local, downloads, offline ->
        PlaylistsUiState(
            playlists = playlists,
            downloads = downloads,
            isOffline = offline,
            serverName = server?.name,
            isLoading = false,
            isRefreshing = local.refreshing,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistsUiState())

    init {
        // On entry a failure stays silent: the cached list is shown (offline is first-class).
        viewModelScope.launch { remoteOutcome { playlistRepository.refreshPlaylists() } }
    }

    /** Pull-to-refresh. Reports a failure, since the user asked for it. */
    fun refresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val failed = remoteOutcome { playlistRepository.refreshPlaylists() }.isFailure
            local.update { it.copy(refreshing = false, message = if (failed) PlaylistMessage.Failed else it.message) }
        }
    }

    fun create(name: String) = write { playlistRepository.createPlaylist(name) }

    fun rename(playlist: Playlist, name: String) = write { playlistRepository.updatePlaylist(playlist.id, name = name) }

    fun delete(playlist: Playlist) = write { playlistRepository.deletePlaylist(playlist.id) }

    /** A playlist's ⋮ menu ([MediaActionHandler]); Rename and Delete stay with the screen's dialogs. */
    fun onPlaylistAction(playlist: Playlist, action: MediaAction) {
        viewModelScope.launch {
            if (mediaActions.playlist(playlist, action, MediaActionHost()) != null) {
                local.update { it.copy(message = PlaylistMessage.Failed) }
            }
        }
    }

    fun onMessageDismiss() = local.update { it.copy(message = null) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (remoteOutcome { block() }.isFailure) local.update { it.copy(message = PlaylistMessage.Failed) }
        }
    }

    private data class LocalState(val refreshing: Boolean = false, val message: PlaylistMessage? = null)
}

internal const val STOP_TIMEOUT_MS = 5_000L
