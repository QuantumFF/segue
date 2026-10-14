package com.quantumff.segue.core.playback.shuffle

import com.quantumff.segue.core.model.Song
import kotlin.random.Random

/** "Shuffle albums": random albums, each queued whole in disc and track order. */
object AlbumPicker {
    /** How many albums a library- or genre-wide album shuffle queues. */
    const val DEFAULT_ALBUM_COUNT = 25

    /** Up to [count] distinct album IDs from [albumIds], in random order. */
    fun pick(albumIds: List<String>, count: Int, random: Random): List<String> =
        albumIds.distinct().shuffled(random).take(count)

    /** [songs] grouped by album in [albumOrder], disc then track order inside each album. Other songs are dropped. */
    fun queueInAlbumOrder(songs: List<Song>, albumOrder: List<String>): List<Song> {
        val byAlbum = songs.groupBy { it.albumId }
        return albumOrder.flatMap { albumId ->
            byAlbum[albumId].orEmpty().sortedWith(compareBy({ it.discNumber ?: 0 }, { it.track ?: 0 }))
        }
    }
}
