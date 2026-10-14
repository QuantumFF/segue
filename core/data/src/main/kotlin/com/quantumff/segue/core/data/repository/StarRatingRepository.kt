package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.model.MediaKind
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Stars and ratings. Updates show at once; a failed server call rolls them back and throws RemoteException. */
interface StarRatingRepository {
    suspend fun setStarred(kind: MediaKind, id: String, starred: Boolean)

    /** [rating] 1–5, or 0 to clear. */
    suspend fun setRating(kind: MediaKind, id: String, rating: Int)
}

internal class OptimisticStarRatingRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val clock: Clock,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : StarRatingRepository {
    override suspend fun setStarred(kind: MediaKind, id: String, starred: Boolean) = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val previous = starredAt(kind, serverId, id)
        writeStarred(kind, serverId, id, if (starred) clock.nowMillis() else null)
        rollbackOnFailure({ writeStarred(kind, serverId, id, previous) }) {
            val client = clients.client(serverId)
            val songs = listOfNotNull(id.takeIf { kind == MediaKind.Song })
            val albums = listOfNotNull(id.takeIf { kind == MediaKind.Album })
            val artists = listOfNotNull(id.takeIf { kind == MediaKind.Artist })
            if (starred) client.star(songs, albums, artists) else client.unstar(songs, albums, artists)
        }
    }

    override suspend fun setRating(kind: MediaKind, id: String, rating: Int) = withContext(ioDispatcher) {
        require(rating in 0..MAX_RATING) { "Rating must be 0..$MAX_RATING" }
        val serverId = clients.requireActiveServerId()
        val previous = rating(kind, serverId, id)
        writeRating(kind, serverId, id, rating.takeIf { it > 0 })
        rollbackOnFailure({ writeRating(kind, serverId, id, previous) }) {
            clients.client(serverId).setRating(id, rating)
        }
    }

    private suspend inline fun rollbackOnFailure(rollback: () -> Unit, call: () -> Unit) {
        try {
            remote(call)
        } catch (e: Exception) {
            rollback()
            throw e
        }
    }

    private suspend fun starredAt(kind: MediaKind, serverId: String, id: String): Long? = when (kind) {
        MediaKind.Song -> songDao.observe(serverId, id).first()?.starredAt
        MediaKind.Album -> albumDao.observe(serverId, id).first()?.starredAt
        MediaKind.Artist -> artistDao.observe(serverId, id).first()?.starredAt
    }

    private suspend fun rating(kind: MediaKind, serverId: String, id: String): Int? = when (kind) {
        MediaKind.Song -> songDao.observe(serverId, id).first()?.userRating
        MediaKind.Album -> albumDao.observe(serverId, id).first()?.userRating
        MediaKind.Artist -> artistDao.observe(serverId, id).first()?.userRating
    }

    private suspend fun writeStarred(kind: MediaKind, serverId: String, id: String, starredAt: Long?) = when (kind) {
        MediaKind.Song -> songDao.setStarred(serverId, id, starredAt)
        MediaKind.Album -> albumDao.setStarred(serverId, id, starredAt)
        MediaKind.Artist -> artistDao.setStarred(serverId, id, starredAt)
    }

    private suspend fun writeRating(kind: MediaKind, serverId: String, id: String, rating: Int?) = when (kind) {
        MediaKind.Song -> songDao.setRating(serverId, id, rating)
        MediaKind.Album -> albumDao.setRating(serverId, id, rating)
        MediaKind.Artist -> artistDao.setRating(serverId, id, rating)
    }

    private companion object {
        const val MAX_RATING = 5
    }
}
