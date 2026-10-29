package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.common.ApplicationScope
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
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.model.StorageLimit
import com.quantumff.segue.core.model.StreamingQuality
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Something to pin: what it is, plus the name and art to show if it later leaves the library. */
data class PinTarget(val kind: PinKind, val id: String, val name: String, val coverArtId: String?) {
    companion object {
        fun of(album: Album) = PinTarget(PinKind.Album, album.id, album.name, album.coverArtId)

        fun of(song: Song) = PinTarget(PinKind.Song, song.id, song.title, song.coverArtId)

        fun of(artist: Artist) = PinTarget(PinKind.Artist, artist.id, artist.name, artist.coverArtId)

        fun of(playlist: Playlist) = PinTarget(PinKind.Playlist, playlist.id, playlist.name, playlist.coverArtId)
    }
}

/**
 * A download removal that Undo can still take back ([DownloadRepository.unpinUndoably]). Call one of
 * [commit] or [undo]; only the first call counts. Both return at once and finish in the background,
 * in the job they return, so they can be called after the caller's screen has gone.
 */
interface PendingRemoval {
    /** Deletes the files of the songs no pin covers any more. */
    fun commit(): Job

    /** Pins it again; the files are still there. */
    fun undo(): Job
}

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

    /** The active server's downloaded songs, with the albums and artists they make available offline. */
    val downloadedLibrary: Flow<DownloadedLibrary>

    /** The pin's status, or null when it is not pinned. */
    fun pinStatus(kind: PinKind, id: String): Flow<DownloadStatus?>

    suspend fun pin(target: PinTarget)

    suspend fun unpin(kind: PinKind, id: String)

    /**
     * Unpins at once, like [unpin], but keeps the songs it covered until [PendingRemoval.commit],
     * even through the cleanup that follows pin changes, so [PendingRemoval.undo] can pin it again
     * without downloading them anew. Null when it is not pinned.
     */
    suspend fun unpinUndoably(kind: PinKind, id: String): PendingRemoval?

    /** Holds the pin's unfinished songs; their partial files are kept. */
    suspend fun pause(kind: PinKind, id: String)

    /** Puts paused and failed songs of the pin back in the queue. */
    suspend fun resume(kind: PinKind, id: String)

    /** Unpins everything on the active server and deletes its files. */
    suspend fun removeAll()

    /** Holds every unfinished download on the active server (the notification's "Pause all"). */
    suspend fun pauseAll()

    /**
     * Cancels every unfinished download on the active server (the notification's "Cancel"): unpins
     * each pin that is not fully downloaded, like the Downloads screen's cancel. Finished pins stay.
     */
    suspend fun cancelAll()

    /** The downloaded file for [songId] on the active server, or null. For the player. */
    suspend fun localFile(songId: String): File?

    suspend fun setQuality(quality: StreamingQuality)

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setChargingOnly(enabled: Boolean)

    suspend fun setForceOffline(enabled: Boolean)

    /** Raising or removing the limit lets waiting downloads start. */
    suspend fun setStorageLimit(limit: StorageLimit)
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
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : DownloadRepository {
    private val reconcileMutex = Mutex()

    /** Removals waiting on their Undo, whose songs [reconcile] keeps. Guarded by [reconcileMutex]. */
    private val held = mutableSetOf<HeldRemoval>()

    override val preferences: Flow<DownloadPreferences> = preferenceSource.preferences

    override val overview: Flow<DownloadOverview> = clients.forActiveServer(DownloadOverview()) { serverId ->
        // Free space is read when the rows change (a song finished or was removed), not on every
        // progress tick.
        val rowsWithFreeSpace = dao.observeAll(serverId).map { rows -> rows to files.freeBytes() }
        val pinsAndLimit = combine(
            dao.observePins(serverId),
            preferenceSource.preferences.map { it.storageLimit }.distinctUntilChanged(),
            dao.observeTotalBytes(),
        ) { pins, limit, deviceBytes -> Triple(pins, limit, limit.isReachedBy(deviceBytes)) }
        combine(
            pinsAndLimit,
            dao.observePinMembership(serverId),
            rowsWithFreeSpace,
            progress.progress,
        ) { (pins, limit, limitReached), membership, (rows, freeBytes), live ->
            val liveForServer = live.forServer(serverId)
            val statuses = DownloadPlanner.pinStatuses(pins, membership, rows.associateBy { it.songId }, liveForServer)
            DownloadOverview(
                pins = statuses.toImmutableList(),
                usedBytes = rows.sumOf { row -> liveForServer[row.songId]?.bytes ?: row.bytesDownloaded },
                freeBytes = freeBytes,
                storageLimit = limit,
                limitReached = limitReached,
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

    override val downloadedLibrary: Flow<DownloadedLibrary> =
        clients.forActiveServer(DownloadedLibrary()) { serverId ->
            combine(
                dao.observeCompletedSongIds(serverId),
                dao.observeDownloadedAlbumIds(serverId),
                dao.observeDownloadedArtistIds(serverId),
            ) { songs, albums, artists -> DownloadedLibrary(songs.toSet(), albums.toSet(), artists.toSet()) }
                .distinctUntilChanged()
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

    override suspend fun unpinUndoably(kind: PinKind, id: String): PendingRemoval? = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext null
        val pin = dao.observePins(serverId).first().firstOrNull { it.kind == kind && it.itemId == id }
            ?: return@withContext null
        reconcileMutex.withLock {
            val songs = dao.pinMembership(serverId).filter { it.kind == kind && it.itemId == id }.map { it.songId }
            dao.deletePin(serverId, kind, id)
            // Songs only this pin wanted stop downloading meanwhile, as if they were gone.
            val stillWanted = DownloadPlanner.desiredSongs(dao.pinMembership(serverId))
            val unfinished = dao.getAll(serverId)
                .filter { it.songId in songs && it.songId !in stillWanted && it.state in RUNNING }
                .map { it.songId }
            dao.transition(serverId, unfinished, RUNNING, DownloadState.Paused, clock.nowMillis())
            HeldRemoval(pin, songs.toSet(), unfinished).also { held += it }
        }
    }

    override suspend fun pause(kind: PinKind, id: String) = transition(
        kind,
        id,
        from = RUNNING,
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

    override suspend fun pauseAll() = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext
        dao.pauseUnfinished(serverId, clock.nowMillis())
    }

    override suspend fun cancelAll() = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext
        val rows = dao.getAll(serverId).associateBy { it.songId }
        DownloadPlanner.pinStatuses(dao.observePins(serverId).first(), dao.pinMembership(serverId), rows, emptyMap())
            .filterNot { it.isComplete }
            .forEach { dao.deletePin(serverId, it.kind, it.id) }
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

    override suspend fun setStorageLimit(limit: StorageLimit) = withContext(ioDispatcher) {
        preferenceSource.setStorageLimit(limit)
        // Waiting songs may start now; running ones carry on (no restart).
        if (dao.queuedCount() > 0) scheduler.schedule(preferenceSource.preferences.first(), restart = false)
    }

    /**
     * Makes the server's download rows match its pins: queues songs a pin covers that have no row,
     * and deletes rows and files of songs no pin covers any more, except those a removal still
     * waiting on its Undo holds back ([unpinUndoably]).
     */
    suspend fun reconcile(serverId: String) = withContext(ioDispatcher) {
        reconcileMutex.withLock {
            val desired = DownloadPlanner.desiredSongs(dao.pinMembership(serverId))
            val existing = dao.getAll(serverId).associateBy { it.songId }
            val plan = DownloadPlanner.plan(desired, existing.keys)
            val toRemove = plan.toRemove - held.filter { it.pin.serverId == serverId }.flatMapTo(HashSet()) { it.songs }
            if (toRemove.isNotEmpty()) {
                // Rows first, so the player and the worker stop using the files before they go.
                dao.delete(serverId, toRemove)
                toRemove.forEach { songId ->
                    files.delete(existing[songId]?.filePath)
                    files.deleteSong(serverId, songId)
                }
                // Freed space may let downloads waiting on the storage limit start.
                if (plan.toQueue.isEmpty() && dao.queuedCount() > 0) {
                    scheduler.schedule(preferenceSource.preferences.first(), restart = false)
                }
            }
            if (plan.toQueue.isNotEmpty()) {
                val now = clock.nowMillis()
                dao.insertAll(plan.toQueue.map { DownloadEntity(serverId, it, DownloadState.Queued, updatedAt = now) })
                scheduler.schedule(preferenceSource.preferences.first(), restart = false)
            }
        }
    }

    /**
     * An undoable removal of [pin], holding back the deletion of its [songs]. [paused] are the ones
     * it stopped downloading, which undo queues again.
     */
    private inner class HeldRemoval(val pin: PinEntity, val songs: Set<String>, val paused: List<String>) :
        PendingRemoval {
        private val settled = AtomicBoolean(false)

        override fun commit() = settle {
            reconcileMutex.withLock { held -= this }
            reconcile(pin.serverId)
        }

        override fun undo() = settle {
            reconcileMutex.withLock {
                held -= this
                dao.upsertPin(pin)
                dao.transition(
                    pin.serverId,
                    paused,
                    listOf(DownloadState.Paused),
                    DownloadState.Queued,
                    clock.nowMillis(),
                )
            }
            if (paused.isNotEmpty()) scheduler.schedule(preferenceSource.preferences.first(), restart = false)
            reconcile(pin.serverId)
        }

        private fun settle(block: suspend () -> Unit): Job = if (settled.compareAndSet(false, true)) {
            scope.launch(ioDispatcher) { block() }
        } else {
            Job().apply { complete() }
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

    private companion object {
        /** The states a song is in until it is finished, paused or failed. */
        val RUNNING = listOf(DownloadState.Queued, DownloadState.Downloading)
    }
}
