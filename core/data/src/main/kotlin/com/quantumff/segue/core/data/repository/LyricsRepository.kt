package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.model.LyricLine
import com.quantumff.segue.core.model.Lyrics
import com.quantumff.segue.core.network.model.NetworkStructuredLyrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface LyricsRepository {
    /**
     * Every lyrics track the server has for the song, synced ones first. Empty when there are none
     * or the server lacks the OpenSubsonic `songLyrics` extension. Throws RemoteException.
     */
    suspend fun lyrics(songId: String): ImmutableList<Lyrics>
}

/** Keeps the last few results in memory, so reopening Now Playing does not refetch. */
@Singleton
internal class SubsonicLyricsRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val capabilities: ServerCapabilitiesRepository,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : LyricsRepository {
    private val cache = object : LinkedHashMap<Pair<String, String>, ImmutableList<Lyrics>>(
        CACHE_SIZE,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, ImmutableList<Lyrics>>) =
            size > CACHE_SIZE
    }

    override suspend fun lyrics(songId: String): ImmutableList<Lyrics> = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val key = serverId to songId
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        if (!capabilities.forServer(serverId).supportsSongLyrics) return@withContext persistentListOf()

        val lyrics = remote { clients.client(serverId).getLyricsBySongId(songId) }
            .map { it.asExternalModel() }
            .sortedByDescending { it.synced }
            .toImmutableList()
        synchronized(cache) { cache[key] = lyrics }
        lyrics
    }

    private fun NetworkStructuredLyrics.asExternalModel() = Lyrics(
        language = lang,
        synced = synced,
        offsetMillis = offset ?: 0,
        displayArtist = displayArtist,
        displayTitle = displayTitle,
        lines = line.map { LyricLine(startMillis = it.start.takeIf { synced }, text = it.value) }.toImmutableList(),
    )

    private companion object {
        const val CACHE_SIZE = 16
        const val LOAD_FACTOR = 0.75f
    }
}
