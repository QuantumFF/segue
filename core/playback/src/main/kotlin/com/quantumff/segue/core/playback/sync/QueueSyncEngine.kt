package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RemoteQueueOffer
import java.util.concurrent.atomic.AtomicReference
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
 * A remote queue to adopt, and [undo], the local queue an automatic adoption replaces, for the
 * "Continued from <device> · Undo" snackbar. Null for "Continue from <device>", and when there was
 * no local queue.
 */
data class RemoteAdoption(val queue: RemotePlayQueue, val undo: QueueSnapshot?)

/**
 * Keeps the play queue in sync with the server and with the local Room snapshot. See
 * docs/PLAN.md, "Server queue sync".
 *
 * - **Upload** is event-driven: the player reports [QueueEvent]s (track change, queue edit, pause,
 *   seek), which are debounced for [DEBOUNCE_MILLIS] and then saved in queue order.
 *   Going to the background, or the service stopping, [flush]es at once. Nothing is uploaded
 *   while playback simply runs.
 * - **Snapshot**: the same events save the queue to Room, so a cold start can
 *   restore it exactly, offline. An empty queue is saved and uploaded only through
 *   [onQueueCleared], because an empty player is also what the service looks like before it
 *   restores the snapshot.
 * - **Download** happens in [checkRemote] (app start and foreground). A queue saved by another
 *   client that is newer than the last server change this device accounted for becomes
 *   [remoteOffer] for the banner, or, with auto adopt on, goes to [adoptions] with the local
 *   queue it replaces, which [takeAdoptionUndo] hands back once for Undo. Only server
 *   timestamps are compared (see [RemoteQueuePolicy]), so a skewed device clock does not matter.
 * - **Adoption**: an accepted offer ([acceptOffer]) or an automatic adoption is staged for the
 *   player, which takes it with [takeAdoption] ([SavedQueueLoader]). Whatever the player loads
 *   from a saved queue it reports through [markRestored] or [markAdopted].
 * - **By hand**, from the queue screen: [uploadNow] and [fetchNow] skip the debounce, the checks
 *   and the queue sync setting, because the user asked for exactly that. A fetched queue is
 *   staged for the player like an accepted offer ([stageDownload]).
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
    private val adoptionRequests = Channel<RemoteAdoption>(Channel.BUFFERED)

    /** The remote queue to put in the player next: the accepted offer or the latest automatic adoption. */
    private val pendingAdoption = AtomicReference<RemoteAdoption?>(null)

    /** The local queue an automatic adoption replaced, until undone or played over. */
    private val adoptionUndo = AtomicReference<QueueSnapshot?>(null)

    /** Seen server changes not yet confirmed in Room, so a check right after a dismiss sees it. */
    @Volatile
    private var seenInMemory: Long? = null

    /** A newer queue from another device, for the "Continue from <device>" banner. */
    val remoteOffer: StateFlow<RemoteQueueOffer?> = offer.asStateFlow()

    /**
     * Remote queues adopted without asking, when the user enabled that. Each is staged for the
     * player ([takeAdoption]) before it is sent here.
     */
    val adoptions: Flow<RemoteAdoption> = adoptionRequests.receiveAsFlow()

    override fun onQueueEvent(@Suppress("UNUSED_PARAMETER") event: QueueEvent, state: QueueState) {
        if (state.songIds.isEmpty()) return
        snapshotWriter.submit(state)
        uploader.submit(state)
    }

    /** The user emptied a queue that had songs: clear the snapshot and the server's saved queue. */
    override fun onQueueCleared() {
        val cleared = QueueState(emptyList(), currentIndex = -1, positionMillis = 0)
        snapshotWriter.submit(cleared)
        uploader.submit(cleared)
    }

    /**
     * Saves and uploads now instead of after the quiet period: the app left the foreground, or the
     * service is going away. [state] is the current queue, or null to flush the latest event's.
     */
    fun flush(state: QueueState?) {
        if (state != null && state.songIds.isEmpty()) return
        snapshotWriter.flush(state)
        uploader.flush(state)
    }

    /**
     * Local playback started: the banner goes (DESIGN.md §4.8), and the offered queue counts as
     * declined. This device's next upload replaces it on the server anyway.
     */
    override fun onLocalPlaybackStarted() {
        offeredQueue.value?.let(::clearOffer)
        // Playing on means keeping the adopted queue: Undo no longer applies.
        adoptionUndo.set(null)
    }

    /**
     * The network came back (or offline mode was turned off): upload the queue now if a save
     * failed while offline. [state] is the current queue, or null for the latest event's.
     */
    fun onNetworkRestored(state: QueueState?) {
        if (state != null && state.songIds.isEmpty()) return
        uploader.flush(state)
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
                    deviceName = offered.deviceName,
                    trackCount = offered.songs.size,
                    currentTitle = offered.songs.getOrNull(offered.currentIndex)?.title,
                    positionMillis = offered.positionMillis,
                    changedAt = offered.changedAt,
                )
            }

            RemoteQueueDecision.Adopt -> {
                val adopted = checkNotNull(queue)
                clearOffer(handled = adopted)
                // The snapshot is the local queue: it is flushed when the app leaves the foreground.
                val previous = snapshots.snapshot.first()?.takeIf { it.songIds.isNotEmpty() }
                adoptionUndo.set(previous)
                val adoption = RemoteAdoption(adopted, previous)
                pendingAdoption.set(adoption)
                adoptionRequests.send(adoption)
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

    /**
     * "Continue from <device>": stages the offered queue, the one the banner shows now, for the
     * player ([takeAdoption]), and returns it. Null if there is none.
     */
    fun acceptOffer(): RemoteAdoption? {
        val queue = offeredQueue.value ?: return null
        clearOffer(queue)
        return RemoteAdoption(queue, undo = null).also(pendingAdoption::set)
    }

    /** The remote queue staged for the player to adopt, once; null if none. */
    internal fun takeAdoption(): RemoteAdoption? = pendingAdoption.getAndSet(null)

    /**
     * [adoption] never reached the player (asking the service failed): it is no longer staged, and
     * an automatic one has nothing to undo since nothing was replaced. Once the player took it
     * ([takeAdoption]) this does nothing; the player settles undo itself.
     */
    internal fun abandonAdoption(adoption: RemoteAdoption) {
        if (!pendingAdoption.compareAndSet(adoption, null)) return
        if (adoption.undo != null) adoptionUndo.compareAndSet(adoption.undo, null)
    }

    /** The local queue the last automatic adoption replaced, to restore it; null if none. Only once. */
    internal fun takeAdoptionUndo(): QueueSnapshot? = adoptionUndo.getAndSet(null)

    /** Keeps the local queue. The dismissed save is remembered, so it stays dismissed after a restart too. */
    fun dismissOffer() {
        offeredQueue.value?.let(::clearOffer)
    }

    /**
     * "Upload queue": saves [state] to the server now, whatever the queue sync setting. The
     * server then holds this device's queue, so an offered queue counts as declined. Throws
     * RemoteException.
     */
    suspend fun uploadNow(state: QueueState) {
        remote.save(state.songIds, state.currentIndex, state.positionMillis)
        uploader.markWritten(state)
        offeredQueue.value?.let(::clearOffer)
    }

    /**
     * "Download queue": the queue saved on the server, whoever saved it and whatever the queue
     * sync setting; null when nothing is saved. Once adopted, report it to [markDownloaded].
     * Throws RemoteException.
     */
    suspend fun fetchNow(): RemotePlayQueue? = remote.fetch()?.takeIf { it.songs.isNotEmpty() }

    /** Stages [queue] from [fetchNow] for the player ([takeAdoption]), with nothing to undo. */
    internal fun stageDownload(queue: RemotePlayQueue): RemoteAdoption =
        RemoteAdoption(queue, undo = null).also(pendingAdoption::set)

    /** [queue] from [fetchNow] was adopted: it is accounted for, so it is not offered again. */
    fun markDownloaded(queue: RemotePlayQueue) = clearOffer(handled = queue)

    /** The player was restored from the local snapshot: that state is already saved everywhere. */
    internal fun markRestored(state: QueueState) {
        snapshotWriter.markWritten(state)
        uploader.markWritten(state)
    }

    /** The player adopted a remote queue: do not upload it straight back. The snapshot still saves it. */
    internal fun markAdopted(state: QueueState) {
        uploader.markWritten(state)
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
        snapshots.save(state.songIds, state.currentIndex, state.positionMillis)
        true
    } catch (_: RemoteException) {
        false // Signed out: there is no server to file the snapshot under.
    }

    private fun maxOrNull(a: Long?, b: Long?): Long? = listOfNotNull(a, b).maxOrNull()

    companion object {
        const val DEBOUNCE_MILLIS = 2_000L
        private const val UNKNOWN_DEVICE = "another device"

        /** The client that saved the queue, for "Continue from <device>". */
        val RemotePlayQueue.deviceName: String get() = changedBy ?: UNKNOWN_DEVICE
    }
}
