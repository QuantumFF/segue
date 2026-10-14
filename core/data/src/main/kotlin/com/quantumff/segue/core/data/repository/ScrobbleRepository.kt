package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.ScrobbleScheduler
import com.quantumff.segue.core.database.dao.ScrobbleDao
import com.quantumff.segue.core.database.model.PendingScrobbleEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reports plays to the server. When to scrobble is the playback layer's policy; this only stores
 * and sends.
 */
interface ScrobbleRepository {
    /** Marks [songId] as playing now (`submission=false`). Best effort: failures are ignored, never queued. */
    suspend fun nowPlaying(songId: String)

    /**
     * Records a play that started at [playedAtMillis] (epoch). It is stored in Room first and sent
     * in batches by a WorkManager job once there is a network, so plays made offline are kept.
     */
    suspend fun submit(songId: String, playedAtMillis: Long)
}

internal class PendingScrobbleRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val scrobbleDao: ScrobbleDao,
    private val scheduler: ScrobbleScheduler,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ScrobbleRepository {
    override suspend fun nowPlaying(songId: String) = withContext(ioDispatcher) {
        try {
            val serverId = clients.requireActiveServerId()
            remote { clients.client(serverId).scrobble(listOf(songId), emptyList(), submission = false) }
        } catch (_: RemoteException) {
            // A stale "now playing" is worthless later, so it is not retried.
        }
    }

    override suspend fun submit(songId: String, playedAtMillis: Long) = withContext(ioDispatcher) {
        val serverId = try {
            clients.requireActiveServerId()
        } catch (_: RemoteException.NotSignedIn) {
            return@withContext
        }
        scrobbleDao.insert(
            PendingScrobbleEntity(serverId = serverId, songId = songId, playedAt = playedAtMillis, submission = true),
        )
        scheduler.requestFlush(serverId)
    }
}

/** Sends a server's pending scrobbles, oldest first, in batches of one `scrobble` call each. */
internal class ScrobbleFlusher @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val scrobbleDao: ScrobbleDao,
) {
    /** Sends until nothing is pending. Throws RemoteException; what was sent before stays deleted. */
    suspend fun flush(serverId: String) {
        val client = clients.client(serverId)
        while (true) {
            val batch = scrobbleDao.oldest(serverId, BATCH_SIZE)
            if (batch.isEmpty()) return
            batch.partition { it.submission }.toList().filter { it.isNotEmpty() }.forEach { rows ->
                remote { client.scrobble(rows.map { it.songId }, rows.map { it.playedAt }, rows.first().submission) }
            }
            scrobbleDao.delete(batch.map { it.id })
        }
    }

    companion object {
        const val BATCH_SIZE = 50
    }
}
