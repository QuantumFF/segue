package com.quantumff.segue.core.playback.shuffle

import com.quantumff.segue.core.model.Song

/** Album feeds queue each album whole, in disc and track order. */
object AlbumPicker {
    /** [songs] grouped by album in [albumOrder], disc then track order inside each album. Other songs are dropped. */
    fun queueInAlbumOrder(songs: List<Song>, albumOrder: List<String>): List<Song> {
        val byAlbum = songs.groupBy { it.albumId }
        return albumOrder.flatMap { albumId ->
            byAlbum[albumId].orEmpty().sortedWith(compareBy({ it.discNumber ?: 0 }, { it.track ?: 0 }))
        }
    }
}
