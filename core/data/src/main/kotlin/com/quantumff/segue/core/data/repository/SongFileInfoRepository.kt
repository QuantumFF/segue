package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.model.SongFileInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface SongFileInfoRepository {
    /** The song's file details from `getSong`. Throws RemoteException. */
    suspend fun fileInfo(songId: String): SongFileInfo
}

/** Keeps the last few results in memory, so reopening Now Playing does not refetch. */
@Singleton
internal class SubsonicSongFileInfoRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SongFileInfoRepository {
    private val cache = object : LinkedHashMap<Pair<String, String>, SongFileInfo>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, SongFileInfo>) =
            size > CACHE_SIZE
    }

    override suspend fun fileInfo(songId: String): SongFileInfo = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val key = serverId to songId
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val song = remote { clients.client(serverId).getSong(songId) }
        val info = SongFileInfo(
            path = song.path?.takeIf { it.isNotBlank() },
            samplingRate = song.samplingRate?.takeIf { it > 0 },
            bitDepth = song.bitDepth?.takeIf { it > 0 },
            channelCount = song.channelCount?.takeIf { it > 0 },
        )
        synchronized(cache) { cache[key] = info }
        info
    }

    private companion object {
        const val CACHE_SIZE = 32
        const val LOAD_FACTOR = 0.75f
    }
}
