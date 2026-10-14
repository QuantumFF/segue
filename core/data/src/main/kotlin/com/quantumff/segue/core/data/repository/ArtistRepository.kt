package com.quantumff.segue.core.data.repository

import androidx.paging.PagingData
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.network.model.NetworkChild
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ArtistRepository {
    fun artists(): Flow<PagingData<Artist>>

    fun artist(id: String): Flow<Artist?>

    /** Newest first, including albums that credit the artist only in OpenSubsonic `artists`. */
    fun artistAlbums(id: String): Flow<ImmutableList<Album>>

    val starredArtists: Flow<ImmutableList<Artist>>

    /** Fetches the artist and its album list. Throws RemoteException. */
    suspend fun refreshArtist(id: String)

    /**
     * The server's top songs for the artist, for one-off playback. The songs are also stored in
     * Room. Throws RemoteException.
     */
    suspend fun topSongs(artistId: String, count: Int = DEFAULT_SONG_COUNT): ImmutableList<Song>

    /** Songs similar to the artist (`getSimilarSongs2`), for an instant mix. Throws RemoteException. */
    suspend fun similarSongs(artistId: String, count: Int = DEFAULT_SONG_COUNT): ImmutableList<Song>

    companion object {
        const val DEFAULT_SONG_COUNT = 50
    }
}

internal class OfflineFirstArtistRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val capabilities: CachedServerCapabilitiesRepository,
    private val writer: LibraryWriter,
    private val transactions: TransactionRunner,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ArtistRepository {
    override fun artists(): Flow<PagingData<Artist>> =
        clients.pagedForActiveServer(artistDao::pagingSource, ArtistEntity::asExternalModel)

    override fun artist(id: String): Flow<Artist?> =
        clients.forActiveServer(null) { serverId -> artistDao.observe(serverId, id).map { it?.asExternalModel() } }

    override fun artistAlbums(id: String): Flow<ImmutableList<Album>> = clients.listForActiveServer({ serverId ->
        albumDao.observeByArtist(serverId, id)
    }, AlbumEntity::asExternalModel)

    override val starredArtists: Flow<ImmutableList<Artist>> =
        clients.listForActiveServer(artistDao::observeStarred, ArtistEntity::asExternalModel)

    override suspend fun refreshArtist(id: String) = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val artist = remote { clients.client(serverId).getArtist(id) }
        transactions.run {
            artistDao.upsertAll(listOf(artist.asEntity(serverId)))
            writer.writeAlbumSummaries(serverId, artist.album)
        }
    }

    override suspend fun topSongs(artistId: String, count: Int): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val name = artistDao.observe(serverId, artistId).first()?.name ?: return@withContext emptySongs()
        val byId = artistId.takeIf { capabilities.forServer(serverId).supportsTopSongsByArtistId }
        storeAndMap(serverId, remote { clients.client(serverId).getTopSongs(name, byId, count) })
    }

    override suspend fun similarSongs(artistId: String, count: Int): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        storeAndMap(serverId, remote { clients.client(serverId).getSimilarSongs2(artistId, count) })
    }

    private suspend fun storeAndMap(serverId: String, songs: List<NetworkChild>): ImmutableList<Song> {
        writer.writeSongs(serverId, songs)
        return songs.filterNot { it.isDir }.map { it.asEntity(serverId).asExternalModel() }.toImmutableList()
    }

    private fun emptySongs(): ImmutableList<Song> = emptyList<Song>().toImmutableList()
}
