package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.FtsQuery
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.dao.SearchDao
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.network.SubsonicException
import com.quantumff.segue.core.network.model.NetworkSearchResult3
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface SearchRepository {
    /**
     * Emits local full-text matches at once, then the server's `search3` results merged in front
     * (server results are also stored in Room). If the server search fails, the local results stand.
     * Debounce typing before calling.
     */
    fun search(query: String): Flow<SearchResult>
}

internal class OfflineFirstSearchRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val searchDao: SearchDao,
    private val writer: LibraryWriter,
    private val artistDao: ArtistDao,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {
    override fun search(query: String): Flow<SearchResult> = flow {
        val serverId = clients.activeServerId.first()
        val match = FtsQuery.prefixMatch(query)
        if (serverId == null || match == null) {
            emit(SearchResult.Empty)
            return@flow
        }
        val local = localSearch(serverId, match)
        emit(local)

        val client = clients.client(serverId)
        val remote = try {
            client.search3(query.trim(), artistCount = ARTIST_LIMIT, albumCount = ALBUM_LIMIT, songCount = SONG_LIMIT)
        } catch (_: SubsonicException) {
            return@flow
        }
        store(serverId, remote)
        emit(merge(serverId, remote, local))
    }.flowOn(ioDispatcher)

    private suspend fun localSearch(serverId: String, match: String) = SearchResult(
        artists = searchDao.artists(serverId, match, ARTIST_LIMIT).map { it.asExternalModel() }.toImmutableList(),
        albums = searchDao.albums(serverId, match, ALBUM_LIMIT).map { it.asExternalModel() }.toImmutableList(),
        songs = searchDao.songs(serverId, match, SONG_LIMIT).map { it.asExternalModel() }.toImmutableList(),
        includesServerResults = false,
    )

    private suspend fun store(serverId: String, remote: NetworkSearchResult3) {
        artistDao.upsertAll(remote.artist.map { it.asEntity(serverId) })
        writer.writeAlbumSummaries(serverId, remote.album)
        writer.writeSongs(serverId, remote.song)
    }

    /** Server results first, in the server's relevance order, then local-only matches. */
    private fun merge(serverId: String, remote: NetworkSearchResult3, local: SearchResult) = SearchResult(
        artists = (remote.artist.map { it.asEntity(serverId).asExternalModel() } + local.artists)
            .distinctBy { it.id }.toImmutableList(),
        albums = (remote.album.map { it.asEntity(serverId).asExternalModel() } + local.albums)
            .distinctBy { it.id }.toImmutableList(),
        songs = (remote.song.filterNot { it.isDir }.map { it.asEntity(serverId).asExternalModel() } + local.songs)
            .distinctBy { it.id }.toImmutableList(),
        includesServerResults = true,
    )

    private companion object {
        const val ARTIST_LIMIT = 20
        const val ALBUM_LIMIT = 20
        const val SONG_LIMIT = 50
    }
}
