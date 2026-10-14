package com.quantumff.segue.core.data.testing

import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.SubsonicClient
import com.quantumff.segue.core.network.SubsonicException
import com.quantumff.segue.core.network.model.NetworkAlbum
import com.quantumff.segue.core.network.model.NetworkArtist
import com.quantumff.segue.core.network.model.NetworkArtistIndex
import com.quantumff.segue.core.network.model.NetworkArtistInfo
import com.quantumff.segue.core.network.model.NetworkChild
import com.quantumff.segue.core.network.model.NetworkExtension
import com.quantumff.segue.core.network.model.NetworkGenre
import com.quantumff.segue.core.network.model.NetworkIndex
import com.quantumff.segue.core.network.model.NetworkInternetRadioStation
import com.quantumff.segue.core.network.model.NetworkLicense
import com.quantumff.segue.core.network.model.NetworkMusicFolder
import com.quantumff.segue.core.network.model.NetworkPlayQueue
import com.quantumff.segue.core.network.model.NetworkPlayQueueByIndex
import com.quantumff.segue.core.network.model.NetworkPlaylist
import com.quantumff.segue.core.network.model.NetworkSearchResult3
import com.quantumff.segue.core.network.model.NetworkServerInfo
import com.quantumff.segue.core.network.model.NetworkStructuredLyrics
import kotlinx.coroutines.delay

/** An in-memory Subsonic server. Configure its library, then inspect the calls it recorded. */
@Suppress("TooManyFunctions")
class FakeSubsonicClient : SubsonicClient {
    var capabilities = ServerCapabilities.Unknown
    var pingError: SubsonicException? = null

    /** Thrown by the next write call (star, rating, playlist edits, queue saves), then cleared. */
    var nextWriteError: SubsonicException? = null

    var artists = listOf<NetworkArtist>()
    var artistsLastModified = 1L

    /** Simulates a server that honors `ifModifiedSince` by returning an empty index. */
    var honorIfModifiedSince = false
    val artistRequests = mutableListOf<Long?>()

    /** Albums with their songs, in "newest first" order. */
    val albums = linkedMapOf<String, NetworkAlbum>()
    val albumRequests = mutableListOf<String>()
    val albumListRequests = mutableListOf<Triple<AlbumListType, Int, Int>>()
    var albumFetchDelayMillis = 0L

    /** Albums that appear in lists but were deleted before `getAlbum`. */
    val missingOnFetch = mutableSetOf<String>()
    var maxConcurrentAlbumFetches = 0
        private set
    private var albumFetchesInFlight = 0

    var genres = listOf<NetworkGenre>()
    val playlists = linkedMapOf<String, NetworkPlaylist>()
    val playlistRequests = mutableListOf<String>()
    val playlistEdits = mutableListOf<String>()

    var searchResult = NetworkSearchResult3()
    var searchError: SubsonicException? = null
    val searchQueries = mutableListOf<String>()

    var playQueue: NetworkPlayQueue? = null
    var playQueueByIndex: NetworkPlayQueueByIndex? = null
    val savedQueues = mutableListOf<SavedQueue>()

    val starCalls = mutableListOf<String>()
    val lyrics = mutableMapOf<String, List<NetworkStructuredLyrics>>()
    val lyricsRequests = mutableListOf<String>()
    var randomSongs = listOf<NetworkChild>()

    data class SavedQueue(
        val indexBased: Boolean,
        val ids: List<String>,
        val current: String?,
        val positionMillis: Long,
        val clientName: String?,
    )

    fun addAlbum(album: NetworkAlbum, newest: Boolean = false) {
        if (newest) {
            val rest = albums.toMap()
            albums.clear()
            albums[album.id] = album
            albums.putAll(rest)
        } else {
            albums[album.id] = album
        }
    }

    private fun write() {
        nextWriteError?.let {
            nextWriteError = null
            throw it
        }
    }

    override suspend fun ping(): NetworkServerInfo {
        pingError?.let { throw it }
        return NetworkServerInfo(
            capabilities.apiVersion,
            capabilities.serverType,
            capabilities.serverVersion,
            capabilities.openSubsonic,
        )
    }

    override suspend fun detectCapabilities(): ServerCapabilities {
        ping()
        return capabilities
    }

    override suspend fun getOpenSubsonicExtensions(): List<NetworkExtension> =
        capabilities.extensions.map { NetworkExtension(it.key, it.value) }

    override suspend fun getLicense() = NetworkLicense(valid = true)

    override suspend fun getMusicFolders() = emptyList<NetworkMusicFolder>()

    override suspend fun getArtists(musicFolderId: String?, ifModifiedSince: Long?): NetworkArtistIndex {
        artistRequests += ifModifiedSince
        if (honorIfModifiedSince && ifModifiedSince != null && ifModifiedSince >= artistsLastModified) {
            return NetworkArtistIndex(lastModified = artistsLastModified)
        }
        return NetworkArtistIndex(index = listOf(NetworkIndex("A", artists)), lastModified = artistsLastModified)
    }

    override suspend fun getArtist(id: String): NetworkArtist {
        val artist = artists.firstOrNull { it.id == id } ?: throw SubsonicException.NotFound("no artist")
        return artist.copy(album = albums.values.filter { it.artistId == id }.map { it.copy(song = emptyList()) })
    }

    override suspend fun getAlbum(id: String): NetworkAlbum {
        albumRequests += id
        albumFetchesInFlight++
        maxConcurrentAlbumFetches = maxOf(maxConcurrentAlbumFetches, albumFetchesInFlight)
        try {
            if (albumFetchDelayMillis > 0) delay(albumFetchDelayMillis)
            if (id in missingOnFetch) throw SubsonicException.NotFound("Album not found")
            return albums[id] ?: throw SubsonicException.NotFound("Album not found")
        } finally {
            albumFetchesInFlight--
        }
    }

