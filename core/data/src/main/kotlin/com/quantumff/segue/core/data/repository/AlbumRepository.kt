package com.quantumff.segue.core.data.repository

import androidx.paging.PagingData
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.AlbumListDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface AlbumRepository {
    fun albums(sort: AlbumSortOrder, filter: AlbumFilter = AlbumFilter()): Flow<PagingData<Album>>

    /** How many albums [filter] matches. */
    fun albumCount(filter: AlbumFilter = AlbumFilter()): Flow<Int>

    /** Release years in the library, newest first, for the year filter. */
    val albumYears: Flow<ImmutableList<Int>>

    fun album(id: String): Flow<Album?>

    /** In disc, then track order. */
    fun albumSongs(id: String): Flow<ImmutableList<Song>>

    /** A server-ordered list such as recently played, as last refreshed. For home sections. */
    fun albumList(type: AlbumListType): Flow<ImmutableList<Album>>

    val starredAlbums: Flow<ImmutableList<Album>>

    /** Fetches the album and its songs. An album deleted on the server is removed. Throws RemoteException. */
    suspend fun refreshAlbum(id: String)

    /**
     * The album's songs in disc and track order, for playing it from a card or menu: from Room, or
     * fetched first when the album's songs have not been synced yet. Throws RemoteException.
     */
    suspend fun songsForPlayback(id: String): ImmutableList<Song>

    /** Fetches the first [size] albums of [type]. Throws RemoteException. */
    suspend fun refreshAlbumList(type: AlbumListType, size: Int = DEFAULT_LIST_SIZE)

    companion object {
        const val DEFAULT_LIST_SIZE = 20
    }
}

internal class OfflineFirstAlbumRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val albumListDao: AlbumListDao,
    private val writer: LibraryWriter,
    private val transactions: TransactionRunner,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : AlbumRepository {
    override fun albums(sort: AlbumSortOrder, filter: AlbumFilter): Flow<PagingData<Album>> =
        clients.pagedForActiveServer(
            source = { serverId ->
                val starred = filter.starred
                val (genre, year) = filter.genre to filter.year
                val downloaded = filter.downloaded
                if (downloaded != null) {
                    when (sort) {
                        AlbumSortOrder.Name ->
                            albumDao.downloadFilteredPagingSourceByName(serverId, starred, genre, year, downloaded)

                        AlbumSortOrder.Artist ->
                            albumDao.downloadFilteredPagingSourceByArtist(serverId, starred, genre, year, downloaded)

                        AlbumSortOrder.RecentlyAdded -> albumDao.downloadFilteredPagingSourceByRecentlyAdded(
                            serverId,
                            starred,
                            genre,
                            year,
                            downloaded,
                        )

                        AlbumSortOrder.Year ->
                            albumDao.downloadFilteredPagingSourceByYear(serverId, starred, genre, year, downloaded)
                    }
                } else {
                    when (sort) {
                        AlbumSortOrder.Name -> albumDao.pagingSourceByName(serverId, starred, genre, year)

                        AlbumSortOrder.Artist -> albumDao.pagingSourceByArtist(serverId, starred, genre, year)

                        AlbumSortOrder.RecentlyAdded -> albumDao.pagingSourceByRecentlyAdded(
                            serverId,
                            starred,
                            genre,
                            year,
                        )

                        AlbumSortOrder.Year -> albumDao.pagingSourceByYear(serverId, starred, genre, year)
                    }
                }
            },
            mapper = AlbumEntity::asExternalModel,
        )

    override fun albumCount(filter: AlbumFilter): Flow<Int> = clients.forActiveServer(0) { serverId ->
        val downloaded = filter.downloaded
        if (downloaded != null) {
            albumDao.observeDownloadFilteredCount(serverId, filter.starred, filter.genre, filter.year, downloaded)
        } else {
            albumDao.observeCount(serverId, filter.starred, filter.genre, filter.year)
        }
    }

    override val albumYears: Flow<ImmutableList<Int>> =
        clients.listForActiveServer(albumDao::observeYears) { it }

    override fun album(id: String): Flow<Album?> =
        clients.forActiveServer(null) { serverId -> albumDao.observe(serverId, id).map { it?.asExternalModel() } }

    override fun albumSongs(id: String): Flow<ImmutableList<Song>> =
        clients.listForActiveServer({ serverId -> songDao.observeByAlbum(serverId, id) }, SongEntity::asExternalModel)

    override fun albumList(type: AlbumListType): Flow<ImmutableList<Album>> = clients.listForActiveServer({ serverId ->
        albumListDao.observeAlbums(serverId, type.apiValue)
    }, AlbumEntity::asExternalModel)

    override val starredAlbums: Flow<ImmutableList<Album>> =
        clients.listForActiveServer(albumDao::observeStarred, AlbumEntity::asExternalModel)

    override suspend fun refreshAlbum(id: String) = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val album = try {
            remote { clients.client(serverId).getAlbum(id) }
        } catch (e: RemoteException.NotFound) {
            albumDao.deleteByIds(serverId, listOf(id))
            throw e
        }
        writer.writeAlbums(serverId, listOf(album))
    }

    override suspend fun songsForPlayback(id: String): ImmutableList<Song> {
        val cached = albumSongs(id).first()
        val album = album(id).first()
        // A partial track list (only some songs synced through search or playlists) is refetched too.
        if (cached.isNotEmpty() && (album == null || cached.size >= album.songCount)) return cached
        refreshAlbum(id)
        return albumSongs(id).first()
    }

    override suspend fun refreshAlbumList(type: AlbumListType, size: Int) = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val albums = remote { clients.client(serverId).getAlbumList2(type, size = size, offset = 0) }
        transactions.run {
            writer.writeAlbumSummaries(serverId, albums)
            albumListDao.replace(serverId, type.apiValue, albums.map { it.id })
        }
    }
}
