package com.quantumff.segue.core.playback.browse

import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song

/** An in-memory library. Records the offset/limit of paged reads. */
class FakeMediaBrowseRepository : MediaBrowseRepository {
    var albums: List<Album> = emptyList()
    var recentlyPlayed: List<Album> = emptyList()
    var recentlyAdded: List<Album> = emptyList()
    var artists: List<Artist> = emptyList()
    var genres: List<Genre> = emptyList()
    var playlists: List<Playlist> = emptyList()
    var songs: List<Song> = emptyList()
    var downloadedIds: List<String> = emptyList()

    /** Playlist ID to song IDs, in order. */
    var playlistSongIds: Map<String, List<String>> = emptyMap()

    /** Genre to album IDs. */
    var genreAlbumIds: Map<String, List<String>> = emptyMap()

    val pagedReads = mutableListOf<Triple<String, Int, Int>>()

    private fun <T> List<T>.page(name: String, offset: Int, limit: Int): List<T> {
        pagedReads += Triple(name, offset, limit)
        return drop(offset).take(limit)
    }

    private fun songsById(ids: List<String>) = ids.mapNotNull { id -> songs.firstOrNull { it.id == id } }

    override suspend fun recentlyPlayedAlbums(offset: Int, limit: Int) = recentlyPlayed.page("recent", offset, limit)

    override suspend fun recentlyAddedAlbums(offset: Int, limit: Int) = recentlyAdded.page("added", offset, limit)

    override suspend fun starredSongs(offset: Int, limit: Int) =
        songs.filter { it.isStarred }.page("starred", offset, limit)

    override suspend fun albums(offset: Int, limit: Int) = albums.page("albums", offset, limit)

    override suspend fun artists(offset: Int, limit: Int) = artists.page("artists", offset, limit)

    override suspend fun artistAlbums(artistId: String) = albums.filter { it.artistId == artistId }

    override suspend fun genres() = genres

    override suspend fun genreAlbums(genre: String, offset: Int, limit: Int) =
        genreAlbumIds[genre].orEmpty().mapNotNull { id -> albums.firstOrNull { it.id == id } }.page("genre", offset, limit)

    override suspend fun playlists() = playlists

    override suspend fun downloadedSongs(offset: Int, limit: Int) = songsById(downloadedIds).page("downloads", offset, limit)

    override suspend fun albumSongs(albumId: String) =
        songs.filter { it.albumId == albumId }.sortedWith(compareBy({ it.discNumber ?: 0 }, { it.track ?: 0 }))

    override suspend fun albumSongsForPlayback(albumId: String) = albumSongs(albumId)

    override suspend fun playlistSongs(playlistId: String) = songsById(playlistSongIds[playlistId].orEmpty())

    override suspend fun album(id: String) = albums.firstOrNull { it.id == id }

    override suspend fun artist(id: String) = artists.firstOrNull { it.id == id }

    override suspend fun playlist(id: String) = playlists.firstOrNull { it.id == id }

    override suspend fun song(id: String) = songs.firstOrNull { it.id == id }

    var homeListRefreshes = 0

    override suspend fun refreshHomeLists() {
        homeListRefreshes++
    }
}
