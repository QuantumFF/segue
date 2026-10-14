package com.quantumff.segue.core.network

import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.auth.SubsonicAuthParams
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
import com.quantumff.segue.core.network.model.SubsonicEnvelope
import com.quantumff.segue.core.network.model.SubsonicResponse
import java.io.IOException
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import retrofit2.HttpException

@Suppress("TooManyFunctions")
internal class RetrofitSubsonicClient(
    private val api: SubsonicApi,
    private val json: Json,
    private val restUrl: HttpUrl,
    private val authParams: SubsonicAuthParams,
) : SubsonicClient {
    override suspend fun ping(): NetworkServerInfo {
        val response = call { ping() }
        return NetworkServerInfo(
            version = response.version,
            type = response.type,
            serverVersion = response.serverVersion,
            openSubsonic = response.openSubsonic,
        )
    }

    override suspend fun detectCapabilities(): ServerCapabilities {
        val info = ping()
        val extensions = if (info.openSubsonic) {
            try {
                getOpenSubsonicExtensions()
            } catch (_: SubsonicException) {
                emptyList()
            }
        } else {
            emptyList()
        }
        return ServerCapabilities(
            apiVersion = info.version,
            openSubsonic = info.openSubsonic,
            serverType = info.type,
            serverVersion = info.serverVersion,
            extensions = extensions.associate { it.name to it.versions.toImmutableList() }.toImmutableMap(),
        )
    }

    override suspend fun getOpenSubsonicExtensions(): List<NetworkExtension> = call {
        getOpenSubsonicExtensions()
    }.openSubsonicExtensions

    override suspend fun getLicense(): NetworkLicense = call { getLicense() }.license.required("license")

    override suspend fun getMusicFolders(): List<NetworkMusicFolder> = call {
        getMusicFolders()
    }.musicFolders?.musicFolder.orEmpty()

    override suspend fun getArtists(musicFolderId: String?, ifModifiedSince: Long?): NetworkArtistIndex =
        call { getArtists(musicFolderId, ifModifiedSince) }.artists ?: NetworkArtistIndex()

    override suspend fun getArtist(id: String): NetworkArtist = call { getArtist(id) }.artist.required("artist")

    override suspend fun getAlbum(id: String): NetworkAlbum = call { getAlbum(id) }.album.required("album")

    override suspend fun getAlbumList2(
        type: AlbumListType,
        size: Int,
        offset: Int,
        musicFolderId: String?,
    ): List<NetworkAlbum> =
        call { getAlbumList2(type.apiValue, size, offset, musicFolderId = musicFolderId) }.albumList2?.album.orEmpty()

    override suspend fun getAlbumList2ByYear(fromYear: Int, toYear: Int, size: Int, offset: Int): List<NetworkAlbum> =
        call { getAlbumList2("byYear", size, offset, fromYear = fromYear, toYear = toYear) }.albumList2?.album.orEmpty()

    override suspend fun getAlbumList2ByGenre(genre: String, size: Int, offset: Int): List<NetworkAlbum> =
        call { getAlbumList2("byGenre", size, offset, genre = genre) }.albumList2?.album.orEmpty()

    override suspend fun getSong(id: String): NetworkChild = call { getSong(id) }.song.required("song")

    override suspend fun getRandomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<NetworkChild> =
        call { getRandomSongs(size, genre, fromYear, toYear) }.randomSongs?.song.orEmpty()

    override suspend fun getGenres(): List<NetworkGenre> = call { getGenres() }.genres?.genre.orEmpty()

    override suspend fun getSongsByGenre(genre: String, count: Int, offset: Int): List<NetworkChild> =
        call { getSongsByGenre(genre, count, offset) }.songsByGenre?.song.orEmpty()

    override suspend fun search3(
        query: String,
        artistCount: Int,
        artistOffset: Int,
        albumCount: Int,
        albumOffset: Int,
        songCount: Int,
        songOffset: Int,
    ): NetworkSearchResult3 = call {
        search3(query, artistCount, artistOffset, albumCount, albumOffset, songCount, songOffset)
    }.searchResult3 ?: NetworkSearchResult3()

    override suspend fun getPlaylists(): List<NetworkPlaylist> = call { getPlaylists() }.playlists?.playlist.orEmpty()

    override suspend fun getPlaylist(id: String): NetworkPlaylist = call {
        getPlaylist(id)
    }.playlist.required("playlist")

    override suspend fun createPlaylist(name: String, songIds: List<String>): NetworkPlaylist? =
        call { createPlaylist(name, songIds) }.playlist

    override suspend fun updatePlaylist(
        id: String,
        name: String?,
        comment: String?,
        isPublic: Boolean?,
        songIdsToAdd: List<String>,
        songIndexesToRemove: List<Int>,
    ) {
        call { updatePlaylist(id, name, comment, isPublic, songIdsToAdd, songIndexesToRemove) }
    }

    override suspend fun deletePlaylist(id: String) {
        call { deletePlaylist(id) }
    }

    override suspend fun star(songIds: List<String>, albumIds: List<String>, artistIds: List<String>) {
        call { star(songIds, albumIds, artistIds) }
    }

    override suspend fun unstar(songIds: List<String>, albumIds: List<String>, artistIds: List<String>) {
        call { unstar(songIds, albumIds, artistIds) }
    }

    override suspend fun setRating(id: String, rating: Int) {
        require(rating in 0..MAX_RATING) { "Rating must be 0..$MAX_RATING, was $rating" }
        call { setRating(id, rating) }
    }

    override suspend fun scrobble(songIds: List<String>, timesMillis: List<Long>, submission: Boolean) {
        require(timesMillis.isEmpty() || timesMillis.size == songIds.size) { "One time per song" }
        call { scrobble(songIds, timesMillis, submission) }
    }

    override suspend fun getPlayQueue(): NetworkPlayQueue? = call {
        getPlayQueue()
    }.playQueue?.takeIf { it.entry.isNotEmpty() }

    override suspend fun savePlayQueue(
        songIds: List<String>,
        currentId: String?,
        positionMillis: Long,
        clientName: String?,
    ) {
        val empty = songIds.isEmpty()
        call { savePlayQueue(songIds, currentId.takeUnless { empty }, positionMillis.takeUnless { empty }, clientName) }
    }

    override suspend fun getPlayQueueByIndex(): NetworkPlayQueueByIndex? =
        call { getPlayQueueByIndex() }.playQueueByIndex?.takeIf { it.entry.isNotEmpty() }

    override suspend fun savePlayQueueByIndex(
        songIds: List<String>,
        currentIndex: Int,
        positionMillis: Long,
        clientName: String?,
    ) {
        if (songIds.isEmpty()) {
            // The spec: clear the queue with no parameters, and currentIndex must not be set.
            call { savePlayQueueByIndex(emptyList(), null, null, clientName) }
            return
        }
        require(currentIndex in songIds.indices) { "currentIndex $currentIndex is outside 0..${songIds.lastIndex}" }
        call { savePlayQueueByIndex(songIds, currentIndex, positionMillis, clientName) }
    }

    override suspend fun getLyricsBySongId(id: String): List<NetworkStructuredLyrics> =
        call { getLyricsBySongId(id) }.lyricsList?.structuredLyrics.orEmpty()

    override suspend fun getSimilarSongs2(artistId: String, count: Int): List<NetworkChild> =
        call { getSimilarSongs2(artistId, count) }.similarSongs2?.song.orEmpty()

    override suspend fun getArtistInfo2(artistId: String, similarCount: Int): NetworkArtistInfo =
        call { getArtistInfo2(artistId, similarCount) }.artistInfo2 ?: NetworkArtistInfo()

    override suspend fun getTopSongs(artistName: String, artistId: String?, count: Int): List<NetworkChild> =
        call { getTopSongs(artistName, artistId, count) }.topSongs?.song.orEmpty()

    override suspend fun getInternetRadioStations(): List<NetworkInternetRadioStation> =
        call { getInternetRadioStations() }.internetRadioStations?.internetRadioStation.orEmpty()

    override fun streamUrl(id: String, maxBitRateKbps: Int?, format: String?, timeOffsetSeconds: Int?): String =
        mediaUrl("stream.view") {
            addQueryParameter("id", id)
            maxBitRateKbps?.takeIf { it > 0 }?.let { addQueryParameter("maxBitRate", it.toString()) }
            format?.let { addQueryParameter("format", it) }
            timeOffsetSeconds?.takeIf { it > 0 }?.let { addQueryParameter("timeOffset", it.toString()) }
        }

    override fun coverArtUrl(id: String, size: Int?): String = mediaUrl("getCoverArt.view") {
        addQueryParameter("id", id)
        size?.let { addQueryParameter("size", it.toString()) }
    }

    override fun downloadUrl(id: String): String = mediaUrl("download.view") { addQueryParameter("id", id) }

    private inline fun mediaUrl(endpoint: String, params: HttpUrl.Builder.() -> Unit): String {
        val builder = restUrl.newBuilder().addPathSegment(endpoint).apply(params)
        return authParams.applyTo(builder).build().toString()
    }

    /** Runs a request and turns every failure into a [SubsonicException]. */
    private suspend fun call(request: suspend SubsonicApi.() -> SubsonicEnvelope): SubsonicResponse {
        val envelope = try {
            api.request()
        } catch (e: HttpException) {
            throw errorFromHttp(e)
        } catch (e: SerializationException) {
            throw SubsonicException.Malformed(e)
        } catch (e: IOException) {
            throw SubsonicException.Network(e)
        }
        val response = envelope.response
        if (response.status != STATUS_OK) throw SubsonicException.from(response.error)
        return response
    }

    /** Some servers and proxies send a Subsonic error with a 4xx status. Prefer the typed error. */
    private fun errorFromHttp(e: HttpException): SubsonicException {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val response = body?.let { runCatching { json.decodeFromString<SubsonicEnvelope>(it).response }.getOrNull() }
        return if (response != null && response.status != STATUS_OK) {
            SubsonicException.from(response.error)
        } else {
            SubsonicException.Http(e.code())
        }
    }

    private fun <T : Any> T?.required(name: String): T =
        this ?: throw SubsonicException.Malformed(IllegalStateException("Response has no '$name'"))

    private companion object {
        const val STATUS_OK = "ok"
        const val MAX_RATING = 5
    }
}
