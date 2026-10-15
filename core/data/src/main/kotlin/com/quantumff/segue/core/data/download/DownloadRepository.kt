package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.forActiveServer
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.DownloadDao
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PinEntity
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.model.StreamingQuality
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Something to pin: what it is, plus the name and art to show if it later leaves the library. */
data class PinTarget(val kind: PinKind, val id: String, val name: String, val coverArtId: String?)

/**
 * Offline downloads. Pin an album, playlist, artist or song and its songs are downloaded; unpin
 * it and they are deleted unless another pin still covers them. Room holds the pins and the
 * per-song state; a WorkManager worker does the downloading.
 */
interface DownloadRepository {
    val preferences: Flow<DownloadPreferences>

    /** The active server's pins with their progress, and the storage used. */
    val overview: Flow<DownloadOverview>

    /** Download state per song ID on the active server, for row badges. */
    val songDownloads: Flow<ImmutableMap<String, SongDownload>>

    /** IDs of the active server's songs that are fully downloaded. */
    val downloadedSongIds: Flow<ImmutableSet<String>>

    /** The pin's status, or null when it is not pinned. */
    fun pinStatus(kind: PinKind, id: String): Flow<DownloadStatus?>

    suspend fun pin(target: PinTarget)

    suspend fun unpin(kind: PinKind, id: String)

    /** Holds the pin's unfinished songs; their partial files are kept. */
    suspend fun pause(kind: PinKind, id: String)

    /** Puts paused and failed songs of the pin back in the queue. */
    suspend fun resume(kind: PinKind, id: String)

    /** Unpins everything on the active server and deletes its files. */
    suspend fun removeAll()

    /** The downloaded file for [songId] on the active server, or null. For the player. */
    suspend fun localFile(songId: String): File?

    suspend fun setQuality(quality: StreamingQuality)

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setChargingOnly(enabled: Boolean)

    suspend fun setForceOffline(enabled: Boolean)
}

