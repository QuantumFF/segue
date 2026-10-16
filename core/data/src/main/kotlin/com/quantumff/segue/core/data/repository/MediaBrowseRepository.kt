package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.AlbumListDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.dao.BrowseDao
import com.quantumff.segue.core.database.dao.GenreDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * One-shot, offset/limit reads of the local library for media browsers such as Android Auto.
 * Reads Room only (except [albumSongsForPlayback]), so browsing works offline and stays fast.
 * Everything is empty when signed out.
 */
interface MediaBrowseRepository {
    /** The server's "recently played" list as last refreshed, else albums by their synced play time. */
    suspend fun recentlyPlayedAlbums(offset: Int, limit: Int): List<Album>

    /** The server's "newest" list as last refreshed, else albums by when they were added. */
    suspend fun recentlyAddedAlbums(offset: Int, limit: Int): List<Album>

    suspend fun starredSongs(offset: Int, limit: Int): List<Song>

    suspend fun albums(offset: Int, limit: Int): List<Album>

    suspend fun artists(offset: Int, limit: Int): List<Artist>

    /** Newest first. */
    suspend fun artistAlbums(artistId: String): List<Album>

    suspend fun genres(): List<Genre>

    suspend fun genreAlbums(genre: String, offset: Int, limit: Int): List<Album>

    suspend fun playlists(): List<Playlist>

    suspend fun downloadedSongs(offset: Int, limit: Int): List<Song>

    /** In disc and track order, as synced. */
    suspend fun albumSongs(albumId: String): List<Song>

    /** Like [albumSongs], but fetches the album first when its songs were never synced. Room if offline. */
    suspend fun albumSongsForPlayback(albumId: String): List<Song>

    /** In playlist order. */
    suspend fun playlistSongs(playlistId: String): List<Song>

    suspend fun album(id: String): Album?

    suspend fun artist(id: String): Artist?

    suspend fun playlist(id: String): Playlist?

    suspend fun song(id: String): Song?

    /** Refreshes the "recently played" and "newest" lists from the server. Throws RemoteException. */
    suspend fun refreshHomeLists()
}

@Suppress("TooManyFunctions")
internal class RoomMediaBrowseRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val browseDao: BrowseDao,
    private val albumDao: AlbumDao,
    private val albumListDao: AlbumListDao,
    private val artistDao: ArtistDao,
    private val songDao: SongDao,
    private val genreDao: GenreDao,
    private val playlistDao: PlaylistDao,
    private val albumRepository: AlbumRepository,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : MediaBrowseRepository {
    override suspend fun recentlyPlayedAlbums(offset: Int, limit: Int) =
        listOrFallback(AlbumListType.Recent, offset, limit, browseDao::albumsByRecentlyPlayed)

    override suspend fun recentlyAddedAlbums(offset: Int, limit: Int) =
        listOrFallback(AlbumListType.Newest, offset, limit, browseDao::albumsByRecentlyAdded)

    override suspend fun starredSongs(offset: Int, limit: Int) = query(emptyList()) { serverId ->
        browseDao.starredSongs(serverId, limit, offset).map { it.asExternalModel() }
    }

    override suspend fun albums(offset: Int, limit: Int) = query(emptyList()) { serverId ->
        browseDao.albumsByName(serverId, limit, offset).map { it.asExternalModel() }
    }

    override suspend fun artists(offset: Int, limit: Int) = query(emptyList()) { serverId ->
        browseDao.artistsByName(serverId, limit, offset).map { it.asExternalModel() }
    }

    override suspend fun artistAlbums(artistId: String) = query(emptyList()) { serverId ->
        albumDao.observeByArtist(serverId, artistId).first().map { it.asExternalModel() }
    }

    override suspend fun genres() = query(emptyList()) { serverId ->
        genreDao.observeAll(serverId).first().map { it.asExternalModel() }
    }

    override suspend fun genreAlbums(genre: String, offset: Int, limit: Int) = query(emptyList()) { serverId ->
        browseDao.albumsByGenre(serverId, genre, limit, offset).map { it.asExternalModel() }
    }

    override suspend fun playlists() = query(emptyList()) { serverId ->
        playlistDao.observeAll(serverId).first().map { it.asExternalModel() }
    }

    override suspend fun downloadedSongs(offset: Int, limit: Int) = query(emptyList()) { serverId ->
        browseDao.downloadedSongs(serverId, limit, offset).map { it.asExternalModel() }
    }

    override suspend fun albumSongs(albumId: String) = query(emptyList()) { serverId ->
        songDao.observeByAlbum(serverId, albumId).first().map { it.asExternalModel() }
    }

    override suspend fun albumSongsForPlayback(albumId: String): List<Song> = try {
        albumRepository.songsForPlayback(albumId)
    } catch (_: RemoteException) {
        albumSongs(albumId)
    }

    override suspend fun playlistSongs(playlistId: String) = query(emptyList()) { serverId ->
        playlistDao.observeSongs(serverId, playlistId).first().map { it.asExternalModel() }
    }

    override suspend fun album(id: String) = query(null) { serverId ->
        albumDao.observe(serverId, id).first()?.asExternalModel()
    }

    override suspend fun artist(id: String) = query(null) { serverId ->
        artistDao.observe(serverId, id).first()?.asExternalModel()
    }

    override suspend fun playlist(id: String) = query(null) { serverId ->
        playlistDao.observe(serverId, id).first()?.asExternalModel()
    }

    override suspend fun song(id: String) = query(null) { serverId ->
        songDao.observe(serverId, id).first()?.asExternalModel()
    }

    override suspend fun refreshHomeLists() {
        albumRepository.refreshAlbumList(AlbumListType.Recent)
        albumRepository.refreshAlbumList(AlbumListType.Newest)
    }

    private suspend fun listOrFallback(
        type: AlbumListType,
        offset: Int,
        limit: Int,
        fallback: suspend (serverId: String, limit: Int, offset: Int) -> List<AlbumEntity>,
    ): List<Album> = query(emptyList()) { serverId ->
        val list = albumListDao.observeAlbums(serverId, type.apiValue).first()
        val rows = if (list.isNotEmpty()) list.drop(offset).take(limit) else fallback(serverId, limit, offset)
        rows.map { it.asExternalModel() }
    }

    private suspend fun <T> query(empty: T, block: suspend (serverId: String) -> T): T = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext empty
        block(serverId)
    }
}
