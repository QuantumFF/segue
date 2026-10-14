package com.quantumff.segue.core.data.sync

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.dao.GenreDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.SyncMetadataDao
import com.quantumff.segue.core.database.model.SyncMetadataEntity
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.network.SubsonicClient
import com.quantumff.segue.core.network.SubsonicDates
import com.quantumff.segue.core.network.SubsonicException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal typealias SyncProgressListener = suspend (SyncProgress.Running) -> Unit

/**
 * Mirrors a server's library into Room.
 *
 * - **Full** (first sync, when forced, or when the last full sync is older than
 *   [FULL_SYNC_MAX_AGE_MILLIS]): every artist, every album page of `alphabeticalByName`, every
 *   album's songs, and every playlist. Rows the server no longer has are deleted.
 * - **Incremental**: artists only if `getArtists` reports a new `lastModified`; albums from the
 *   `newest` list until a page reaches albums already stored; playlists whose `changed` moved.
 *
 * Network calls run at most [ALBUM_FETCH_CONCURRENCY] at a time. Each batch of results is written
 * in one transaction. Albums edited in place on the server are picked up by the next full sync.
 */
internal class LibrarySynchronizer @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val genreDao: GenreDao,
    private val playlistDao: PlaylistDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val writer: LibraryWriter,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /** Throws [SubsonicException] when the server cannot be reached or refuses a request. */
    suspend fun sync(serverId: String, fullRefresh: Boolean = false, onProgress: SyncProgressListener = {}) =
        withContext(ioDispatcher) {
            val client = clients.client(serverId)
            val metadata = syncMetadataDao.get(serverId) ?: SyncMetadataEntity(serverId)
            val startedAt = clock.nowMillis()
            val lastFull = metadata.lastFullSyncAt
            val full = fullRefresh || lastFull == null || startedAt - lastFull > FULL_SYNC_MAX_AGE_MILLIS

            val artistsLastModified = syncArtists(client, serverId, metadata, full, onProgress)
            syncGenres(client, serverId, onProgress)
            if (full) {
                fullAlbumSync(
                    client,
                    serverId,
                    onProgress,
                )
            } else {
                incrementalAlbumSync(client, serverId, onProgress)
            }
            syncPlaylists(client, serverId, onProgress)

            syncMetadataDao.upsert(
                metadata.copy(
                    artistsLastModified = artistsLastModified,
                    lastSyncAt = startedAt,
                    lastFullSyncAt = if (full) startedAt else metadata.lastFullSyncAt,
                ),
            )
        }

    /** Returns the `lastModified` to remember. */
    private suspend fun syncArtists(
        client: SubsonicClient,
        serverId: String,
        metadata: SyncMetadataEntity,
        full: Boolean,
        onProgress: SyncProgressListener,
    ): Long? {
        onProgress(SyncProgress.Running(SyncPhase.Artists, completed = 0, total = 1))
        val known = metadata.artistsLastModified.takeUnless { full }
        val index = client.getArtists(ifModifiedSince = known)
        val artists = index.index.flatMap { it.artist }
        // Navidrome ignores ifModifiedSince, so compare lastModified as well. A server that honors it
        // answers an unchanged library with an empty index, which must not delete everything.
        val changed = full || (index.lastModified != known && artists.isNotEmpty())
        if (changed) {
            val entities = artists.map { it.asEntity(serverId) }
            val remoteIds = entities.mapTo(HashSet()) { it.id }
            transactions.run {
                artistDao.upsertAll(entities)
                artistDao.deleteByIds(serverId, artistDao.ids(serverId).filterNot { it in remoteIds })
            }
        }
        onProgress(SyncProgress.Running(SyncPhase.Artists, completed = 1, total = 1))
        return if (changed) index.lastModified else metadata.artistsLastModified
    }

    private suspend fun syncGenres(client: SubsonicClient, serverId: String, onProgress: SyncProgressListener) {
        onProgress(SyncProgress.Running(SyncPhase.Genres, completed = 0, total = 1))
        genreDao.replaceAll(serverId, client.getGenres().map { it.asEntity(serverId) })
        onProgress(SyncProgress.Running(SyncPhase.Genres, completed = 1, total = 1))
    }

    private suspend fun fullAlbumSync(client: SubsonicClient, serverId: String, onProgress: SyncProgressListener) {
        val remoteIds = LinkedHashSet<String>()
        var offset = 0
        do {
            onProgress(SyncProgress.Running(SyncPhase.Albums, completed = remoteIds.size, total = 0))
            val page = client.getAlbumList2(AlbumListType.AlphabeticalByName, size = PAGE_SIZE, offset = offset)
            writer.writeAlbumSummaries(serverId, page)
            page.mapTo(remoteIds) { it.id }
            offset += PAGE_SIZE
        } while (page.size == PAGE_SIZE)
        onProgress(SyncProgress.Running(SyncPhase.Albums, completed = remoteIds.size, total = remoteIds.size))

        albumDao.deleteByIds(serverId, albumDao.ids(serverId).filterNot { it in remoteIds })
        fetchAlbumDetails(client, serverId, remoteIds.toList(), onProgress)
    }

    private suspend fun incrementalAlbumSync(
        client: SubsonicClient,
        serverId: String,
        onProgress: SyncProgressListener,
    ) {
        val newIds = mutableListOf<String>()
        var offset = 0
        while (true) {
            onProgress(SyncProgress.Running(SyncPhase.Albums, completed = newIds.size, total = 0))
            val page = client.getAlbumList2(AlbumListType.Newest, size = NEWEST_PAGE_SIZE, offset = offset)
            val known = albumDao.knownIds(serverId, page.map { it.id }).toSet()
            page.filterNot { it.id in known }.mapTo(newIds) { it.id }
            if (known.isNotEmpty() || page.size < NEWEST_PAGE_SIZE) break
            offset += NEWEST_PAGE_SIZE
        }
        onProgress(SyncProgress.Running(SyncPhase.Albums, completed = newIds.size, total = newIds.size))
        fetchAlbumDetails(client, serverId, newIds, onProgress)
    }

    /** `getAlbum` for each ID, a few at a time, writing each batch in one transaction. */
    private suspend fun fetchAlbumDetails(
        client: SubsonicClient,
        serverId: String,
        albumIds: List<String>,
        onProgress: SyncProgressListener,
    ) {
        onProgress(SyncProgress.Running(SyncPhase.Songs, completed = 0, total = albumIds.size))
        fetchInBatches(albumIds, fetch = { id -> notFoundAsNull { client.getAlbum(id) } }) { results, done ->
            writer.writeAlbums(
                serverId,
                albums = results.values.filterNotNull(),
                // Listed, then deleted before it was fetched.
                missing = results.filterValues { it == null }.keys,
            )
            onProgress(SyncProgress.Running(SyncPhase.Songs, completed = done, total = albumIds.size))
        }
    }

    /** Playlists only: fetches those whose `changed` moved and deletes those gone from the server. */
    suspend fun syncPlaylists(serverId: String) = withContext(ioDispatcher) {
        syncPlaylists(clients.client(serverId), serverId, onProgress = {})
    }

    private suspend fun syncPlaylists(client: SubsonicClient, serverId: String, onProgress: SyncProgressListener) {
        onProgress(SyncProgress.Running(SyncPhase.Playlists, completed = 0, total = 0))
        val remote = client.getPlaylists()
        val local = playlistDao.getAll(serverId).associateBy { it.id }
        val changedIds = remote.filter { playlist ->
            val changedAt = SubsonicDates.toEpochMillis(playlist.changed)
            // Without a timestamp there is no way to tell, so refetch.
            changedAt == null || local[playlist.id]?.changedAt != changedAt
        }.map { it.id }
        fetchInBatches(changedIds, fetch = { id -> notFoundAsNull { client.getPlaylist(id) } }) { results, done ->
            writer.writePlaylists(serverId, results.values.filterNotNull())
            onProgress(SyncProgress.Running(SyncPhase.Playlists, completed = done, total = changedIds.size))
        }
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        playlistDao.deleteByIds(serverId, local.keys.filterNot { it in remoteIds })
        onProgress(SyncProgress.Running(SyncPhase.Playlists, completed = changedIds.size, total = changedIds.size))
    }

    /**
     * Fetches [ids] at most [ALBUM_FETCH_CONCURRENCY] at a time, handing each batch of
     * [WRITE_BATCH_SIZE] results to [write] with the running count of IDs done.
     */
    private suspend fun <T> fetchInBatches(
        ids: List<String>,
        fetch: suspend (String) -> T,
        write: suspend (results: Map<String, T>, done: Int) -> Unit,
    ) {
        val permits = Semaphore(ALBUM_FETCH_CONCURRENCY)
        var done = 0
        ids.chunked(WRITE_BATCH_SIZE).forEach { batch ->
            val results = coroutineScope {
                batch.map { id -> async { permits.withPermit { id to fetch(id) } } }.awaitAll()
            }
            done += batch.size
            write(results.toMap(), done)
        }
    }

    /** Null when the item was deleted on the server after it was listed. */
    private suspend fun <T : Any> notFoundAsNull(fetch: suspend () -> T): T? = try {
        fetch()
    } catch (_: SubsonicException.NotFound) {
        null
    }

    companion object {
        /** Navidrome's maximum `getAlbumList2` size. */
        const val PAGE_SIZE = 500
        const val NEWEST_PAGE_SIZE = 50
        const val ALBUM_FETCH_CONCURRENCY = 4
        const val WRITE_BATCH_SIZE = 50
        const val FULL_SYNC_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
