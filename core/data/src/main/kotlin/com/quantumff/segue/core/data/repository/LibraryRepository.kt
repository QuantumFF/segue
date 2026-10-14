package com.quantumff.segue.core.data.repository

import androidx.paging.PagingData
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.dao.GenreDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.GenreEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Songs and genres of the synced library. Albums and artists have their own repositories. */
interface LibraryRepository {
    val genres: Flow<ImmutableList<Genre>>

    fun songs(): Flow<PagingData<Song>>

    fun songsByGenre(genre: String): Flow<PagingData<Song>>

    fun song(id: String): Flow<Song?>

    val starredSongs: Flow<ImmutableList<Song>>

    /** Random songs from the server, for "shuffle all". Also stored in Room. Throws RemoteException. */
    suspend fun randomSongs(size: Int = DEFAULT_RANDOM_SIZE): ImmutableList<Song>

    companion object {
        const val DEFAULT_RANDOM_SIZE = 100
    }
}

internal class OfflineFirstLibraryRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val songDao: SongDao,
    private val genreDao: GenreDao,
    private val writer: LibraryWriter,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {
    override val genres: Flow<ImmutableList<Genre>> =
        clients.listForActiveServer(genreDao::observeAll, GenreEntity::asExternalModel)

    override fun songs(): Flow<PagingData<Song>> =
        clients.pagedForActiveServer(songDao::pagingSource, SongEntity::asExternalModel)

    override fun songsByGenre(genre: String): Flow<PagingData<Song>> = clients.pagedForActiveServer({ serverId ->
        songDao.pagingSourceByGenre(serverId, genre)
    }, SongEntity::asExternalModel)

    override fun song(id: String): Flow<Song?> =
        clients.forActiveServer(null) { serverId -> songDao.observe(serverId, id).map { it?.asExternalModel() } }

    override val starredSongs: Flow<ImmutableList<Song>> =
        clients.listForActiveServer(songDao::observeStarred, SongEntity::asExternalModel)

    override suspend fun randomSongs(size: Int): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val songs = remote { clients.client(serverId).getRandomSongs(size) }
        writer.writeSongs(serverId, songs)
        songs.filterNot { it.isDir }.map { it.asEntity(serverId).asExternalModel() }.toImmutableList()
    }
}
