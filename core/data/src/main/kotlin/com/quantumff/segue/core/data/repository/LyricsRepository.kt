package com.quantumff.segue.core.data.repository

import android.util.Log
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.DownloadDao
import com.quantumff.segue.core.database.dao.LyricsDao
import com.quantumff.segue.core.database.model.LyricsEntity
import com.quantumff.segue.core.database.model.LyricsList
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.LyricLine
import com.quantumff.segue.core.model.Lyrics
import com.quantumff.segue.core.network.model.NetworkStructuredLyrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface LyricsRepository {
    /**
     * Every lyrics track the server has for the song, synced ones first. Empty when there are none
     * or the server lacks the OpenSubsonic `songLyrics` extension. A downloaded song's lyrics are
     * kept on the device and load without the network; any other song's need it, so this throws
     * RemoteException when offline.
     */
    suspend fun lyrics(songId: String): ImmutableList<Lyrics>
}

/**
 * Lyrics of downloaded songs are kept in Room ([keep], deleted with the download); everything else
 * lives only in a small memory cache, so reopening Now Playing does not refetch.
 */
@Singleton
internal class SubsonicLyricsRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val capabilities: ServerCapabilitiesRepository,
    private val lyricsDao: LyricsDao,
    private val downloads: DownloadDao,
    private val transactions: TransactionRunner,
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
        lyricsDao.get(serverId, songId)?.let { return@withContext it.lyrics.items.toImmutableList() }
        if (!capabilities.forServer(serverId).supportsSongLyrics) return@withContext persistentListOf()
        fetch(serverId, songId)
    }

    /**
     * Stores the lyrics of a song whose download just completed. Best effort: a failure is logged,
     * never thrown, so it cannot fail the download. Servers without `songLyrics` are skipped.
     */
    suspend fun keep(serverId: String, songId: String) = withContext(ioDispatcher) {
        try {
            if (!capabilities.forServer(serverId).supportsSongLyrics) return@withContext
            val lyrics = fetch(serverId, songId)
            transactions.run {
                // Unpinned while the lyrics were on their way: the removal already ran.
                if (downloads.get(serverId, songId)?.state == DownloadState.Completed) {
                    lyricsDao.upsert(LyricsEntity(serverId, songId, LyricsList(lyrics)))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "No lyrics kept for a downloaded song", e)
        }
    }

    private suspend fun fetch(serverId: String, songId: String): ImmutableList<Lyrics> {
        val key = serverId to songId
        synchronized(cache) { cache[key] }?.let { return it }
        val lyrics = remote { clients.client(serverId).getLyricsBySongId(songId) }
            .map { it.asExternalModel() }
            .sortedByDescending { it.synced }
            .toImmutableList()
        synchronized(cache) { cache[key] = lyrics }
        return lyrics
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
        const val TAG = "LyricsRepository"
        const val CACHE_SIZE = 16
        const val LOAD_FACTOR = 0.75f
    }
}
