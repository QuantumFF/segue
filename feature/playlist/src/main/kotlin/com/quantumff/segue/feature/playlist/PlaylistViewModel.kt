package com.quantumff.segue.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongAvailability
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

/** A playlist entry with a key that stays unique when a song appears twice, and stable while reordering. */
data class PlaylistEntry(val key: String, val song: Song)

enum class LoadState {
    Loading,
    Loaded,

    /** The refresh failed: cached rows (if any) are shown with an offline chip. */
    Failed,
}

data class PlaylistUiState(
    val playlist: Playlist? = null,
    /** The saved order, or the working order while [isEditing]. */
    val entries: ImmutableList<PlaylistEntry> = persistentListOf(),
    val loadState: LoadState = LoadState.Loading,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    /** Set once the playlist was deleted from this screen; the screen then navigates back. */
    val isDeleted: Boolean = false,
    val message: PlaylistMessage? = null,
    /** The playlist's pin status; null when it is not pinned for offline. */
    val download: DownloadStatus? = null,
    /** Row badges; offline, rows of songs that are not downloaded are dimmed and do not play. */
    val availability: SongAvailability = SongAvailability(),
) {
    /** Only the owner's playlists can be edited, renamed or deleted. */
    val isEditable: Boolean get() = playlist != null && !playlist.readonly

    val songs: List<Song> get() = entries.map { it.song }
}

/**
 * Playlist detail (DESIGN.md §4.5): Room-backed rows, refreshed on entry, with an edit mode for
 * reorder and removal.
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val starRatingRepository: StarRatingRepository,
    private val downloadRepository: DownloadRepository,
    offlineView: OfflineLibraryView,
) : ViewModel() {
    private val playlistId: String = checkNotNull(savedStateHandle[PLAYLIST_ID_KEY]) { "Missing $PLAYLIST_ID_KEY" }
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PlaylistUiState> = combine(
        playlistRepository.playlist(playlistId),
        playlistRepository.playlistSongs(playlistId),
        local,
        combine(downloadRepository.pinStatus(PinKind.Playlist, playlistId), offlineView.songAvailability, ::Pair),
    ) { playlist, songs, local, (download, availability) ->
        PlaylistUiState(
            download = download,
            availability = availability,
            playlist = playlist,
            entries = local.working ?: songs.toEntries(),
            loadState = when {
                local.refreshFailed -> LoadState.Failed
                playlist == null && local.refreshing -> LoadState.Loading
                else -> LoadState.Loaded
            },
            isEditing = local.working != null,
            isSaving = local.saving,
            isDeleted = local.deleted,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistUiState())

    init {
        refresh()
    }

    fun refresh() {
        local.update { it.copy(refreshing = true, refreshFailed = false) }
        viewModelScope.launch {
            val failed = runRemote { playlistRepository.refreshPlaylist(playlistId) }
            local.update { it.copy(refreshing = false, refreshFailed = failed) }
        }
    }

    fun startEditing() {
        val state = uiState.value
        if (!state.isEditable || state.isEditing) return
        local.update { it.copy(working = state.entries) }
    }

    fun cancelEditing() = local.update { it.copy(working = null) }

    /** Moves the entry at [from] to [to] in the working order. */
    fun move(from: Int, to: Int) = local.update { state ->
        val working = state.working ?: return@update state
        if (from !in working.indices || to !in working.indices || from == to) return@update state
        val list = working.toMutableList()
        list.add(to, list.removeAt(from))
        state.copy(working = list.toImmutableList())
    }

    /** Removes the entry with [key] from the working order. */
    fun removeEntry(key: String) = local.update { state ->
        val working = state.working ?: return@update state
        state.copy(working = working.filterNot { it.key == key }.toImmutableList())
    }

    /** Writes the working order to the server in one `updatePlaylist` call. A failure keeps edit mode. */
    fun save() {
        val working = local.value.working ?: return
        if (local.value.saving) return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val failed = runRemote { playlistRepository.replaceSongs(playlistId, working.map { it.song.id }) }
            local.update {
                if (failed) {
                    it.copy(saving = false, message = PlaylistMessage.SaveFailed)
                } else {
                    it.copy(saving = false, working = null)
                }
            }
        }
    }

    /** Removes one track outside edit mode, from its menu. [index] is the position in the saved order. */
    fun removeAt(index: Int) = write { playlistRepository.removeSongs(playlistId, listOf(index)) }

    fun rename(name: String) = write { playlistRepository.updatePlaylist(playlistId, name = name) }

    fun delete() {
        viewModelScope.launch {
            val failed = runRemote { playlistRepository.deletePlaylist(playlistId) }
            local.update { if (failed) it.copy(message = PlaylistMessage.Failed) else it.copy(deleted = true) }
        }
    }

    /** Pins the playlist for offline use; it then follows the server copy on every library sync. */
    fun download() {
        val playlist = uiState.value.playlist ?: return
        write { downloadRepository.pin(PinTarget(PinKind.Playlist, playlist.id, playlist.name, playlist.coverArtId)) }
    }

    fun removeDownload() {
        viewModelScope.launch { downloadRepository.unpin(PinKind.Playlist, playlistId) }
    }

    fun downloadSong(song: Song) = write {
        downloadRepository.pin(PinTarget(PinKind.Song, song.id, song.title, song.coverArtId))
    }

    fun toggleStar(song: Song) = write { starRatingRepository.setStarred(MediaKind.Song, song.id, !song.isStarred) }

    fun onMessageDismiss() = local.update { it.copy(message = null) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (runRemote(block)) local.update { it.copy(message = PlaylistMessage.Failed) }
        }
    }

    private data class LocalState(
        val refreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        /** The working order while editing; null outside edit mode. */
        val working: ImmutableList<PlaylistEntry>? = null,
        val saving: Boolean = false,
        val deleted: Boolean = false,
        val message: PlaylistMessage? = null,
    )

    companion object {
        /** The [com.quantumff.segue.feature.playlist.navigation.PlaylistRoute] argument name. */
        const val PLAYLIST_ID_KEY = "playlistId"
    }
}

private fun List<Song>.toEntries(): ImmutableList<PlaylistEntry> =
    mapIndexed { index, song -> PlaylistEntry(key = "$index:${song.id}", song = song) }.toImmutableList()
