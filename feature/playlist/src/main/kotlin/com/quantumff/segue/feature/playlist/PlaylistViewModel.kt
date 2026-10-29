package com.quantumff.segue.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.data.download.pinAndConfirm
import com.quantumff.segue.core.data.download.unpinWithUndo
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.data.play.PlayerTransport
import com.quantumff.segue.core.data.play.heroPlay
import com.quantumff.segue.core.data.play.pagePlayback
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.designsystem.model.toTrackRowState
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.HeroPlayback
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongAvailability
import com.quantumff.segue.core.model.SourcePage
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

/**
 * A playlist entry: its [song], and which [copy] of that song it is (from 0), for playlists that
 * list a song more than once. Its [key] stays unique, and stable while reordering.
 */
data class PlaylistEntry(val song: Song, val copy: Int = 0) {
    val key: String get() = "$copy:${song.id}"
}

enum class LoadState {
    Loading,
    Loaded,

    /** The refresh failed: cached rows (if any) stay; with none, the screen shows an error. */
    Failed,
}

data class PlaylistUiState(
    val playlist: Playlist? = null,
    /** The saved order, or the working order while [isEditing]. */
    val entries: ImmutableList<PlaylistEntry> = persistentListOf(),
    val loadState: LoadState = LoadState.Loading,
    /** The refresh could not reach the server: cached rows show with the offline chip. */
    val isOffline: Boolean = false,
    val isEditing: Boolean = false,
    /** The working order differs from the saved one. */
    val hasChanges: Boolean = false,
    /** Back was pressed with unsaved changes: "Discard changes?" shows. */
    val isDiscardPromptShown: Boolean = false,
    val isSaving: Boolean = false,
    /** Set once the playlist was deleted from this screen; the screen then navigates back. */
    val isDeleted: Boolean = false,
    val message: PlaylistMessage? = null,
    /** The playlist's pin status; null when it is not pinned for offline. */
    val download: DownloadStatus? = null,
    /** Row badges; offline, rows of songs that are not downloaded are dimmed and do not play. */
    val availability: SongAvailability = SongAvailability(),
    /** The song the player is on; its rows are marked. */
    val currentSong: CurrentSong? = null,
    /** Whether this playlist is the queue source and plays: the hero's Play then shows Pause. */
    val heroPlayback: HeroPlayback = HeroPlayback.NotSource,
) {
    /** Only the owner's playlists can be edited, renamed or deleted. */
    val isEditable: Boolean get() = playlist != null && !playlist.readonly

    val songs: List<Song> get() = entries.map { it.song }

    /** Save is enabled only when there is a change to save. */
    val canSave: Boolean get() = hasChanges && !isSaving

    /** [entry]'s track row: its download badge, whether it is available, and whether it is playing. */
    fun trackRow(entry: PlaylistEntry): TrackRowState =
        entry.song.toTrackRowState(availability, currentSong, rowKey = entry.key)
}

