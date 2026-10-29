package com.quantumff.segue.feature.queue

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
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

/** A manual queue transfer that is running. */
enum class QueueTransfer { Upload, Download }

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
    /** The manual upload or download that is running, if any. Both buttons wait for it. */
    val transfer: QueueTransfer? = null,
    val downloadConfirmation: DownloadConfirmation? = null,
) {
    val isTransferring: Boolean get() = transfer != null
}

/**
 * The play queue (DESIGN.md §4.8): reorder, play next, remove with undo, upload or download the
 * server's saved queue by hand, and the "Continue from <device>" banner. Clear and Save as
 * playlist are Now Playing's.
 * Queues are shuffled only when music is added, never in place, so the queue order is the play
 * order. It reads the queue from [PlayerStateSource] and edits it through [PlaybackController].
 * Its messages go to the app's [Snackbars].
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val player: PlayerStateSource,
    private val playback: PlaybackController,
    private val snackbars: Snackbars,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())
    private val removals = QueueRemovals(snackbars)

    val uiState: StateFlow<QueueUiState> = combine(player.state, local) { state, local ->
        state.toUiState(local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QueueUiState())

    fun skipTo(track: QueueListItem.Track) = playback.skipTo(track.position)

    fun move(move: QueueMove) = playback.move(move.from, move.to)

    fun playNext(track: QueueListItem.Track) {
        val state = player.state.value
        QueueList.playNextMove(state.queue.size, state.currentQueueIndex, track.position)?.let(::move)
    }

    fun remove(track: QueueListItem.Track) {
        playback.remove(track.position)
        removals.removed(track.row.title, onUndo = playback::undoRemove)
    }

    fun toggleHistory() = local.update { it.copy(historyExpanded = !it.historyExpanded) }

    /** Saves the queue to the server now, whatever the queue sync setting. */
    fun uploadQueue() = runTransfer(QueueTransfer.Upload) {
        try {
            if (playback.uploadQueue()) UPLOADED else UPLOAD_FAILED
        } catch (_: RemoteException) {
            UPLOAD_FAILED
        }
    }

    /**
     * Fetches the server's saved queue. It replaces an empty queue at once; a queue with tracks
     * asks first, through [QueueUiState.downloadConfirmation].
     */
    fun downloadQueue() = runTransfer(QueueTransfer.Download) {
        val queue = try {
            playback.fetchRemoteQueue()
        } catch (_: RemoteException) {
            return@runTransfer DOWNLOAD_FAILED
        }
        when {
            queue == null -> NOTHING_TO_DOWNLOAD

            player.state.value.queue.isEmpty() -> adoptDownloaded(queue)

            else -> {
                local.update { it.copy(pendingDownload = queue) }
                null
            }
        }
    }

    fun confirmDownload() {
        val queue = local.value.pendingDownload ?: return
        local.update { it.copy(pendingDownload = null) }
        runTransfer(QueueTransfer.Download) { adoptDownloaded(queue) }
    }

    fun cancelDownload() = local.update { it.copy(pendingDownload = null) }

    private suspend fun adoptDownloaded(queue: RemotePlayQueue): SnackbarText =
        if (playback.adoptDownloadedQueue(queue)) {
            SnackbarText.plural(R.plurals.feature_queue_downloaded, queue.songs.size, queue.songs.size)
        } else {
            DOWNLOAD_FAILED
        }

    /** Runs one upload or download at a time, then shows its message, if any. */
    private fun runTransfer(transfer: QueueTransfer, block: suspend () -> SnackbarText?) {
        if (local.value.transfer != null) return
        local.update { it.copy(transfer = transfer) }
        viewModelScope.launch {
            val message = try {
                block()
            } finally {
                local.update { it.copy(transfer = null) }
            }
            if (message != null) snackbars.post(message)
        }
    }

    fun continueFromOtherDevice() = playback.adoptRemoteQueue()

    fun dismissOtherDevice() = playback.dismissRemoteQueue()

    private data class LocalState(
        val historyExpanded: Boolean = false,
        val transfer: QueueTransfer? = null,
        val pendingDownload: RemotePlayQueue? = null,
    )

    private fun PlayerUiState.toUiState(local: LocalState): QueueUiState {
        val items = QueueList.build(queue, currentQueueIndex, isPlaying, local.historyExpanded)
        return QueueUiState(
            items = items,
            currentKey = items.firstOrNull { it is QueueListItem.Track && it.row.isCurrent }?.key,
            trackCount = queue.size,
            remoteOffer = remoteQueue,
            isLoading = false,
            transfer = local.transfer,
            downloadConfirmation = local.pendingDownload?.let { DownloadConfirmation(it.songs.size, it.deviceName) },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val UPLOADED = SnackbarText.of(R.string.feature_queue_uploaded)
        val UPLOAD_FAILED = SnackbarText.of(R.string.feature_queue_upload_failed)
        val NOTHING_TO_DOWNLOAD = SnackbarText.of(R.string.feature_queue_nothing_to_download)
        val DOWNLOAD_FAILED = SnackbarText.of(R.string.feature_queue_download_failed)
    }
}
