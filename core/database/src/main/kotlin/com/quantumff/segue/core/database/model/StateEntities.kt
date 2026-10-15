package com.quantumff.segue.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.ShuffleMode

/**
 * The local play queue snapshot: where playback was, so it can resume offline. [currentIndex]
 * points into the queue items in queue (unshuffled) order; [playOrder] holds the shuffle.
 */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val serverId: String,
    val currentIndex: Int,
    val positionMillis: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "Off") val shuffleMode: ShuffleMode = ShuffleMode.Off,
    /** Queue item positions in play order while shuffled; null when shuffle is off. */
    val playOrder: IndexList? = null,
)

/** The snapshot's tracks by position. The same song may appear more than once. */
@Entity(tableName = "queue_items", primaryKeys = ["serverId", "position"])
data class QueueItemEntity(val serverId: String, val position: Int, val songId: String)

/**
 * Queue sync bookkeeping per server. [seenRemoteChangedAt] is the server's `changed` time of the
 * newest server queue this device has accounted for: its own save, or another client's save that
 * the user adopted or dismissed. It is a server timestamp, so comparing it with later server
 * timestamps does not depend on this device's clock.
 */
@Entity(tableName = "queue_sync_state")
data class QueueSyncStateEntity(@PrimaryKey val serverId: String, val seenRemoteChangedAt: Long)

/** A list of indices, stored as comma-separated text. */
data class IndexList(val items: List<Int> = emptyList())

/** A play not yet sent to the server. Sent in batches with one `scrobble` call. */
@Entity(tableName = "pending_scrobbles", indices = [Index("serverId", "playedAt")])
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val songId: String,
    val playedAt: Long,
    val submission: Boolean,
)

@Entity(
    tableName = "downloads",
    primaryKeys = ["serverId", "songId"],
    indices = [Index("serverId", "state")],
)
data class DownloadEntity(
    val serverId: String,
    val songId: String,
    val state: DownloadState,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
    val filePath: String? = null,
    val failureReason: String? = null,
    val updatedAt: Long,
)

/** Library sync bookkeeping per server. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val serverId: String,
    /** `getArtists` `lastModified` from the last sync. */
    val artistsLastModified: Long? = null,
    val lastFullSyncAt: Long? = null,
    val lastSyncAt: Long? = null,
)

/** Cached result of capability detection. [extensions] maps names to versions. */
@Entity(tableName = "server_capabilities")
data class ServerCapabilitiesEntity(
    @PrimaryKey val serverId: String,
    val apiVersion: String,
    val openSubsonic: Boolean,
    val serverType: String?,
    val serverVersion: String?,
    val extensions: ExtensionMap,
    val detectedAt: Long,
)

/** OpenSubsonic extension names to supported versions. */
data class ExtensionMap(val versions: Map<String, List<Int>> = emptyMap())
