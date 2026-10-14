package com.quantumff.segue.core.playback.cache

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.quantumff.segue.core.playback.media.SegueUris
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads upcoming tracks whole into the stream cache in one burst, so the radio can go idle
 * until the next track change instead of waking for every buffer refill. Which tracks depends on
 * the network ([PrefetchPolicy]). The current track is covered by the player's long load-control
 * buffer instead, so it is not downloaded twice.
 *
 * Call [update] from the player thread whenever what plays next may have changed.
 */
class NextTrackPrefetcher(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val resolver: SegueUriResolver,
    private val network: NetworkMonitor,
) {
    private var targets: List<String> = emptyList()
    private var job: Job? = null

    /** [upNext] is the song IDs after the current one, in play order. Pass empty to stop. */
    fun update(upNext: List<String>) {
        val wanted = PrefetchPolicy.targets(upNext, network.current())
        if (wanted == targets && job?.isActive == true) return
        targets = wanted
        job?.cancel()
        job = if (wanted.isEmpty()) null else scope.launch { wanted.forEach { prefetch(it) } }
    }

    fun stop() = update(emptyList())

    private suspend fun prefetch(songId: String) = withContext(ioDispatcher) {
        try {
            val dataSpec = resolver.resolveDataSpec(DataSpec(SegueUris.song(songId)))
            val writer = CacheWriter(cacheDataSourceFactory.createDataSourceForDownloading(), dataSpec, null, null)
            val handle = coroutineContext.job.invokeOnCompletion { writer.cancel() }
            try {
                writer.cache()
            } finally {
                handle.dispose()
            }
        } catch (_: IOException) {
            // Offline or a server error: the player will stream it when it gets there.
        }
    }
}
