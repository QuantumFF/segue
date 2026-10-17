package com.quantumff.segue.core.playback.browse

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.playback.media.toMediaItem

/** What the media session does with a controller's set or add request ([SessionPlayResolver]). */
sealed interface SessionPlay {
    /**
     * Queue [items]: a set from [startIndex] at [startPositionMs], shuffled first with [shuffle].
     * The session still narrows them to what can play
     * ([com.quantumff.segue.core.playback.service.QueueEditor.playable]). An add of several items
     * with "Shuffle albums" among them also starts that [albumFeed], which appends its own window.
     */
    data class Items(
        val items: List<MediaItem>,
        val startIndex: Int = 0,
        val startPositionMs: Long = 0,
        val shuffle: Boolean = false,
        val albumFeed: AlbumPlayRequest? = null,
    ) : SessionPlay

    /** Start an album feed instead of a fixed list; a set replaces the queue, an add appends. */
    data class AlbumFeed(val request: AlbumPlayRequest) : SessionPlay

    /** "Play music" while something is queued: carry on with the queue. */
    data object KeepQueue : SessionPlay

    /** A library request from a caller that may not browse the library. */
    data object NotAllowed : SessionPlay

    /** A library request that matched nothing playable. */
    data object NothingToPlay : SessionPlay
}

/**
 * Decides what a controller's set or add request queues, for every controller (the app, Android
 * Auto, Assistant, Bluetooth). Plain song items pass through. A library request is a browse node
 * (Android Auto) or a voice "play <query>": it stands for songs from Room, so only allowed
 * browsers may make one ([resolveSet]'s and [resolveAdd]'s `allowed`).
 *
 * - Setting a track plays its whole list from that track; adding one adds only the track.
 * - "Shuffle albums", and a voice "play some music" (an empty query) with nothing queued, are the
 *   whole library as a shuffled album feed. With a queue, "play some music" carries on with it.
 *
 * Reads Room (and the server, for voice search); call from the session's thread.
 */
class SessionPlayResolver(private val tree: BrowseTree, private val search: LibrarySearch) {
    /** A set request (the session callback's `onSetMediaItems`); [hasQueue] is whether anything is queued now. */
    suspend fun resolveSet(
        items: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        allowed: Boolean,
        hasQueue: Boolean,
    ): SessionPlay {
        val item = items.singleOrNull()?.takeIf { it.isLibraryRequest }
            ?: return when (val added = resolveAdd(items, allowed)) {
                // Media3's default for a set: the items as an add resolves them, from the requested start.
                is SessionPlay.Items -> added.copy(startIndex = startIndex, startPositionMs = startPositionMs)

                else -> added
            }
        if (!allowed) return SessionPlay.NotAllowed
        if (item.searchQuery?.isBlank() == true && hasQueue) return SessionPlay.KeepQueue
        return resolve(item, adding = false)
    }

    /** An add request (the session callback's `onAddMediaItems`). */
    suspend fun resolveAdd(items: List<MediaItem>, allowed: Boolean): SessionPlay {
        if (items.none { it.isLibraryRequest }) return SessionPlay.Items(items)
        if (!allowed) return SessionPlay.NotAllowed
        items.singleOrNull()?.let { return resolve(it, adding = true) }
        // Several items: each node's songs, plain songs as they are, and "Shuffle albums" as a feed beside them.
        val plays = items.map { if (it.isLibraryRequest) resolve(it, adding = true) else SessionPlay.Items(listOf(it)) }
        val songs = plays.filterIsInstance<SessionPlay.Items>().flatMap { it.items }
        val feed = plays.filterIsInstance<SessionPlay.AlbumFeed>().firstOrNull()?.request
        if (songs.isEmpty() && feed == null) return SessionPlay.NothingToPlay
        return SessionPlay.Items(songs, albumFeed = feed)
    }

    private suspend fun resolve(item: MediaItem, adding: Boolean): SessionPlay {
        val query = item.searchQuery
        if (query?.isBlank() == true || BrowseId.parse(item.mediaId) == BrowseId.ShuffleAlbums) {
            val placement = if (adding) QueuePlacement.End else QueuePlacement.Replace
            return SessionPlay.AlbumFeed(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.Shuffled, placement))
        }
        val request = when {
            query != null -> search.playRequest(query, item.requestMetadata.extras ?: Bundle.EMPTY)
            adding -> tree.songs(item.mediaId)?.let { PlayRequest(it) }
            else -> tree.playRequest(item.mediaId)
        }
        if (request == null || request.songs.isEmpty()) return SessionPlay.NothingToPlay
        return SessionPlay.Items(request.songs.map { it.toMediaItem() }, request.startIndex, shuffle = request.shuffle)
    }

    private val MediaItem.searchQuery: String? get() = requestMetadata.searchQuery

    private val MediaItem.isLibraryRequest: Boolean get() = searchQuery != null || BrowseId.parse(mediaId) != null
}
