package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RemoteQueueOffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Keeps the play queue in sync with the server and with the local Room snapshot. See
 * docs/PLAN.md, "Server queue sync".
 *
 * - **Upload** is event-driven: the player reports [QueueEvent]s, which are debounced for
 *   [DEBOUNCE_MILLIS] and then saved in effective play order. Going to the background flushes at
 *   once. Nothing is uploaded while playback simply runs.
 * - **Snapshot**: the same events save the queue to Room, so a cold start can restore it offline.
 *   An empty queue is saved and uploaded only through [onQueueCleared], because an empty player is
 *   also what the service looks like before it restores the snapshot.
 * - **Download** happens in [checkRemote] (app start and foreground). A newer queue from another
 *   client becomes [remoteOffer] for the banner, or, with auto adopt on, goes to [adoptions].
 */
@Singleton
class QueueSyncEngine @Inject constructor(
    private val remote: PlayQueueRemoteRepository,
    private val snapshots: QueueSnapshotRepository,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val uploader = DebouncedQueueWriter(scope, DEBOUNCE_MILLIS, ::upload)
    private val snapshotWriter = DebouncedQueueWriter(scope, DEBOUNCE_MILLIS, ::saveSnapshot)

    private val offeredQueue = MutableStateFlow<RemotePlayQueue?>(null)
    private val offer = MutableStateFlow<RemoteQueueOffer?>(null)
    private val adoptionRequests = Channel<RemotePlayQueue>(Channel.BUFFERED)

    @Volatile
    private var handledChangedAt: Long? = null

    /** A newer queue from another device, for the "Continue from <device>" banner. */
    val remoteOffer: StateFlow<RemoteQueueOffer?> = offer.asStateFlow()

    /** Remote queues to adopt without asking, when the user enabled that. */
    val adoptions: Flow<RemotePlayQueue> = adoptionRequests.receiveAsFlow()

    fun onQueueEvent(@Suppress("UNUSED_PARAMETER") event: QueueEvent, state: QueueState) {
        if (state.songIds.isEmpty()) return
        snapshotWriter.submit(state)
        uploader.submit(state)
    }

    /** The user emptied a queue that had songs: clear the snapshot and the server's saved queue. */
    fun onQueueCleared() {
        val cleared = QueueState(emptyList(), currentIndex = -1, positionMillis = 0)
        snapshotWriter.submit(cleared)
        uploader.submit(cleared)
    }

    /** The app left the foreground: save now. [state] is the current queue, or null to flush the latest event's. */
    fun onAppBackground(state: QueueState?) {
        if (state != null && state.songIds.isEmpty()) return
        snapshotWriter.flush(state)
        uploader.flush(state)
    }

    /** Fetches the server's queue and decides whether to offer or adopt it. Offline does nothing. */
    suspend fun checkRemote(isPlayingLocally: Boolean) {
        val prefs = preferences.preferences.first()
        if (!prefs.queueSyncEnabled) return
        val (queue, ownName) = try {
            remote.fetch() to remote.deviceClientName()
        } catch (_: RemoteException) {
            return
        }
        val decision = RemoteQueuePolicy.decide(
            remote = queue,
            local = snapshots.snapshot.first(),
            ownClientName = ownName,
            autoAdopt = prefs.adoptRemoteQueueAutomatically,
            isPlayingLocally = isPlayingLocally,
            handledChangedAt = handledChangedAt,
        )
        when (decision) {
            RemoteQueueDecision.Offer -> {
                val offered = checkNotNull(queue)
                offeredQueue.value = offered
                offer.value = RemoteQueueOffer(
                    deviceName = offered.changedBy ?: UNKNOWN_DEVICE,
                    trackCount = offered.songs.size,
                    currentTitle = offered.songs.getOrNull(offered.currentIndex)?.title,
                )
            }

            RemoteQueueDecision.Adopt -> {
                val adopted = checkNotNull(queue)
                clearOffer(handled = adopted)
                adoptionRequests.send(adopted)
            }

            is RemoteQueueDecision.Ignore -> if (decision.reason != RemoteQueueDecision.IgnoreReason.AlreadyHandled) {
                offeredQueue.value = null
                offer.value = null
            }
        }
    }

    /** Takes the offered queue to adopt it; null if there is none. */
    fun acceptOffer(): RemotePlayQueue? = offeredQueue.value?.also { clearOffer(handled = it) }

    /**
     * Keeps the local queue. Its snapshot is saved again so it counts as newer than the dismissed
     * remote queue, which then stays dismissed after a restart too.
     */
    fun dismissOffer() {
        offeredQueue.value?.let { clearOffer(handled = it) } ?: return
        scope.launch {
            val local = snapshots.snapshot.first() ?: return@launch
            saveSnapshot(QueueState(local.songIds, local.currentIndex, local.positionMillis))
        }
    }

    /** The player was restored from the local snapshot: that state is already saved everywhere. */
    fun markRestored(state: QueueState) {
        snapshotWriter.markWritten(state)
        uploader.markWritten(state)
    }

    /** The player adopted a remote queue: do not upload it straight back. The snapshot still saves it. */
    fun markAdopted(state: QueueState) {
        uploader.markWritten(state)
    }

    private fun clearOffer(handled: RemotePlayQueue) {
        handledChangedAt = RemoteQueuePolicy.handledKey(handled)
        offeredQueue.value = null
        offer.value = null
    }

    private suspend fun upload(state: QueueState): Boolean {
        if (!preferences.preferences.first().queueSyncEnabled) return false
        return try {
            remote.save(state.songIds, state.currentIndex, state.positionMillis)
            true
        } catch (_: RemoteException) {
            false
        }
    }

    private suspend fun saveSnapshot(state: QueueState): Boolean = try {
        snapshots.save(state.songIds, state.currentIndex, state.positionMillis)
        true
    } catch (_: RemoteException) {
        false // Signed out: there is no server to file the snapshot under.
    }

    companion object {
        const val DEBOUNCE_MILLIS = 2_000L
        private const val UNKNOWN_DEVICE = "another device"
    }
}