/**
 * Playlist detail (DESIGN.md §4.5): Room-backed rows, refreshed on entry, with an edit mode for
 * reorder and removal.
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    playerState: PlayerStateSource,
    private val transport: PlayerTransport,
    private val mediaActions: MediaActionHandler,
    private val removals: PlaylistRemovals,
    private val snackbars: Snackbars,
) : ViewModel() {
    private val playlistId: String = checkNotNull(savedStateHandle[PLAYLIST_ID_KEY]) { "Missing $PLAYLIST_ID_KEY" }
    private val local = MutableStateFlow(LocalState())

    /** The current song, and whether this playlist is the queue source and plays. */
    private val player = playerState.pagePlayback(SourcePage.Playlist(playlistId))

    val uiState: StateFlow<PlaylistUiState> = combine(
        playlistRepository.playlist(playlistId),
        removals.entries(playlistId),
        local,
        combine(downloadRepository.pinStatus(PinKind.Playlist, playlistId), offlineView.songAvailability, ::Pair),
        player,
    ) { playlist, saved, local, (download, availability), player ->
        PlaylistUiState(
            download = download,
            availability = availability,
            currentSong = player.currentSong,
            heroPlayback = player.hero,
            playlist = playlist,
            entries = local.working ?: saved,
            loadState = when {
                local.refreshFailed -> LoadState.Failed
                playlist == null && local.refreshing -> LoadState.Loading
                else -> LoadState.Loaded
            },
            isOffline = local.refreshOffline,
            isEditing = local.working != null,
            hasChanges = local.working != null && local.working.map { it.song.id } != saved.map { it.song.id },
            isDiscardPromptShown = local.discardPrompt,
            isSaving = local.saving,
            isDeleted = local.deleted,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistUiState())

    init {
        refresh()
    }

    fun refresh() {
        local.update { it.copy(refreshing = true, refreshFailed = false, refreshOffline = false) }
        viewModelScope.launch {
            val outcome = remoteOutcome { playlistRepository.refreshPlaylist(playlistId) }
            local.update {
                it.copy(
                    refreshing = false,
                    refreshFailed = outcome.isFailure,
                    refreshOffline = outcome.showsOfflineChip,
                )
            }
        }
    }

    /** Enters edit mode. A removal's Undo goes away: its write must land before the edit is saved. */
    fun startEditing() {
        val state = uiState.value
        if (!state.isEditable || state.isEditing) return
        removals.flush(playlistId)
        local.update { it.copy(working = state.entries) }
    }

    /** Leaves edit mode, dropping the working order; also the discard prompt's Discard. */
    fun cancelEditing() = local.update { it.copy(working = null, discardPrompt = false) }

    /** Back while editing: asks before discarding changes, and otherwise just leaves edit mode. */
    fun onEditBack() {
        if (uiState.value.hasChanges) local.update { it.copy(discardPrompt = true) } else cancelEditing()
    }

    /** The discard prompt's Keep editing. */
    fun keepEditing() = local.update { it.copy(discardPrompt = false) }

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
            val failed = remoteOutcome {
                removals.afterRemovals(playlistId) {
                    playlistRepository.replaceSongs(playlistId, working.map { it.song.id })
                }
            }.isFailure
            local.update {
                if (failed) {
                    it.copy(saving = false, message = PlaylistMessage.SaveFailed)
                } else {
                    it.copy(saving = false, working = null)
                }
            }
        }
    }

    /**
     * Removes one track outside edit mode, from its menu: [index] is its row. The row hides at once;
     * [PlaylistRemovals] writes the removal once its Undo is gone.
     */
    fun removeAt(index: Int) {
        val state = uiState.value
        if (state.isEditing) return
        removals.remove(playlistId, state.entries.getOrNull(index) ?: return)
    }

    fun rename(name: String) = write { playlistRepository.updatePlaylist(playlistId, name = name) }

    /**
     * Deletes the playlist, after its confirm dialog. "Deleted <name>" goes to the app's snackbars,
     * so it still shows once this screen has closed.
     */
    fun delete() {
        val name = uiState.value.playlist?.name ?: return
        viewModelScope.launch {
            val failed = remoteOutcome {
                removals.afterRemovals(playlistId) { playlistRepository.deletePlaylist(playlistId) }
            }.isFailure
            if (!failed) snackbars.post(SnackbarText.of(R.string.feature_playlist_deleted, name))
            local.update { if (failed) it.copy(message = PlaylistMessage.Failed) else it.copy(deleted = true) }
        }
    }

    /**
     * Pins the playlist for offline use, confirmed with its name; it then follows the server copy on
     * every library sync.
     */
    fun download() {
        val playlist = uiState.value.playlist ?: return
        write { downloadRepository.pinAndConfirm(PinTarget.of(playlist), snackbars) }
    }

    /** Unpins the playlist with Undo; files no other pin needs are deleted once the snackbar has gone. */
    fun removeDownload() {
        val playlist = uiState.value.playlist ?: return
        viewModelScope.launch {
            downloadRepository.unpinWithUndo(PinKind.Playlist, playlistId, playlist.name, snackbars)
        }
    }

    /** Plays the playlist as shown from [startIndex]; [shuffle] shuffles its tracks. */
    fun play(startIndex: Int = 0, shuffle: Boolean = false) {
        val songs = uiState.value.songs.ifEmpty { return }
        val target = PlayTarget.Songs(songs, startIndex)
        val source = queueSource()
        viewModelScope.launch { playIntents.play(target, shuffle = shuffle, source = source) }
    }

    /**
     * The hero's Play, or the collapsed top bar's: pauses this playlist while it is the queue source
     * and plays, resumes it while it is the paused queue source, and otherwise plays it from the start.
     */
    fun onHeroPlay() {
        transport.heroPlay(uiState.value.heroPlayback) { play() }
    }

    /** The playlist's "Shuffle albums": its tracks grouped by album. */
    fun shuffleAlbums() {
        val request = AlbumPlayRequest(AlbumSource.Playlist(playlistId), AlbumOrder.Shuffled)
        val source = queueSource()
        viewModelScope.launch { playIntents.playAlbums(request, source) }
    }

    /** The playlist, as the queue source its plays record; unknown until it has loaded. */
    private fun queueSource(): QueueSource =
        uiState.value.playlist?.let { QueueSource.Playlist(it.id, it.name) } ?: QueueSource.Unknown

    /** A track's ⋮ menu ([MediaActionHandler]). Remove from playlist comes back to [host]. */
    fun onSongAction(song: Song, action: MediaAction, host: MediaActionHost) {
        viewModelScope.launch {
            if (mediaActions.song(song, action, host) != null) {
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

    private data class LocalState(
        val refreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        val refreshOffline: Boolean = false,
        /** The working order while editing; null outside edit mode. */
        val working: ImmutableList<PlaylistEntry>? = null,
        val discardPrompt: Boolean = false,
        val saving: Boolean = false,
        val deleted: Boolean = false,
        val message: PlaylistMessage? = null,
    )

    companion object {
        /** The [com.quantumff.segue.feature.playlist.navigation.PlaylistRoute] argument name. */
        const val PLAYLIST_ID_KEY = "playlistId"
    }
}
