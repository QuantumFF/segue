package com.quantumff.segue.core.data.repository

import androidx.paging.PagingData
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.GenreDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.GenreEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Songs and genres of the synced library. Albums and artists have their own repositories. */
interface LibraryRepository {
    val genres: Flow<ImmutableList<Genre>>

    fun songs(): Flow<PagingData<Song>>

    val songCount: Flow<Int>

    /** Songs with a finished download, by name. For offline mode. */
    fun downloadedSongs(): Flow<PagingData<Song>>

    val downloadedSongCount: Flow<Int>

    fun songsByGenre(genre: String): Flow<PagingData<Song>>

    fun song(id: String): Flow<Song?>

    val starredSongs: Flow<ImmutableList<Song>>

    /** Songs for [ids] in that order, duplicates kept. IDs not in the local library are skipped. */
    suspend fun songsByIds(ids: List<String>): ImmutableList<Song>
}

internal class OfflineFirstLibraryRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val songDao: SongDao,
    private val genreDao: GenreDao,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {
    override val genres: Flow<ImmutableList<Genre>> =
        clients.listForActiveServer(genreDao::observeAll, GenreEntity::asExternalModel)

    override fun songs(): Flow<PagingData<Song>> =
        clients.pagedForActiveServer(songDao::pagingSource, SongEntity::asExternalModel)

    override val songCount: Flow<Int> = clients.forActiveServer(0, songDao::observeCount)

    override fun downloadedSongs(): Flow<PagingData<Song>> =
        clients.pagedForActiveServer(songDao::pagingSourceDownloaded, SongEntity::asExternalModel)

    override val downloadedSongCount: Flow<Int> = clients.forActiveServer(0, songDao::observeDownloadedCount)

    override fun songsByGenre(genre: String): Flow<PagingData<Song>> = clients.pagedForActiveServer({ serverId ->
        songDao.pagingSourceByGenre(serverId, genre)
    }, SongEntity::asExternalModel)

    override fun song(id: String): Flow<Song?> =
        clients.forActiveServer(null) { serverId -> songDao.observe(serverId, id).map { it?.asExternalModel() } }

    override val starredSongs: Flow<ImmutableList<Song>> =
        clients.listForActiveServer(songDao::observeStarred, SongEntity::asExternalModel)

    override suspend fun songsByIds(ids: List<String>): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext persistentListOf()
        val byId = songDao.getByIds(serverId, ids).associateBy { it.id }
        ids.mapNotNull { byId[it]?.asExternalModel() }.toImmutableList()
    }
}
