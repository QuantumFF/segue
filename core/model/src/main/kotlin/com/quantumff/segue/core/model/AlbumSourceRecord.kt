package com.quantumff.segue.core.model

/**
 * An [AlbumSource] flattened to plain fields: the one codec for every place a source leaves memory
 * (the album_feed Room row, the PLAY_ALBUMS session command). [kind] names the variant, [id] holds
 * an artist ID, genre or playlist ID, and the sort and filter fields hold an `Albums` source's view.
 *
 * The strings are persisted in Room, so [kind] values and enum-by-name encodings must not change.
 */
data class AlbumSourceRecord(
    val kind: String,
    val id: String? = null,
    val sort: String? = null,
    val starred: Boolean? = null,
    val genre: String? = null,
    val year: Int? = null,
    val downloaded: Boolean? = null,
) {
    /** Null for a record this version cannot read: an unknown kind or sort, or a missing ID. */
    fun toSource(): AlbumSource? = when (kind) {
        KIND_LIBRARY -> AlbumSource.Library

        KIND_ALBUMS -> AlbumSource.Albums(
            sort = AlbumSortOrder.entries.firstOrNull { it.name == sort } ?: return null,
            filter = AlbumFilter(starred, genre, year, downloaded),
        )

        KIND_ARTIST -> AlbumSource.Artist(id ?: return null)

        KIND_GENRE -> AlbumSource.Genre(id ?: return null)

        KIND_PLAYLIST -> AlbumSource.Playlist(id ?: return null)

        else -> null
    }

    companion object {
        private const val KIND_LIBRARY = "Library"
        private const val KIND_ALBUMS = "Albums"
        private const val KIND_ARTIST = "Artist"
        private const val KIND_GENRE = "Genre"
        private const val KIND_PLAYLIST = "Playlist"

        fun of(source: AlbumSource): AlbumSourceRecord = when (source) {
            AlbumSource.Library -> AlbumSourceRecord(KIND_LIBRARY)

            is AlbumSource.Albums -> AlbumSourceRecord(
                kind = KIND_ALBUMS,
                sort = source.sort.name,
                starred = source.filter.starred,
                genre = source.filter.genre,
                year = source.filter.year,
                downloaded = source.filter.downloaded,
            )

            is AlbumSource.Artist -> AlbumSourceRecord(KIND_ARTIST, id = source.artistId)

            is AlbumSource.Genre -> AlbumSourceRecord(KIND_GENRE, id = source.name)

            is AlbumSource.Playlist -> AlbumSourceRecord(KIND_PLAYLIST, id = source.playlistId)
        }
    }
}
