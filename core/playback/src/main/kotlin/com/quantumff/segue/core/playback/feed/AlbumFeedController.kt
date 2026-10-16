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
import com.quantumff.segue.core.playback.media.albumKey
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.QueueEditor
import com.quantumff.segue.core.playback.service.SeguePlayer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Play albums" and "Shuffle albums" in the playback service (docs/PLAN.md, "Album shuffle").
 *
 * [start] queues the first window of whole albums (about [AlbumFeedPolicy.WINDOW_TRACKS] tracks)
 * in the request's order and placement ([QueueEditor.place]). While those albums are the end of the queue, the running
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
    /** Places each window; the windows are already narrowed to what can play. */
    private val queue: QueueEditor,
    private val albums: AlbumShuffleRepository,
    private val feeds: AlbumFeedRepository,
    private val scope: CoroutineScope,
    /** What is downloaded, while offline; null online. Only downloaded songs can play offline. */
    private val offline: suspend () -> DownloadedLibrary?,
    /** A fresh seed per started feed: shuffled feeds rebuild their album order from it. */
    private val newSeed: () -> Long = Random.Default::nextLong,
) : Player.Listener {
    /** The running feed, or null when the queue is not (or no longer) an album feed's. */
    var current: AlbumFeed? = null
        private set

    private var extension: Job? = null

    /**
     * A feed whose first window a media session is about to queue ([firstWindowForSession]). It waits
     * for that queue through unrelated queue events (a favorite toggle) until [cancelPending].
     */
    private var pending: AlbumFeed? = null

    // All state here is read and written on the player's (main) thread only; the coroutines run on
    // it too, so [current] can be compared by identity after a suspension to spot a stopped feed.
    private var editing = false
    private val persistence = Mutex()
    private val starting = Mutex()

    /**
     * Queues the first window of [request] and, when its albums end the queue, keeps the feed
     * running. False if there is nothing to play (the queue is then left alone).
     */
    suspend fun start(request: AlbumPlayRequest): Boolean = starting.withLock {
        // One start at a time: a quick Play then Shuffle queue one after the other, never interleaved.
        val seed = newSeed()
        val window = firstWindow(request, seed)
        if (window.songs.isEmpty()) return@withLock false
        stop()
        pending = null
        val items = window.songs.map { it.toMediaItem() }
        var first = 0
        ownEdit { first = queue.place(items, request.placement) }
        // Only a feed whose albums end the queue may grow; "play next" before other tracks stays one window.
        val endsQueue = first + items.size == player.mediaItemCount
        if (endsQueue && !window.exhausted) {
            current = AlbumFeed(request.source, request.order, seed, window.cursor, fingerprint())
            persist()
            extendIfNeeded()
        }
        true
    }

    /**
     * The first window of [request] for a media session to queue itself, as the reply to a media
     * browser's play request (Android Auto's "Shuffle albums"): the session, not the feed, replaces
     * the queue. Once the session has queued exactly these items, the feed runs as if [start] had
     * queued them. Until then it waits, unless the session's next set or add request comes first
     * ([cancelPending]). Empty if there is nothing to play.
     */
    suspend fun firstWindowForSession(request: AlbumPlayRequest): List<MediaItem> = starting.withLock {
        val seed = newSeed()
        val window = firstWindow(request, seed)
        val items = window.songs.map { it.toMediaItem() }
        pending = AlbumFeed(request.source, request.order, seed, window.cursor, fingerprint(items))
            .takeUnless { items.isEmpty() || window.exhausted }
        items
    }

    /**
     * The session took a new set or add request: a first window it prepared before but has not
     * queued is not coming any more. Call before resolving each request.
     */
    fun cancelPending() {
        pending = null
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
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        onQueueChanged()
        adoptPending()
    }

    /**
     * The session queued a prepared first window: the feed takes it over. Other queue changes leave it
     * waiting: between the session's answer and Media3 applying it, the queue may change on its own.
     */
    private fun adoptPending() {
        val feed = pending ?: return
        if (feed.queueFingerprint != fingerprint()) return
        pending = null
        if (current != null) return
        current = feed
        persist()
        extendIfNeeded()
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

    private suspend fun firstWindow(request: AlbumPlayRequest, seed: Long): AlbumFeedWindows.Window {
        val downloaded = offline()
        val order = albumOrder(request.source, request.order, seed, downloaded)
        return windows(request.source, downloaded).next(order, cursor = 0, AlbumFeedPolicy.WINDOW_TRACKS)
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

    private fun fingerprint(items: List<MediaItem>): Long = AlbumFeedPolicy.fingerprint(items.map { it.mediaId })

    private companion object {
        const val TAG = "AlbumFeed"
    }
}
