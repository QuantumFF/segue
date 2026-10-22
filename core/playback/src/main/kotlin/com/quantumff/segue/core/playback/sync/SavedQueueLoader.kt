package com.quantumff.segue.core.playback.sync

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.QueueEditor
import com.quantumff.segue.core.playback.service.SeguePlayer
import com.quantumff.segue.core.playback.sync.QueueSyncEngine.Companion.deviceName
import kotlinx.coroutines.flow.first

/**
 * Puts saved queues into the player, in the playback service. Every way a saved queue reaches the
 * player goes through here, and each tells queue sync what it loaded, so a queue that is already
 * saved is not saved or uploaded straight back:
 *
 * - **Restore** ([restore] at service start, [resumption] for the system's "resume playback"): this
 *   device's saved queue, with its queue source, from Room only, so it works offline. Songs no
 *   longer in the library are dropped ([QueueRestore]). It is already saved everywhere.
 * - **Adoption** ([adopt]): the remote queue [QueueSyncEngine] staged, accepted from the "Continue
 *   from <device>" offer or adopted automatically, paused at its saved position. Its source is
 *   that device ([QueueSource.Adopted]). Offline only its
 *   downloaded songs ([QueueEditor.playable]). It is already on the server; this device's saved
 *   queue follows it. If the player cannot take it, there is nothing to undo.
 * - **Undo** ([undoAdoption]): the local queue the last automatic adoption replaced, paused, with
 *   its source. That is a queue edit like any other: saved and uploaded.
 *
 * Call on the player's thread.
 */
class SavedQueueLoader(
    private val player: SeguePlayer,
    private val queueSync: QueueSyncEngine,
    private val snapshots: QueueSnapshotRepository,
    private val library: LibraryRepository,
    /** Narrows an adopted queue to what can play now. */
    private val editor: QueueEditor,
) {
    /** At service start: this device's saved queue, paused, unless something was queued meanwhile. */
    suspend fun restore() {
        val restored = loadSaved() ?: return
        if (player.mediaItemCount > 0) return // Something was queued while we read the saved queue.
        player.restore(restored.items, restored.state)
        queueSync.markRestored(player.queueState())
    }

    /**
     * The system's "resume playback" with nothing queued (a Bluetooth play button): this device's
     * saved queue, for the session to set. Null if there is none.
     */
    suspend fun resumption(): MediaItemsWithStartPosition? {
        val restored = loadSaved() ?: return null
        // The session sets the items after this returns, so name their source and mark the state
        // the player will then report.
        player.setQueueSource(restored.state.source)
        queueSync.markRestored(restored.state)
        return MediaItemsWithStartPosition(restored.items, restored.state.currentIndex, restored.state.positionMillis)
    }

    /**
     * Replaces the queue with the remote queue queue sync staged ([QueueSyncEngine.takeAdoption]),
     * paused. False if none was staged or none of its songs can play; the queue is then left alone.
     */
    suspend fun adopt(): Boolean {
        val adoption = queueSync.takeAdoption() ?: return false
        val queue = adoption.queue
        val items = queue.songs.map { it.toMediaItem() }
        val playable = items.takeIf { it.isNotEmpty() }?.let {
            editor.playable(it, queue.currentIndex.coerceIn(it.indices), queue.positionMillis)
        }
        if (playable == null) {
            // Nothing was replaced: an automatic adoption has nothing to undo.
            if (adoption.undo != null) queueSync.takeAdoptionUndo()
            return false
        }
        player.pause()
        player.setQueueSource(QueueSource.Adopted(queue.deviceName))
        player.setMediaItems(playable.mediaItems, playable.startIndex, playable.startPositionMs)
        queueSync.markAdopted(player.queueState())
        return true
    }

    /** The snackbar's Undo: the local queue the last automatic adoption replaced comes back, paused. */
    suspend fun undoAdoption() {
        val previous = queueSync.takeAdoptionUndo() ?: return
        val restored = load(previous) ?: return
        player.pause()
        player.restore(restored.items, restored.state)
    }

    private class Restored(val items: List<MediaItem>, val state: QueueState)

    private suspend fun loadSaved(): Restored? = snapshots.snapshot.first()?.let { load(it) }

    /** [snapshot] as playable items, from Room only. */
    private suspend fun load(snapshot: QueueSnapshot): Restored? {
        // In queue order with duplicates; songs missing from the library are dropped (see QueueRestore).
        val songs = library.songsByIds(snapshot.songIds)
        val state = QueueRestore.plan(snapshot, songs.mapTo(HashSet()) { it.id }) ?: return null
        return Restored(songs.map { it.toMediaItem() }, state)
    }
}
