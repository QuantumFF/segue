package com.quantumff.segue.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PendingScrobbleEntity
import com.quantumff.segue.core.database.model.PinEntity
import com.quantumff.segue.core.database.model.PinSongRow
import com.quantumff.segue.core.database.model.QueueItemEntity
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.database.model.ServerCapabilitiesEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.SyncMetadataEntity
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.PinKind
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QueueDao {
    @Query("SELECT * FROM queue_state WHERE serverId = :serverId")
    abstract fun observeState(serverId: String): Flow<QueueStateEntity?>

    @Query("SELECT songId FROM queue_items WHERE serverId = :serverId ORDER BY position")
    abstract fun observeSongIds(serverId: String): Flow<List<String>>

    @Query("SELECT songId FROM queue_items WHERE serverId = :serverId ORDER BY position")
    abstract suspend fun songIds(serverId: String): List<String>

    /** Replaces the whole snapshot atomically, so readers never see a half-written queue. */
    @Transaction
    open suspend fun replaceSnapshot(state: QueueStateEntity, songIds: List<String>) {
        deleteItems(state.serverId)
        insertItems(songIds.mapIndexed { index, id -> QueueItemEntity(state.serverId, index, id) })
        upsertState(state)
    }

    @Query("DELETE FROM queue_items WHERE serverId = :serverId")
    protected abstract suspend fun deleteItems(serverId: String)

    @Insert
    protected abstract suspend fun insertItems(items: List<QueueItemEntity>)

    @Upsert
    protected abstract suspend fun upsertState(state: QueueStateEntity)
}

@Dao
abstract class ScrobbleDao {
    @Insert
    abstract suspend fun insert(scrobble: PendingScrobbleEntity)

    @Query("SELECT * FROM pending_scrobbles WHERE serverId = :serverId ORDER BY playedAt LIMIT :limit")
    abstract suspend fun oldest(serverId: String, limit: Int): List<PendingScrobbleEntity>

    @Query("SELECT COUNT(*) FROM pending_scrobbles WHERE serverId = :serverId")
    abstract fun observeCount(serverId: String): Flow<Int>

    @Transaction
    open suspend fun delete(ids: Collection<Long>) {
        ids.chunked(SQL_CHUNK).forEach { deleteChunk(it) }
    }

    @Query("DELETE FROM pending_scrobbles WHERE id IN (:ids)")
    protected abstract suspend fun deleteChunk(ids: List<Long>)
}

/** Songs covered by each pin of a server: album tracks, playlist entries, all albums of an artist, single songs. */
private const val PIN_MEMBERSHIP = """
    SELECT p.kind AS kind, p.itemId AS itemId, s.id AS songId FROM pins p
    INNER JOIN songs s ON s.serverId = p.serverId AND s.albumId = p.itemId
    WHERE p.serverId = :serverId AND p.kind = 'Album'
    UNION ALL
    SELECT p.kind, p.itemId, ps.songId FROM pins p
    INNER JOIN playlist_songs ps ON ps.serverId = p.serverId AND ps.playlistId = p.itemId
    WHERE p.serverId = :serverId AND p.kind = 'Playlist'
    UNION ALL
    SELECT p.kind, p.itemId, s.id FROM pins p
    INNER JOIN albums a ON a.serverId = p.serverId AND (
        a.artistId = p.itemId OR a.id IN (
            SELECT aa.albumId FROM album_artists aa WHERE aa.serverId = p.serverId AND aa.artistId = p.itemId
        )
    )
    INNER JOIN songs s ON s.serverId = a.serverId AND s.albumId = a.id
    WHERE p.serverId = :serverId AND p.kind = 'Artist'
    UNION ALL
    SELECT p.kind, p.itemId, p.itemId FROM pins p WHERE p.serverId = :serverId AND p.kind = 'Song'
"""

/**
 * Pins and the per-song download rows they produce. Which songs should be on the device is
 * always derived from the pins ([pinMembership]); the download engine reconciles the rows to it.
 */