    override suspend fun getAlbumList2(
        type: AlbumListType,
        size: Int,
        offset: Int,
        musicFolderId: String?,
    ): List<NetworkAlbum> {
        albumListRequests += Triple(type, size, offset)
        val ordered = when (type) {
            AlbumListType.AlphabeticalByName -> albums.values.sortedBy { it.name }
            else -> albums.values.toList()
        }
        return ordered.drop(offset).take(size).map { it.copy(song = emptyList()) }
    }

    override suspend fun getAlbumList2ByYear(fromYear: Int, toYear: Int, size: Int, offset: Int) =
        emptyList<NetworkAlbum>()

    override suspend fun getAlbumList2ByGenre(genre: String, size: Int, offset: Int) = emptyList<NetworkAlbum>()

    override suspend fun getSong(id: String): NetworkChild =
        albums.values.flatMap { it.song }.firstOrNull { it.id == id } ?: throw SubsonicException.NotFound("no song")

    override suspend fun getRandomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?) =
        randomSongs.take(size)

    override suspend fun getGenres() = genres

    override suspend fun getSongsByGenre(genre: String, count: Int, offset: Int) = emptyList<NetworkChild>()

    override suspend fun search3(
        query: String,
        artistCount: Int,
        artistOffset: Int,
        albumCount: Int,
        albumOffset: Int,
        songCount: Int,
        songOffset: Int,
    ): NetworkSearchResult3 {
        searchQueries += query
        searchError?.let { throw it }
        return searchResult
    }

    override suspend fun getPlaylists() = playlists.values.map { it.copy(entry = emptyList()) }

    override suspend fun getPlaylist(id: String): NetworkPlaylist {
        playlistRequests += id
        return playlists[id] ?: throw SubsonicException.NotFound("no playlist")
    }

    override suspend fun createPlaylist(name: String, songIds: List<String>): NetworkPlaylist {
        write()
        playlistEdits += "create $name $songIds"
        val playlist = NetworkPlaylist(
            id = "new-${playlists.size}",
            name = name,
            songCount = songIds.size,
            changed = "2024-01-01T00:00:00Z",
            entry = songIds.map { NetworkChild(id = it, title = it) },
        )
        playlists[playlist.id] = playlist
        return playlist
    }

    override suspend fun updatePlaylist(
        id: String,
        name: String?,
        comment: String?,
        isPublic: Boolean?,
        songIdsToAdd: List<String>,
        songIndexesToRemove: List<Int>,
    ) {
        write()
        playlistEdits += "update $id name=$name add=$songIdsToAdd remove=$songIndexesToRemove"
        val playlist = playlists.getValue(id)
        val entries = playlist.entry.filterIndexed { index, _ -> index !in songIndexesToRemove } +
            songIdsToAdd.map { NetworkChild(id = it, title = it) }
        playlists[id] = playlist.copy(name = name ?: playlist.name, entry = entries, songCount = entries.size)
    }

    override suspend fun deletePlaylist(id: String) {
        write()
        playlistEdits += "delete $id"
        playlists.remove(id)
    }

    override suspend fun star(songIds: List<String>, albumIds: List<String>, artistIds: List<String>) {
        write()
        starCalls += "star songs=$songIds albums=$albumIds artists=$artistIds"
    }

    override suspend fun unstar(songIds: List<String>, albumIds: List<String>, artistIds: List<String>) {
        write()
        starCalls += "unstar songs=$songIds albums=$albumIds artists=$artistIds"
    }

    override suspend fun setRating(id: String, rating: Int) {
        write()
        starCalls += "rate $id $rating"
    }

    override suspend fun scrobble(songIds: List<String>, timesMillis: List<Long>, submission: Boolean) = Unit

    override suspend fun getPlayQueue() = playQueue

    override suspend fun savePlayQueue(
        songIds: List<String>,
        currentId: String?,
        positionMillis: Long,
        clientName: String?,
    ) {
        write()
        savedQueues += SavedQueue(indexBased = false, songIds, currentId, positionMillis, clientName)
    }

    override suspend fun getPlayQueueByIndex() = playQueueByIndex

    override suspend fun savePlayQueueByIndex(
        songIds: List<String>,
        currentIndex: Int,
        positionMillis: Long,
        clientName: String?,
    ) {
        write()
        savedQueues += SavedQueue(indexBased = true, songIds, currentIndex.toString(), positionMillis, clientName)
    }

    override suspend fun getLyricsBySongId(id: String): List<NetworkStructuredLyrics> {
        lyricsRequests += id
        return lyrics[id].orEmpty()
    }

    override suspend fun getSimilarSongs2(artistId: String, count: Int) = emptyList<NetworkChild>()

    var artistInfo = NetworkArtistInfo()

    override suspend fun getArtistInfo2(artistId: String, similarCount: Int) = artistInfo

    override suspend fun getTopSongs(artistName: String, artistId: String?, count: Int) = emptyList<NetworkChild>()

    override suspend fun getInternetRadioStations() = emptyList<NetworkInternetRadioStation>()

    override fun streamUrl(id: String, maxBitRateKbps: Int?, format: String?, timeOffsetSeconds: Int?) =
        "https://fake/stream/$id"

    override fun coverArtUrl(id: String, size: Int?) = "https://fake/cover/$id?size=$size"

    override fun downloadUrl(id: String) = "https://fake/download/$id"
}
