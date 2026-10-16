package com.quantumff.segue.feature.queue

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.playback.PlaybackController
import com.quantumff.segue.core.playback.sync.QueueSyncEngine.Companion.deviceName
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

    data object Uploaded : QueueMessage

    data object UploadFailed : QueueMessage

    data class Downloaded(val trackCount: Int) : QueueMessage

    /** The server has no saved queue. */
    data object NothingToDownload : QueueMessage

    data object DownloadFailed : QueueMessage
}

/** "Download queue" over a queue with tracks: replace it with [trackCount] tracks saved by [deviceName]? */
@Immutable
data class DownloadConfirmation(val trackCount: Int, val deviceName: String)

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
    /** A manual upload or download is running: the menu's sync items wait for it. */
    val isTransferring: Boolean = false,
    val downloadConfirmation: DownloadConfirmation? = null,
    val message: QueueMessage? = null,
)

/**
 * The play queue (DESIGN.md §4.8): reorder, play next, remove with undo, upload or download the
 * server's saved queue by hand, and the "Continue from <device>" banner. Clear and Save as
 * playlist are Now Playing's.
 * Queues are shuffled only when music is added, never in place, so the queue order is the play
 * order.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(private val playback: PlaybackController) : ViewModel() {
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

    /** Saves the queue to the server now, whatever the queue sync setting. */
    fun uploadQueue() = runTransfer {
        try {
            if (playback.uploadQueue()) QueueMessage.Uploaded else QueueMessage.UploadFailed
        } catch (_: RemoteException) {
            QueueMessage.UploadFailed
        }
    }

    /**
     * Fetches the server's saved queue. It replaces an empty queue at once; a queue with tracks
     * asks first, through [QueueUiState.downloadConfirmation].
     */
    fun downloadQueue() = runTransfer {
        val queue = try {
            playback.fetchRemoteQueue()
        } catch (_: RemoteException) {
            return@runTransfer QueueMessage.DownloadFailed
        }
        when {
            queue == null -> QueueMessage.NothingToDownload

            playback.eventState.value.queue.isEmpty() -> adoptDownloaded(queue)

            else -> {
                local.update { it.copy(pendingDownload = queue) }
                null
            }
        }
    }

    fun confirmDownload() {
        val queue = local.value.pendingDownload ?: return
        local.update { it.copy(pendingDownload = null) }
        runTransfer { adoptDownloaded(queue) }
    }

    fun cancelDownload() = local.update { it.copy(pendingDownload = null) }

    private suspend fun adoptDownloaded(queue: RemotePlayQueue): QueueMessage =
        if (playback.adoptDownloadedQueue(queue)) {
            QueueMessage.Downloaded(queue.songs.size)
        } else {
            QueueMessage.DownloadFailed
        }

    /** Runs one upload or download at a time, then shows its message, if any. */
    private fun runTransfer(block: suspend () -> QueueMessage?) {
        if (local.value.isTransferring) return
        local.update { it.copy(isTransferring = true) }
        viewModelScope.launch {
            val message = try {
                block()
            } finally {
                local.update { it.copy(isTransferring = false) }
            }
            if (message != null) local.update { it.copy(message = message) }
        }
    }

    fun continueFromOtherDevice() = playback.adoptRemoteQueue()

    fun dismissOtherDevice() = playback.dismissRemoteQueue()

    fun onMessageShown() = local.update { it.copy(message = null) }

    private data class LocalState(
        val historyExpanded: Boolean = false,
        val isTransferring: Boolean = false,
        val pendingDownload: RemotePlayQueue? = null,
        val message: QueueMessage? = null,
    )

    private fun PlayerUiState.toUiState(local: LocalState): QueueUiState {
        val items = QueueList.build(queue, currentQueueIndex, isPlaying, local.historyExpanded)
        return QueueUiState(
            items = items,
            currentKey = items.firstOrNull { it is QueueListItem.Track && it.row.isCurrent }?.key,
            trackCount = queue.size,
            remoteOffer = remoteQueue,
            isLoading = false,
            isTransferring = local.isTransferring,
            downloadConfirmation = local.pendingDownload?.let { DownloadConfirmation(it.songs.size, it.deviceName) },
            message = local.message,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
