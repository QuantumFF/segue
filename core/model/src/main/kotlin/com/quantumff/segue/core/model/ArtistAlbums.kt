package com.quantumff.segue.core.model

/**
 * An artist page's albums (DESIGN.md §4.4): the artist's own albums (they are the album artist),
 * then the albums by others that credit them. Each part keeps the order it was given in.
 */
data class ArtistAlbums(val own: List<Album>, val appearsOn: List<Album>) {
    /** The order the page shows the albums in, which is also the order "Play albums" queues them in. */
    val inPageOrder: List<Album> get() = own + appearsOn

    companion object {
        fun of(artistId: String, albums: List<Album>): ArtistAlbums {
            val (own, appearsOn) = albums.partition { it.artistId == artistId }
            return ArtistAlbums(own, appearsOn)
        }
    }
}