@Singleton
@Suppress("LongParameterList", "TooManyFunctions")
internal class OfflineDownloadRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val dao: DownloadDao,
    private val files: DownloadFiles,
    private val progress: DownloadProgressTracker,
    private val scheduler: DownloadScheduler,
    private val preferenceSource: DownloadPreferencesDataSource,
    private val albums: AlbumRepository,
    private val artists: ArtistRepository,
    private val playlists: PlaylistRepository,
    private val clock: Clock,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : DownloadRepository {
    private val reconcileMutex = Mutex()

    override val preferences: Flow<DownloadPreferences> = preferenceSource.preferences

    override val overview: Flow<DownloadOverview> = clients.forActiveServer(DownloadOverview()) { serverId ->
        // Free space is read when the rows change (a song finished or was removed), not on every
        // progress tick.
        val rowsWithFreeSpace = dao.observeAll(serverId).map { rows -> rows to files.freeBytes() }
        combine(
            dao.observePins(serverId),
            dao.observePinMembership(serverId),
            rowsWithFreeSpace,
            progress.progress,
        ) { pins, membership, (rows, freeBytes), live ->
            val liveForServer = live.forServer(serverId)
            val statuses = DownloadPlanner.pinStatuses(pins, membership, rows.associateBy { it.songId }, liveForServer)
            DownloadOverview(
                pins = statuses.toImmutableList(),
                usedBytes = rows.sumOf { row -> liveForServer[row.songId]?.bytes ?: row.bytesDownloaded },
                freeBytes = freeBytes,
            )
        }.conflate().flowOn(ioDispatcher)
    }

    override val songDownloads: Flow<ImmutableMap<String, SongDownload>> =
        clients.forActiveServer(persistentMapOf()) { serverId ->
            combine(dao.observeAll(serverId), progress.progress) { rows, live ->
                val liveForServer = live.forServer(serverId)
                rows.associate { row ->
                    val partial = liveForServer[row.songId]
                    val fraction = partial?.total?.takeIf { it > 0 }?.let { partial.bytes.toFloat() / it } ?: 0f
                    row.songId to SongDownload(row.songId, row.state, fraction.coerceIn(0f, 1f))
                }.toImmutableMap()
            }.distinctUntilChanged().conflate()
        }

    override val downloadedSongIds: Flow<ImmutableSet<String>> =
        clients.forActiveServer(persistentSetOf()) { serverId ->
            dao.observeCompletedSongIds(serverId).map { it.toImmutableSet() }.distinctUntilChanged()
        }

    override fun pinStatus(kind: PinKind, id: String): Flow<DownloadStatus?> =
        overview.map { overview -> overview.pins.firstOrNull { it.kind == kind && it.id == id }?.status }
            .distinctUntilChanged()

    override suspend fun pin(target: PinTarget) = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: throw RemoteException.NotSignedIn()
        dao.upsertPin(PinEntity(serverId, target.kind, target.id, target.name, target.coverArtId, clock.nowMillis()))
        // Best effort: make sure the songs are in the library so the pin covers all of them.
        try {
            when (target.kind) {
                PinKind.Album -> albums.songsForPlayback(target.id)
                PinKind.Playlist -> playlists.refreshPlaylist(target.id)
                PinKind.Artist -> artists.artistAlbums(target.id).first().forEach { albums.songsForPlayback(it.id) }
                PinKind.Song -> Unit
            }
        } catch (_: RemoteException) {
            // Offline: the pin covers what is synced, and the sync reconciles it later.
        }
        reconcile(serverId)
    }

    override suspend fun unpin(kind: PinKind, id: String) = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext
        dao.deletePin(serverId, kind, id)
        reconcile(serverId)
    }

    override suspend fun pause(kind: PinKind, id: String) = transition(
        kind,
        id,
        from = listOf(DownloadState.Queued, DownloadState.Downloading),
        to = DownloadState.Paused,
    )

    override suspend fun resume(kind: PinKind, id: String) {
        transition(kind, id, from = listOf(DownloadState.Paused, DownloadState.Failed), to = DownloadState.Queued)
        scheduler.schedule(preferenceSource.preferences.first(), restart = false)
    }

    override suspend fun removeAll() = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext
        dao.observePins(serverId).first().forEach { dao.deletePin(serverId, it.kind, it.itemId) }
        reconcile(serverId)
    }

    override suspend fun localFile(songId: String): File? = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext null
        val row = dao.get(serverId, songId)?.takeIf { it.state == DownloadState.Completed } ?: return@withContext null
        row.filePath?.let(::File)?.takeIf { it.isFile }
    }

    override suspend fun setQuality(quality: StreamingQuality) = preferenceSource.setQuality(quality)

    override suspend fun setWifiOnly(enabled: Boolean) {
        preferenceSource.setWifiOnly(enabled)
        reschedule()
    }

    override suspend fun setChargingOnly(enabled: Boolean) {
        preferenceSource.setChargingOnly(enabled)
        reschedule()
    }

    override suspend fun setForceOffline(enabled: Boolean) = preferenceSource.setForceOffline(enabled)

    /**
     * Makes the server's download rows match its pins: queues songs a pin covers that have no row,
     * and deletes rows and files of songs no pin covers any more.
     */
    suspend fun reconcile(serverId: String) = withContext(ioDispatcher) {
        reconcileMutex.withLock {
            val desired = DownloadPlanner.desiredSongs(dao.pinMembership(serverId))
            val existing = dao.getAll(serverId).associateBy { it.songId }
            val plan = DownloadPlanner.plan(desired, existing.keys)
            if (plan.toRemove.isNotEmpty()) {
                // Rows first, so the player and the worker stop using the files before they go.
                dao.delete(serverId, plan.toRemove)
                plan.toRemove.forEach { songId ->
                    files.delete(existing[songId]?.filePath)
                    files.deleteSong(serverId, songId)
                }
            }
            if (plan.toQueue.isNotEmpty()) {
                val now = clock.nowMillis()
                dao.insertAll(plan.toQueue.map { DownloadEntity(serverId, it, DownloadState.Queued, updatedAt = now) })
                scheduler.schedule(preferenceSource.preferences.first(), restart = false)
            }
        }
    }

    private suspend fun transition(kind: PinKind, id: String, from: List<DownloadState>, to: DownloadState) =
        withContext(ioDispatcher) {
            val serverId = clients.activeServerId.first() ?: return@withContext
            val songIds = dao.pinMembership(serverId).filter { it.kind == kind && it.itemId == id }.map { it.songId }
            dao.transition(serverId, songIds.distinct(), from, to, clock.nowMillis())
        }

    private suspend fun reschedule() {
        val hasWork = dao.queuedCount() + dao.downloadingCount() > 0
        if (hasWork) scheduler.schedule(preferenceSource.preferences.first(), restart = true)
    }

    private fun Map<String, LiveProgress>.forServer(serverId: String): Map<String, LiveProgress> {
        val prefix = "$serverId/"
        return entries.filter { it.key.startsWith(prefix) }.associate { it.key.removePrefix(prefix) to it.value }
    }
}
