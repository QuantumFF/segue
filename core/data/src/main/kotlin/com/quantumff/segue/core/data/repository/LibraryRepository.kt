package com.quantumff.segue.core.data.repository

import androidx.paging.PagingData
import androidx.paging.PagingSource
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
import com.quantumff.segue.core.model.SongFilter
import com.quantumff.segue.core.model.SongSortOrder
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

    /** The Songs tab: songs in [sort] order, narrowed by [filter]. */
    fun songs(sort: SongSortOrder, filter: SongFilter = SongFilter()): Flow<PagingData<Song>>

    /** How many songs [filter] keeps. */
    fun songCount(filter: SongFilter = SongFilter()): Flow<Int>

    /** Every song with a finished download, in no particular order: what Shuffle all plays offline. */
    suspend fun allDownloadedSongs(): ImmutableList<Song>

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

    override fun songs(sort: SongSortOrder, filter: SongFilter): Flow<PagingData<Song>> = clients.pagedForActiveServer(
        source = { serverId -> songDao.songSource(serverId, sort, filter) },
        mapper = SongEntity::asExternalModel,
    )

    private fun SongDao.songSource(
        serverId: String,
        sort: SongSortOrder,
        filter: SongFilter,
    ): PagingSource<Int, SongEntity> {
        val starred = filter.starred
        val downloaded = filter.downloaded ?: return when (sort) {
            SongSortOrder.Title -> pagingSourceByTitle(serverId, starred)
            SongSortOrder.Artist -> pagingSourceByArtist(serverId, starred)
            SongSortOrder.Album -> pagingSourceByAlbum(serverId, starred)
            SongSortOrder.RecentlyAdded -> pagingSourceByRecentlyAdded(serverId, starred)
            SongSortOrder.Year -> pagingSourceByYear(serverId, starred)
            SongSortOrder.PlayCount -> pagingSourceByPlayCount(serverId, starred)
        }
        return when (sort) {
            SongSortOrder.Title -> downloadFilteredPagingSourceByTitle(serverId, starred, downloaded)
            SongSortOrder.Artist -> downloadFilteredPagingSourceByArtist(serverId, starred, downloaded)
            SongSortOrder.Album -> downloadFilteredPagingSourceByAlbum(serverId, starred, downloaded)
            SongSortOrder.RecentlyAdded -> downloadFilteredPagingSourceByRecentlyAdded(serverId, starred, downloaded)
            SongSortOrder.Year -> downloadFilteredPagingSourceByYear(serverId, starred, downloaded)
            SongSortOrder.PlayCount -> downloadFilteredPagingSourceByPlayCount(serverId, starred, downloaded)
        }
    }

    override fun songCount(filter: SongFilter): Flow<Int> = clients.forActiveServer(0) { serverId ->
        val downloaded = filter.downloaded
        if (downloaded != null) {
            songDao.observeDownloadFilteredCount(serverId, filter.starred, downloaded)
        } else {
            songDao.observeCount(serverId, filter.starred)
        }
    }

    override suspend fun allDownloadedSongs(): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext persistentListOf()
        songDao.getDownloaded(serverId).map { it.asExternalModel() }.toImmutableList()
    }

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