@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads WHERE serverId = :serverId ORDER BY updatedAt DESC")
    abstract fun observeAll(serverId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND state = :state ORDER BY updatedAt DESC")
    abstract fun observeByState(serverId: String, state: DownloadState): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND songId = :songId")
    abstract fun observe(serverId: String, songId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND songId = :songId")
    abstract suspend fun get(serverId: String, songId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE serverId = :serverId")
    abstract suspend fun getAll(serverId: String): List<DownloadEntity>

    /** Every server's rows, for removing all downloads. */
    @Query("SELECT * FROM downloads")
    abstract suspend fun getEverything(): List<DownloadEntity>

    @Query("SELECT songId FROM downloads WHERE serverId = :serverId AND state = 'Completed'")
    abstract fun observeCompletedSongIds(serverId: String): Flow<List<String>>

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads")
    abstract fun observeUsedBytes(): Flow<Long>

    @Upsert
    abstract suspend fun upsert(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(downloads: List<DownloadEntity>)

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND songId = :songId")
    abstract suspend fun delete(serverId: String, songId: String)

    @Transaction
    open suspend fun delete(serverId: String, songIds: Collection<String>) {
        songIds.chunked(SQL_CHUNK).forEach { deleteChunk(serverId, it) }
    }

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND songId IN (:songIds)")
    protected abstract suspend fun deleteChunk(serverId: String, songIds: List<String>)

    // --- Pins -------------------------------------------------------------------------------------

    @Query("SELECT * FROM pins WHERE serverId = :serverId ORDER BY createdAt DESC")
    abstract fun observePins(serverId: String): Flow<List<PinEntity>>

    @Query("SELECT * FROM pins")
    abstract suspend fun allPins(): List<PinEntity>

    @Upsert
    abstract suspend fun upsertPin(pin: PinEntity)

    @Query("DELETE FROM pins WHERE serverId = :serverId AND kind = :kind AND itemId = :itemId")
    abstract suspend fun deletePin(serverId: String, kind: PinKind, itemId: String)

    @Query("DELETE FROM pins")
    abstract suspend fun deleteAllPins()

    @Query(PIN_MEMBERSHIP)
    abstract fun observePinMembership(serverId: String): Flow<List<PinSongRow>>

    @Query(PIN_MEMBERSHIP)
    abstract suspend fun pinMembership(serverId: String): List<PinSongRow>

    // --- Download engine ----------------------------------------------------------------------------

    /**
     * Claims the oldest queued download of any server: marks it Downloading and returns it, or
     * returns null when nothing is queued. Atomic, so parallel downloaders never take the same row.
     */
    @Transaction
    open suspend fun claimNext(now: Long): DownloadEntity? {
        val next = oldestQueued() ?: return null
        setState(next.serverId, next.songId, DownloadState.Downloading, now)
        return next.copy(state = DownloadState.Downloading, updatedAt = now)
    }

    @Query("SELECT * FROM downloads WHERE state = 'Queued' ORDER BY updatedAt, songId LIMIT 1")
    protected abstract suspend fun oldestQueued(): DownloadEntity?

    @Query("UPDATE downloads SET state = :state, updatedAt = :now WHERE serverId = :serverId AND songId = :songId")
    protected abstract suspend fun setState(serverId: String, songId: String, state: DownloadState, now: Long)

    /** Downloads left Downloading by a stopped or killed worker go back to the queue. */
    @Query("UPDATE downloads SET state = 'Queued' WHERE state = 'Downloading'")
    abstract suspend fun requeueInterrupted()

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'Queued'")
    abstract suspend fun queuedCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'Downloading'")
    abstract suspend fun downloadingCount(): Int

    /**
     * Marks a running download finished. Returns false when the row is no longer Downloading
     * (paused, removed): the caller then discards the file.
     */
    open suspend fun complete(serverId: String, songId: String, path: String, bytes: Long, now: Long): Boolean =
        completeRow(serverId, songId, path, bytes, now) > 0

    @Query(
        """
        UPDATE downloads SET state = 'Completed', filePath = :path, bytesDownloaded = :bytes, totalBytes = :bytes,
            failureReason = NULL, updatedAt = :now
        WHERE serverId = :serverId AND songId = :songId AND state = 'Downloading'
        """,
    )
    protected abstract suspend fun completeRow(
        serverId: String,
        songId: String,
        path: String,
        bytes: Long,
        now: Long,
    ): Int

    /** Saves how far a stopped download got. Only touches a row that is still Downloading. */
    @Query(
        """
        UPDATE downloads SET state = :state, bytesDownloaded = :bytes, totalBytes = :total, failureReason = :reason,
            updatedAt = :now
        WHERE serverId = :serverId AND songId = :songId AND state = 'Downloading'
        """,
    )
    abstract suspend fun finishAttempt(
        serverId: String,
        songId: String,
        state: DownloadState,
        bytes: Long,
        total: Long?,
        reason: String?,
        now: Long,
    ): Int

    /** Whether the row is still Downloading: false once it was paused or removed mid-download. */
    @Query(
        "SELECT COUNT(*) > 0 FROM downloads WHERE serverId = :serverId AND songId = :songId AND state = 'Downloading'",
    )
    abstract suspend fun isDownloading(serverId: String, songId: String): Boolean

    /** Moves [songIds] in state [from] to [to]; for pause, resume and retry. */
    @Transaction
    open suspend fun transition(
        serverId: String,
        songIds: Collection<String>,
        from: Collection<DownloadState>,
        to: DownloadState,
        now: Long,
    ) {
        songIds.chunked(SQL_CHUNK).forEach { transitionChunk(serverId, it, from.toList(), to, now) }
    }

    @Query(
        """
        UPDATE downloads SET state = :to, updatedAt = :now
        WHERE serverId = :serverId AND songId IN (:songIds) AND state IN (:from)
        """,
    )
    protected abstract suspend fun transitionChunk(
        serverId: String,
        songIds: List<String>,
        from: List<DownloadState>,
        to: DownloadState,
        now: Long,
    )
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE serverId = :serverId")
    suspend fun get(serverId: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE serverId = :serverId")
    fun observe(serverId: String): Flow<SyncMetadataEntity?>

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}

@Dao
interface ServerCapabilitiesDao {
    @Query("SELECT * FROM server_capabilities WHERE serverId = :serverId")
    fun observe(serverId: String): Flow<ServerCapabilitiesEntity?>

    @Query("SELECT * FROM server_capabilities WHERE serverId = :serverId")
    suspend fun get(serverId: String): ServerCapabilitiesEntity?

    @Upsert
    suspend fun upsert(capabilities: ServerCapabilitiesEntity)
}

/** Full-text search over the FTS4 tables. Pass a `MATCH` expression from FtsQuery. */
@Dao
interface SearchDao {
    @Query(
        """
        SELECT songs.* FROM songs JOIN songs_fts ON songs.rowid = songs_fts.rowid
        WHERE songs_fts MATCH :match AND songs.serverId = :serverId
        ORDER BY songs.sortName LIMIT :limit
        """,
    )
    suspend fun songs(serverId: String, match: String, limit: Int): List<SongEntity>

    @Query(
        """
        SELECT albums.* FROM albums JOIN albums_fts ON albums.rowid = albums_fts.rowid
        WHERE albums_fts MATCH :match AND albums.serverId = :serverId
        ORDER BY albums.sortName LIMIT :limit
        """,
    )
    suspend fun albums(serverId: String, match: String, limit: Int): List<AlbumEntity>

    @Query(
        """
        SELECT artists.* FROM artists JOIN artists_fts ON artists.rowid = artists_fts.rowid
        WHERE artists_fts MATCH :match AND artists.serverId = :serverId
        ORDER BY artists.sortName LIMIT :limit
        """,
    )
    suspend fun artists(serverId: String, match: String, limit: Int): List<ArtistEntity>
}

/** Removes everything stored for a server, for when its account is deleted. */
@Dao
abstract class ServerDataDao {
    @Transaction
    open suspend fun deleteServer(serverId: String) {
        deleteSongs(serverId)
        deleteAlbums(serverId)
        deleteAlbumArtists(serverId)
        deleteArtists(serverId)
        deleteGenres(serverId)
        deletePlaylistSongs(serverId)
        deletePlaylists(serverId)
        deleteAlbumLists(serverId)
        deleteQueueItems(serverId)
        deleteQueueState(serverId)
        deleteScrobbles(serverId)
        deleteDownloads(serverId)
        deletePins(serverId)
        deleteSyncMetadata(serverId)
        deleteCapabilities(serverId)
    }

    @Query("DELETE FROM songs WHERE serverId = :serverId")
    protected abstract suspend fun deleteSongs(serverId: String)

    @Query("DELETE FROM albums WHERE serverId = :serverId")
    protected abstract suspend fun deleteAlbums(serverId: String)

    @Query("DELETE FROM album_artists WHERE serverId = :serverId")
    protected abstract suspend fun deleteAlbumArtists(serverId: String)

    @Query("DELETE FROM artists WHERE serverId = :serverId")
    protected abstract suspend fun deleteArtists(serverId: String)

    @Query("DELETE FROM genres WHERE serverId = :serverId")
    protected abstract suspend fun deleteGenres(serverId: String)

    @Query("DELETE FROM playlist_songs WHERE serverId = :serverId")
    protected abstract suspend fun deletePlaylistSongs(serverId: String)

    @Query("DELETE FROM playlists WHERE serverId = :serverId")
    protected abstract suspend fun deletePlaylists(serverId: String)

    @Query("DELETE FROM album_list_entries WHERE serverId = :serverId")
    protected abstract suspend fun deleteAlbumLists(serverId: String)

    @Query("DELETE FROM queue_items WHERE serverId = :serverId")
    protected abstract suspend fun deleteQueueItems(serverId: String)

    @Query("DELETE FROM queue_state WHERE serverId = :serverId")
    protected abstract suspend fun deleteQueueState(serverId: String)

    @Query("DELETE FROM pending_scrobbles WHERE serverId = :serverId")
    protected abstract suspend fun deleteScrobbles(serverId: String)

    @Query("DELETE FROM downloads WHERE serverId = :serverId")
    protected abstract suspend fun deleteDownloads(serverId: String)

    @Query("DELETE FROM pins WHERE serverId = :serverId")
    protected abstract suspend fun deletePins(serverId: String)

    @Query("DELETE FROM sync_metadata WHERE serverId = :serverId")
    protected abstract suspend fun deleteSyncMetadata(serverId: String)

    @Query("DELETE FROM server_capabilities WHERE serverId = :serverId")
    protected abstract suspend fun deleteCapabilities(serverId: String)
}
