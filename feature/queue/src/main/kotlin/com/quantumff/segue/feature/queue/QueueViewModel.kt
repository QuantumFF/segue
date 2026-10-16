package com.quantumff.segue.feature.queue

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A one-off message for the snackbar. */
@Immutable
sealed interface QueueMessage {
    /** A track was removed; the snackbar offers Undo. */
    data class Removed(val title: String) : QueueMessage

    data class SavedAsPlaylist(val name: String) : QueueMessage

    data object SaveFailed : QueueMessage
}

@Immutable
data class QueueUiState(
    val items: ImmutableList<QueueListItem> = persistentListOf(),
    /** The current track's row key, for highlighting and scrolling to it. */
    val currentKey: String? = null,
    val trackCount: Int = 0,
    /** A newer queue saved by another device: the "Continue from" banner. */
    val remoteOffer: RemoteQueueOffer? = null,
    /** True until the player state first arrives, so the empty state does not flash. */
    val isLoading: Boolean = true,
    val message: QueueMessage? = null,
)

/**
 * The play queue (DESIGN.md §4.8): reorder, play next, remove with undo, clear, save as playlist,
 * and the "Continue from <device>" banner. Queues are shuffled only when music is added, never in
 * place, so the queue order is the play order.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<QueueUiState> = combine(playback.eventState, local) { player, local ->
        player.toUiState(local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QueueUiState())

    fun skipTo(track: QueueListItem.Track) = playback.skipTo(track.position)

    fun move(move: QueueMove) = playback.move(move.from, move.to)

    fun playNext(track: QueueListItem.Track) {
        val player = playback.eventState.value
        QueueList.playNextMove(player.queue.size, player.currentQueueIndex, track.position)?.let(::move)
    }

    fun remove(track: QueueListItem.Track) {
        playback.remove(track.position)
        local.update { it.copy(message = QueueMessage.Removed(track.row.title)) }
    }

    fun undoRemove() = playback.undoRemove()

    fun toggleHistory() = local.update { it.copy(historyExpanded = !it.historyExpanded) }

    fun clearQueue() = playback.clearQueue()

    /** Saves the queue, in play order, as a new playlist called [name]. */
    fun saveAsPlaylist(name: String) {
        val songIds = playback.eventState.value.queue.map { it.songId }
        if (name.isBlank() || songIds.isEmpty()) return
        viewModelScope.launch {
            val message = try {
                playlists.createPlaylist(name.trim(), songIds)
                QueueMessage.SavedAsPlaylist(name.trim())
            } catch (_: RemoteException) {
                QueueMessage.SaveFailed
            }
            local.update { it.copy(message = message) }
        }
    }

    fun continueFromOtherDevice() = playback.adoptRemoteQueue()

    fun dismissOtherDevice() = playback.dismissRemoteQueue()

    fun onMessageShown() = local.update { it.copy(message = null) }

    private data class LocalState(val historyExpanded: Boolean = false, val message: QueueMessage? = null)

    private fun PlayerUiState.toUiState(local: LocalState): QueueUiState {
        val items = QueueList.build(queue, currentQueueIndex, isPlaying, local.historyExpanded)
        return QueueUiState(
            items = items,
            currentKey = items.firstOrNull { it is QueueListItem.Track && it.row.isCurrent }?.key,
            trackCount = queue.size,
            remoteOffer = remoteQueue,
            isLoading = false,
            message = local.message,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
