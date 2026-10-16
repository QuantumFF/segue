package com.quantumff.segue.core.network

import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.model.NetworkAlbum
import com.quantumff.segue.core.network.model.NetworkArtist
import com.quantumff.segue.core.network.model.NetworkArtistIndex
import com.quantumff.segue.core.network.model.NetworkArtistInfo
import com.quantumff.segue.core.network.model.NetworkChild
import com.quantumff.segue.core.network.model.NetworkExtension
import com.quantumff.segue.core.network.model.NetworkGenre
import com.quantumff.segue.core.network.model.NetworkInternetRadioStation
import com.quantumff.segue.core.network.model.NetworkLicense
import com.quantumff.segue.core.network.model.NetworkMusicFolder
import com.quantumff.segue.core.network.model.NetworkPlayQueue
import com.quantumff.segue.core.network.model.NetworkPlayQueueByIndex
import com.quantumff.segue.core.network.model.NetworkPlaylist
import com.quantumff.segue.core.network.model.NetworkSearchResult3
import com.quantumff.segue.core.network.model.NetworkServerInfo
import com.quantumff.segue.core.network.model.NetworkStructuredLyrics

/**
 * A Subsonic/OpenSubsonic client bound to one server account. Create it with [SubsonicClientFactory].
 *
 * Every call throws [SubsonicException] on failure. Calls are main-safe: OkHttp runs them on its
 * own threads.
 */
@Suppress("TooManyFunctions")
interface SubsonicClient {
    suspend fun ping(): NetworkServerInfo

    /** Pings, then asks an OpenSubsonic server for its extensions. Extension failures are not fatal. */
    suspend fun detectCapabilities(): ServerCapabilities

    suspend fun getOpenSubsonicExtensions(): List<NetworkExtension>

    suspend fun getLicense(): NetworkLicense

    suspend fun getMusicFolders(): List<NetworkMusicFolder>

    /**
     * [ifModifiedSince] is epoch milliseconds. A server that honors it returns an empty index when
     * nothing changed. Navidrome ignores it, so compare [NetworkArtistIndex.lastModified] too.
     */
    suspend fun getArtists(musicFolderId: String? = null, ifModifiedSince: Long? = null): NetworkArtistIndex

    suspend fun getArtist(id: String): NetworkArtist

    suspend fun getAlbum(id: String): NetworkAlbum

    /** Navidrome caps [size] at 500. */
    suspend fun getAlbumList2(
        type: AlbumListType,
        size: Int,
        offset: Int,
        musicFolderId: String? = null,
    ): List<NetworkAlbum>

    suspend fun getAlbumList2ByYear(fromYear: Int, toYear: Int, size: Int, offset: Int): List<NetworkAlbum>

    suspend fun getAlbumList2ByGenre(genre: String, size: Int, offset: Int): List<NetworkAlbum>

    suspend fun getSong(id: String): NetworkChild

    suspend fun getRandomSongs(
        size: Int,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<NetworkChild>

    suspend fun getGenres(): List<NetworkGenre>

    suspend fun getSongsByGenre(genre: String, count: Int, offset: Int): List<NetworkChild>

    @Suppress("LongParameterList")
    suspend fun search3(
        query: String,
        artistCount: Int = 20,
        artistOffset: Int = 0,
        albumCount: Int = 20,
        albumOffset: Int = 0,
        songCount: Int = 20,
        songOffset: Int = 0,
    ): NetworkSearchResult3

    suspend fun getPlaylists(): List<NetworkPlaylist>

    suspend fun getPlaylist(id: String): NetworkPlaylist

    /** Returns the new playlist when the server sends it back (API 1.14.0+). */
    suspend fun createPlaylist(name: String, songIds: List<String>): NetworkPlaylist?

    suspend fun updatePlaylist(
        id: String,
        name: String? = null,
        comment: String? = null,
        isPublic: Boolean? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList(),
    )

    suspend fun deletePlaylist(id: String)

    suspend fun star(
        songIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    )

    suspend fun unstar(
        songIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    )

    /** [rating] 1–5, or 0 to clear. */
    suspend fun setRating(id: String, rating: Int)

    /** Submits several plays in one request. [timesMillis] matches [songIds] by position. */
    suspend fun scrobble(songIds: List<String>, timesMillis: List<Long>, submission: Boolean)

    /** Null when no queue is saved. */
    suspend fun getPlayQueue(): NetworkPlayQueue?

    /**
     * [clientName] overrides `c` for this request. Navidrome records it as the queue's `changedBy`,
     * so pass the stable per-device name to tell devices apart.
     */
    suspend fun savePlayQueue(songIds: List<String>, currentId: String?, positionMillis: Long, clientName: String?)

    /** OpenSubsonic `indexBasedQueue`. Null when no queue is saved. */
    suspend fun getPlayQueueByIndex(): NetworkPlayQueueByIndex?

    /** OpenSubsonic `indexBasedQueue`. Empty [songIds] clears the saved queue. See [savePlayQueue] for [clientName]. */
    suspend fun savePlayQueueByIndex(
        songIds: List<String>,
        currentIndex: Int,
        positionMillis: Long,
        clientName: String?,
    )

    /** OpenSubsonic `songLyrics`. */
    suspend fun getLyricsBySongId(id: String): List<NetworkStructuredLyrics>

    suspend fun getSimilarSongs2(artistId: String, count: Int): List<NetworkChild>

    /** Biography and up to [similarCount] similar artists. Empty when the server has none. */
    suspend fun getArtistInfo2(artistId: String, similarCount: Int): NetworkArtistInfo

    /** [artistId] is used by servers with the `topSongsByArtistId` extension. */
    suspend fun getTopSongs(artistName: String, artistId: String? = null, count: Int): List<NetworkChild>

    suspend fun getInternetRadioStations(): List<NetworkInternetRadioStation>

    /**
     * An authenticated `stream` URL. Each call makes a new salt, so do not use the URL as a cache key.
     * [timeOffsetSeconds] needs the `transcodeOffset` extension.
     */
    fun streamUrl(
        id: String,
        maxBitRateKbps: Int? = null,
        format: String? = null,
        timeOffsetSeconds: Int? = null,
    ): String

    /** An authenticated `getCoverArt` URL. Request the displayed size. Cache by ID and size, not by URL. */
    fun coverArtUrl(id: String, size: Int? = null): String

    /** An authenticated `download` URL for the original file. */
    fun downloadUrl(id: String): String
}

/** The account a [SubsonicClient] talks to. [baseUrl] may include a path, e.g. `https://host/navidrome`. */
data class SubsonicServerConfig(val baseUrl: String, val username: String, val password: String) {
    override fun toString() = "SubsonicServerConfig(baseUrl=$baseUrl, username=$username, password=***)"
}
