package com.quantumff.segue.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PendingScrobbleEntity
import com.quantumff.segue.core.database.model.QueueItemEntity
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.database.model.QueueSyncStateEntity
import com.quantumff.segue.core.database.model.ServerCapabilitiesEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.SyncMetadataEntity
import com.quantumff.segue.core.model.DownloadState
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

    @Query("SELECT seenRemoteChangedAt FROM queue_sync_state WHERE serverId = :serverId")
    abstract suspend fun seenRemoteChangedAt(serverId: String): Long?

    /** Records [changedAt] as seen, unless a later server change was already seen. */
    @Transaction
    open suspend fun markRemoteChangeSeen(serverId: String, changedAt: Long) {
        val seen = seenRemoteChangedAt(serverId)
        if (seen == null || changedAt > seen) upsertSyncState(QueueSyncStateEntity(serverId, changedAt))
    }

    @Upsert
    protected abstract suspend fun upsertSyncState(state: QueueSyncStateEntity)
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

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE serverId = :serverId ORDER BY updatedAt DESC")
    fun observeAll(serverId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND state = :state ORDER BY updatedAt DESC")
    fun observeByState(serverId: String, state: DownloadState): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND songId = :songId")
    fun observe(serverId: String, songId: String): Flow<DownloadEntity?>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND songId = :songId")
    suspend fun delete(serverId: String, songId: String)
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

    @Query("DELETE FROM sync_metadata WHERE serverId = :serverId")
    protected abstract suspend fun deleteSyncMetadata(serverId: String)

    @Query("DELETE FROM server_capabilities WHERE serverId = :serverId")
    protected abstract suspend fun deleteCapabilities(serverId: String)
}
