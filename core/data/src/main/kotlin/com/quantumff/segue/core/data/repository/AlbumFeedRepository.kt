package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.QueueDao
import com.quantumff.segue.core.database.model.AlbumFeedEntity
import com.quantumff.segue.core.model.AlbumFeed
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumSourceRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The active server's running album feed, kept in Room next to the queue snapshot so it survives
 * process death. Reads and writes nothing when signed out.
 */
interface AlbumFeedRepository {
    suspend fun feed(): AlbumFeed?

    suspend fun save(feed: AlbumFeed)

    suspend fun clear()
}

internal class RoomAlbumFeedRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val queueDao: QueueDao,
) : AlbumFeedRepository {
    override suspend fun feed(): AlbumFeed? {
        val serverId = clients.activeServerId.first() ?: return null
        return queueDao.albumFeed(serverId)?.asExternalModel()
    }

    override suspend fun save(feed: AlbumFeed) {
        val serverId = clients.activeServerId.first() ?: return
        queueDao.upsertAlbumFeed(feed.asEntity(serverId))
    }

    override suspend fun clear() {
        val serverId = clients.activeServerId.first() ?: return
        queueDao.deleteAlbumFeed(serverId)
    }
}

private fun AlbumFeed.asEntity(serverId: String): AlbumFeedEntity {
    val record = AlbumSourceRecord.of(source)
    return AlbumFeedEntity(
        serverId = serverId,
        sourceKind = record.kind,
        sourceId = record.id,
        sort = record.sort,
        starredOnly = record.starredOnly,
        genre = record.genre,
        year = record.year,
        downloadedOnly = record.downloadedOnly,
        albumOrder = order.name,
        seed = seed,
        cursor = cursor,
        queueFingerprint = queueFingerprint,
    )
}

/** Null for a row this version cannot read (see [AlbumSourceRecord.toSource]): the feed is dropped. */
private fun AlbumFeedEntity.asExternalModel(): AlbumFeed? {
    val source = AlbumSourceRecord(sourceKind, sourceId, sort, starredOnly, genre, year, downloadedOnly).toSource()
        ?: return null
    val order = AlbumOrder.entries.firstOrNull { it.name == albumOrder } ?: return null
    return AlbumFeed(source, order, seed, cursor, queueFingerprint)
}
