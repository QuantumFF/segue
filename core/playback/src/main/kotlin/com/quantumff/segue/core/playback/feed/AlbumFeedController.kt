package com.quantumff.segue.core.playback.feed

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.quantumff.segue.core.data.repository.AlbumFeedRepository
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.model.AlbumFeed
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.playback.media.albumKey
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.SeguePlayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Play albums" and "Shuffle albums" in the playback service (docs/PLAN.md, "Album shuffle").
 *
 * [start] queues the first window of whole albums (about [AlbumFeedPolicy.WINDOW_TRACKS] tracks)
 * in the request's order and placement. While those albums are the end of the queue, the running
 * [AlbumFeed] appends the next window whenever playback gets within
 * [AlbumFeedPolicy.LOW_WATER_TRACKS] of the end, and persists its cursor so it survives process
 * death ([restore]). Queue sync needs nothing special: it uploads whatever is queued.
 *
 * The feed only ever extends a queue it built. Any other change of what plays or in what order
 * (an added, removed or moved track, a replaced or cleared queue) ends it for
 * good; only metadata updates such as a favorite toggle leave the order as it was.
 *
 * Runs on the player's thread. Register it as a player listener.
 */
class AlbumFeedController(
    private val player: SeguePlayer,
    private val albums: AlbumShuffleRepository,
    private val feeds: AlbumFeedRepository,
    private val scope: CoroutineScope,
    /** What is downloaded, while offline; null online. Only downloaded songs can play offline. */
    private val offline: suspend () -> DownloadedLibrary?,
) : Player.Listener {
    /** The running feed, or null when the queue is not (or no longer) an album feed's. */
    var current: AlbumFeed? = null
        private set

    private var extension: Job? = null

    // All state here is read and written on the player's (main) thread only; the coroutines run on
    // it too, so [current] can be compared by identity after a suspension to spot a stopped feed.
    private var editing = false
    private val persistence = Mutex()
    private val starting = Mutex()

    /**
     * Queues the first window of [request] and, when its albums end the queue, keeps the feed
     * running. False if there is nothing to play (the queue is then left alone).
     */
    suspend fun start(request: AlbumPlayRequest, seed: Long): Boolean = starting.withLock {
        // One start at a time: a quick Play then Shuffle queue one after the other, never interleaved.
        val downloaded = offline()
        val order = albumOrder(request.source, request.order, seed, downloaded)
        val window = windows(request.source, downloaded).next(order, cursor = 0, AlbumFeedPolicy.WINDOW_TRACKS)
        if (window.songs.isEmpty()) return@withLock false
        stop()
        val items = window.songs.map { it.toMediaItem() }
        var first = 0
        ownEdit {
            when {
                request.placement == QueuePlacement.Replace -> {
                    player.setMediaItems(items, 0, 0)
                    player.prepare()
                    player.play()
                }

                player.mediaItemCount == 0 -> {
                    player.setMediaItems(items)
                    player.prepare()
                }

                request.placement == QueuePlacement.Next -> {
                    first = player.currentMediaItemIndex + 1
                    player.insertNext(items)
                }

                else -> {
                    first = player.mediaItemCount
                    player.addMediaItems(items)
                }
            }
        }
        // Only a feed whose albums end the queue may grow; "play next" before other tracks stays one window.
        val endsQueue = first + items.size == player.mediaItemCount
        if (endsQueue && !window.exhausted) {
            current = AlbumFeed(request.source, request.order, seed, window.cursor, fingerprint())
            persist()
            extendIfNeeded()
        }
        true
    }

    /** After the queue snapshot is restored: picks the saved feed back up if the queue is still the one it left. */
    suspend fun restore() {
        val saved = feeds.feed() ?: return
        // Nothing was restored (no snapshot, or it failed): keep the feed for the next start.
        if (current != null || player.mediaItemCount == 0) return
        if (saved.queueFingerprint == fingerprint()) {
            current = saved
            extendIfNeeded()
        } else {
            persist()
        }
    }

    /** The queue may have changed: a change the feed did not make ends it. */
    private fun onQueueChanged() {
        val feed = current ?: return
        if (editing || feed.queueFingerprint == fingerprint()) return
        stop()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) onQueueChanged()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = extendIfNeeded()

    private fun stop() {
        if (current == null) return
        current = null
        extension?.cancel()
        extension = null
        persist()
    }

    private fun extendIfNeeded() {
        val feed = current ?: return
        if (extension?.isActive == true || !AlbumFeedPolicy.shouldExtend(remaining())) return
        extension = scope.launch {
            try {
                extend(feed)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // A failed read leaves the feed as it is; the next track change tries again.
                Log.w(TAG, "Could not extend the album feed", e)
            }
        }
    }

    private suspend fun extend(feed: AlbumFeed) {
        val downloaded = offline()
        val order = albumOrder(feed.source, feed.order, feed.seed, downloaded)
        // Albums already queued are skipped, in case the library changed and the order shifted.
        val queued = List(player.mediaItemCount) { player.getMediaItemAt(it).albumKey }.filterNotNull().toSet()
        val window = windows(feed.source, downloaded)
            .next(order, feed.cursor, AlbumFeedPolicy.EXTENSION_TRACKS, skip = queued)
        // The queue may have been edited while the songs were read.
        if (current !== feed || feed.queueFingerprint != fingerprint()) return
        if (window.songs.isNotEmpty()) ownEdit { player.addMediaItems(window.songs.map { it.toMediaItem() }) }
        if (window.exhausted) {
            stop()
        } else {
            current = feed.copy(cursor = window.cursor, queueFingerprint = fingerprint())
            persist()
        }
    }

    /** Runs a queue change of the feed's own, then records the queue it left. */
    private inline fun ownEdit(block: () -> Unit) {
        editing = true
        try {
            block()
        } finally {
            editing = false
        }
        current = current?.copy(queueFingerprint = fingerprint())
    }

    /**
     * Writes the feed as it is when the write runs: saved while running, cleared once stopped.
     * Writes take turns, so the last one always leaves the latest state.
     */
    private fun persist() {
        scope.launch {
            persistence.withLock {
                val feed = current
                if (feed == null) feeds.clear() else feeds.save(feed)
            }
        }
    }

    private suspend fun albumOrder(
        source: AlbumSource,
        order: AlbumOrder,
        seed: Long,
        downloaded: DownloadedLibrary?,
    ): List<String> =
        AlbumFeedOrder.order(OfflineLibrary.shuffleAlbumIds(albums.albumIds(source), downloaded), order, seed)

    private fun windows(source: AlbumSource, downloaded: DownloadedLibrary?) = AlbumFeedWindows { ids ->
        OfflineLibrary.shuffleSongs(albums.songs(source, ids), downloaded)
    }

    /** Tracks after the current one. */
    private fun remaining(): Int = player.mediaItemCount - 1 - player.currentMediaItemIndex

    private fun fingerprint(): Long = AlbumFeedPolicy.fingerprint(player.queueState().songIds)

    private companion object {
        const val TAG = "AlbumFeed"
    }
}
