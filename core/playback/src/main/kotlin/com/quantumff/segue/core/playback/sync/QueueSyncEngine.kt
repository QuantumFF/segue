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
 * - **Upload** is event-driven: the player reports [QueueEvent]s (track change, queue edit, pause,
 *   seek), which are debounced for [DEBOUNCE_MILLIS] and then saved in effective play order.
 *   Going to the background flushes at once. Nothing is uploaded while playback simply runs.
 * - **Snapshot**: the same events save the queue to Room with its shuffle, so a cold start can
 *   restore it exactly, offline. An empty queue is saved and uploaded only through
 *   [onQueueCleared], because an empty player is also what the service looks like before it
 *   restores the snapshot.
 * - **Download** happens in [checkRemote] (app start and foreground). A queue saved by another
 *   client that is newer than the last server change this device accounted for becomes
 *   [remoteOffer] for the banner, or, with auto adopt on, goes to [adoptions]. Only server
 *   timestamps are compared (see [RemoteQueuePolicy]), so a skewed device clock does not matter.
 */
@Singleton
class QueueSyncEngine @Inject constructor(
    private val remote: PlayQueueRemoteRepository,
    private val snapshots: QueueSnapshotRepository,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : QueueSyncSink {
    private val uploader = DebouncedQueueWriter(scope, DEBOUNCE_MILLIS, ::upload)
    private val snapshotWriter = DebouncedQueueWriter(scope, DEBOUNCE_MILLIS, ::saveSnapshot)

    private val offeredQueue = MutableStateFlow<RemotePlayQueue?>(null)
    private val offer = MutableStateFlow<RemoteQueueOffer?>(null)
    private val adoptionRequests = Channel<RemotePlayQueue>(Channel.BUFFERED)

    /** Seen server changes not yet confirmed in Room, so a check right after a dismiss sees it. */
    @Volatile
    private var seenInMemory: Long? = null

    /** A newer queue from another device, for the "Continue from <device>" banner. */
    val remoteOffer: StateFlow<RemoteQueueOffer?> = offer.asStateFlow()

    /** Remote queues to adopt without asking, when the user enabled that. */
    val adoptions: Flow<RemotePlayQueue> = adoptionRequests.receiveAsFlow()

    override fun onQueueEvent(@Suppress("UNUSED_PARAMETER") event: QueueEvent, state: QueueState) {
        if (state.songIds.isEmpty()) return
        snapshotWriter.submit(state)
        uploader.submit(state.inPlayOrder())
    }

    /** The user emptied a queue that had songs: clear the snapshot and the server's saved queue. */
    override fun onQueueCleared() {
        val cleared = QueueState(emptyList(), currentIndex = -1, positionMillis = 0)
        snapshotWriter.submit(cleared)
        uploader.submit(cleared)
    }

    /** The app left the foreground: save now. [state] is the current queue, or null to flush the latest event's. */
    fun onAppBackground(state: QueueState?) {
        if (state != null && state.songIds.isEmpty()) return
        snapshotWriter.flush(state)
        uploader.flush(state?.inPlayOrder())
    }

    /**
     * Local playback started: the banner goes (DESIGN.md §4.8), and the offered queue counts as
     * declined. This device's next upload replaces it on the server anyway.
     */
    override fun onLocalPlaybackStarted() {
        offeredQueue.value?.let(::clearOffer)
    }

    /** Fetches the server's queue and decides whether to offer or adopt it. Offline does nothing. */
    suspend fun checkRemote(isPlayingLocally: Boolean) {
        val prefs = preferences.preferences.first()
        if (!prefs.queueSyncEnabled) return
        val (queue, ownName, seen) = try {
            Triple(remote.fetch(), remote.deviceClientName(), maxOrNull(snapshots.seenRemoteChange(), seenInMemory))
        } catch (_: RemoteException) {
            return
        }
        val decision = RemoteQueuePolicy.decide(
            remote = queue,
            local = snapshots.snapshot.first(),
            ownClientName = ownName,
            autoAdopt = prefs.adoptRemoteQueueAutomatically,
            isPlayingLocally = isPlayingLocally,
            seen = seen,
        )
        when (decision) {
            RemoteQueueDecision.Offer -> {
                val offered = checkNotNull(queue)
                offeredQueue.value = offered
                offer.value = RemoteQueueOffer(
                    deviceName = offered.changedBy ?: UNKNOWN_DEVICE,
                    trackCount = offered.songs.size,
                    currentTitle = offered.songs.getOrNull(offered.currentIndex)?.title,
                    positionMillis = offered.positionMillis,
                    changedAt = offered.changedAt,
                )
            }

            RemoteQueueDecision.Adopt -> {
                val adopted = checkNotNull(queue)
                clearOffer(handled = adopted)
                adoptionRequests.send(adopted)
            }

            is RemoteQueueDecision.Ignore -> {
                // Our own save, or the queue we already have: the server state is accounted for.
                val accountedFor = decision.reason == RemoteQueueDecision.IgnoreReason.OwnSave ||
                    decision.reason == RemoteQueueDecision.IgnoreReason.SameQueue
                if (accountedFor) markSeen(RemoteQueuePolicy.seenKey(checkNotNull(queue)))
                offeredQueue.value = null
                offer.value = null
            }
        }
    }

    /** Takes the offered queue to adopt it; null if there is none. */
    fun acceptOffer(): RemotePlayQueue? = offeredQueue.value?.also(::clearOffer)

    /** Keeps the local queue. The dismissed save is remembered, so it stays dismissed after a restart too. */
    fun dismissOffer() {
        offeredQueue.value?.let(::clearOffer)
    }

    /** The player was restored from the local snapshot: that state is already saved everywhere. */
    fun markRestored(state: QueueState) {
        snapshotWriter.markWritten(state)
        uploader.markWritten(state.inPlayOrder())
    }

    /** The player adopted a remote queue: do not upload it straight back. The snapshot still saves it. */
    fun markAdopted(state: QueueState) {
        uploader.markWritten(state.inPlayOrder())
    }

    private fun clearOffer(handled: RemotePlayQueue) {
        offeredQueue.value = null
        offer.value = null
        val key = RemoteQueuePolicy.seenKey(handled)
        seenInMemory = maxOrNull(seenInMemory, key)
        scope.launch { markSeen(key) }
    }

    private suspend fun markSeen(changedAt: Long) {
        seenInMemory = maxOrNull(seenInMemory, changedAt)
        try {
            snapshots.markRemoteChangeSeen(changedAt)
        } catch (_: RemoteException) {
            // Signed out: there is no server to file it under.
        }
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
        snapshots.save(state.songIds, state.currentIndex, state.positionMillis, state.shuffleMode, state.playOrder)
        true
    } catch (_: RemoteException) {
        false // Signed out: there is no server to file the snapshot under.
    }

    private fun maxOrNull(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else maxOf(a, b)

    companion object {
        const val DEBOUNCE_MILLIS = 2_000L
        private const val UNKNOWN_DEVICE = "another device"
    }
}
