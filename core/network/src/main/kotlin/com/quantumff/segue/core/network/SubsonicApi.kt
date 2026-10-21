package com.quantumff.segue.core.network

import com.quantumff.segue.core.network.model.SubsonicEnvelope
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The Subsonic REST endpoints. Auth and `f=json` are added by the interceptor. Null parameters are
 * omitted; list parameters repeat (`id=a&id=b`).
 *
 * Saving the queue posts its song IDs as a form (OpenSubsonic `formPost`): a queue of thousands of
 * songs is far too long for a URL.
 */
internal interface SubsonicApi {
    @GET("rest/ping.view")
    suspend fun ping(): SubsonicEnvelope

    @GET("rest/getOpenSubsonicExtensions.view")
    suspend fun getOpenSubsonicExtensions(): SubsonicEnvelope

    @GET("rest/getLicense.view")
    suspend fun getLicense(): SubsonicEnvelope

    @GET("rest/getMusicFolders.view")
    suspend fun getMusicFolders(): SubsonicEnvelope

    @GET("rest/getArtists.view")
    suspend fun getArtists(
        @Query("musicFolderId") musicFolderId: String?,
        @Query("ifModifiedSince") ifModifiedSince: Long?,
    ): SubsonicEnvelope

    @GET("rest/getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope

    @Suppress("LongParameterList")
    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int,
        @Query("offset") offset: Int,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("musicFolderId") musicFolderId: String? = null,
    ): SubsonicEnvelope

    @GET("rest/getSong.view")
    suspend fun getSong(@Query("id") id: String): SubsonicEnvelope

    @GET("rest/getGenres.view")
    suspend fun getGenres(): SubsonicEnvelope

    @GET("rest/getSongsByGenre.view")
    suspend fun getSongsByGenre(
        @Query("genre") genre: String,
        @Query("count") count: Int,
        @Query("offset") offset: Int,
    ): SubsonicEnvelope

    @Suppress("LongParameterList")
    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int,
        @Query("artistOffset") artistOffset: Int,
        @Query("albumCount") albumCount: Int,
        @Query("albumOffset") albumOffset: Int,
        @Query("songCount") songCount: Int,
        @Query("songOffset") songOffset: Int,
    ): SubsonicEnvelope

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(): SubsonicEnvelope

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("rest/createPlaylist.view")
    suspend fun createPlaylist(@Query("name") name: String, @Query("songId") songIds: List<String>): SubsonicEnvelope

    @Suppress("LongParameterList")
    @GET("rest/updatePlaylist.view")
    suspend fun updatePlaylist(
        @Query("playlistId") id: String,
        @Query("name") name: String?,
        @Query("comment") comment: String?,
        @Query("public") public: Boolean?,
        @Query("songIdToAdd") songIdsToAdd: List<String>,
        @Query("songIndexToRemove") songIndexesToRemove: List<Int>,
    ): SubsonicEnvelope

    @GET("rest/deletePlaylist.view")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("rest/star.view")
    suspend fun star(
        @Query("id") ids: List<String>,
        @Query("albumId") albumIds: List<String>,
        @Query("artistId") artistIds: List<String>,
    ): SubsonicEnvelope

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("id") ids: List<String>,
        @Query("albumId") albumIds: List<String>,
        @Query("artistId") artistIds: List<String>,
    ): SubsonicEnvelope

    @GET("rest/setRating.view")
    suspend fun setRating(@Query("id") id: String, @Query("rating") rating: Int): SubsonicEnvelope

    @GET("rest/scrobble.view")
    suspend fun scrobble(
        @Query("id") ids: List<String>,
        @Query("time") timesMillis: List<Long>,
        @Query("submission") submission: Boolean,
    ): SubsonicEnvelope

    @GET("rest/getPlayQueue.view")
    suspend fun getPlayQueue(): SubsonicEnvelope

    @FormUrlEncoded
    @POST("rest/savePlayQueue.view")
    suspend fun savePlayQueue(
        @Field("id") ids: List<String>,
        @Field("current") current: String?,
        @Field("position") positionMillis: Long?,
        @Query("c") clientName: String?,
    ): SubsonicEnvelope

    @GET("rest/getPlayQueueByIndex.view")
    suspend fun getPlayQueueByIndex(): SubsonicEnvelope

    @FormUrlEncoded
    @POST("rest/savePlayQueueByIndex.view")
    suspend fun savePlayQueueByIndex(
        @Field("id") ids: List<String>,
        @Field("currentIndex") currentIndex: Int?,
        @Field("position") positionMillis: Long?,
        @Query("c") clientName: String?,
    ): SubsonicEnvelope

    @GET("rest/getLyricsBySongId.view")
    suspend fun getLyricsBySongId(@Query("id") id: String): SubsonicEnvelope

    @GET("rest/getArtistInfo2.view")
    suspend fun getArtistInfo2(@Query("id") id: String, @Query("count") count: Int): SubsonicEnvelope

    @GET("rest/getSimilarSongs2.view")
    suspend fun getSimilarSongs2(@Query("id") artistId: String, @Query("count") count: Int): SubsonicEnvelope

    @GET("rest/getTopSongs.view")
    suspend fun getTopSongs(
        @Query("artist") artistName: String,
        @Query("id") artistId: String?,
        @Query("count") count: Int,
    ): SubsonicEnvelope

    @GET("rest/getInternetRadioStations.view")
    suspend fun getInternetRadioStations(): SubsonicEnvelope
}
