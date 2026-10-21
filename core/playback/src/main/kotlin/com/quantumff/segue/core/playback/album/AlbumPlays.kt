package com.quantumff.segue.core.playback.album

import androidx.media3.common.MediaItem
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.QueueEditor
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Play albums", "Shuffle albums" and "Shuffle all" in the playback service (docs/adr/0008): every
 * song of the request's album source that can play, queued at once. The songs are read here, from
 * Room, because a whole library would not fit through the session's binder.
 *
 * Play albums keeps the source's album order and shuffle albums shuffles it, each album whole in disc
 * and track order; shuffle all shuffles every song (a tracks shuffle).
 *
 * Call on the player's thread.
 */
class AlbumPlays(
    /** Places the songs, which are already narrowed to what can play, and narrows songs queued with them. */
    private val queue: QueueEditor,
    private val albums: AlbumShuffleRepository,
    /** What is downloaded, while offline; null online. Only downloaded songs can play offline. */
    private val offline: suspend () -> DownloadedLibrary?,
    /** Seeds each shuffle. */
    private val newSeed: () -> Long = Random.Default::nextLong,
) {
    private val starting = Mutex()

    /**
     * Queues [request] at its placement ([QueueEditor.place]). False if there is nothing to play (the
     * queue is then left alone).
     */
    suspend fun start(request: AlbumPlayRequest): Boolean = starting.withLock {
        // One start at a time: a quick Play then Shuffle queue one after the other, never interleaved.
        val items = items(request)
        if (items.isEmpty()) return@withLock false
        queue.place(items, request.placement)
        true
    }

    /**
     * What a media session queues itself for [request] (Android Auto's "Shuffle albums"): [songs]
     * requested with it, narrowed to what can play, then the request's songs. Empty if nothing can play.
     */
    suspend fun items(request: AlbumPlayRequest, songs: List<MediaItem> = emptyList()): List<MediaItem> =
        queue.playable(songs)?.mediaItems.orEmpty() + songs(request).map { it.toMediaItem() }

    private suspend fun songs(request: AlbumPlayRequest): List<Song> {
        val downloaded = offline()
        val random = Random(newSeed())
        if (request.order == AlbumOrder.ShuffledTracks) {
            return OfflineLibrary.shuffleSongs(albums.allSongs(request.source), downloaded).shuffled(random)
        }
        val albumIds = OfflineLibrary.shuffleAlbumIds(albums.albumIds(request.source), downloaded).distinct()
        val order = if (request.order == AlbumOrder.Shuffled) albumIds.shuffled(random) else albumIds
        if (order.isEmpty()) return emptyList()
        val songs = OfflineLibrary.shuffleSongs(albums.songs(request.source, order), downloaded)
        val byAlbum = songs.groupBy { it.albumId }
        return order.flatMap { byAlbum[it].orEmpty().sortedWith(DISC_AND_TRACK) }
    }

    private companion object {
        /** Each album is queued whole, in disc and track order. */
        val DISC_AND_TRACK = compareBy<Song>({ it.discNumber ?: 0 }, { it.track ?: 0 })
    }
}
