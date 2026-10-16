package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.QueueDao
import com.quantumff.segue.core.database.model.AlbumFeedEntity
import com.quantumff.segue.core.model.AlbumFeed
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
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

private const val KIND_LIBRARY = "Library"
private const val KIND_ALBUMS = "Albums"
private const val KIND_ARTIST = "Artist"
private const val KIND_GENRE = "Genre"
private const val KIND_PLAYLIST = "Playlist"

private fun AlbumFeed.asEntity(serverId: String): AlbumFeedEntity {
    val base = AlbumFeedEntity(
        serverId = serverId,
        sourceKind = KIND_LIBRARY,
        albumOrder = order.name,
        seed = seed,
        cursor = cursor,
        queueFingerprint = queueFingerprint,
    )
    return when (val source = source) {
        AlbumSource.Library -> base

        is AlbumSource.Albums -> base.copy(
            sourceKind = KIND_ALBUMS,
            sort = source.sort.name,
            starredOnly = source.filter.starredOnly,
            genre = source.filter.genre,
            year = source.filter.year,
            downloadedOnly = source.filter.downloadedOnly,
        )

        is AlbumSource.Artist -> base.copy(sourceKind = KIND_ARTIST, sourceId = source.artistId)

        is AlbumSource.Genre -> base.copy(sourceKind = KIND_GENRE, sourceId = source.name)

        is AlbumSource.Playlist -> base.copy(sourceKind = KIND_PLAYLIST, sourceId = source.playlistId)
    }
}

/** Null for a row this version cannot read (an unknown kind or a missing ID): the feed is dropped. */
private fun AlbumFeedEntity.asExternalModel(): AlbumFeed? {
    val source = when (sourceKind) {
        KIND_LIBRARY -> AlbumSource.Library

        KIND_ALBUMS -> AlbumSource.Albums(
            sort = AlbumSortOrder.entries.firstOrNull { it.name == sort } ?: return null,
            filter = AlbumFilter(starredOnly, genre, year, downloadedOnly),
        )

        KIND_ARTIST -> AlbumSource.Artist(sourceId ?: return null)

        KIND_GENRE -> AlbumSource.Genre(sourceId ?: return null)

        KIND_PLAYLIST -> AlbumSource.Playlist(sourceId ?: return null)

        else -> return null
    }
    val order = AlbumOrder.entries.firstOrNull { it.name == albumOrder } ?: return null
    return AlbumFeed(source, order, seed, cursor, queueFingerprint)
}
