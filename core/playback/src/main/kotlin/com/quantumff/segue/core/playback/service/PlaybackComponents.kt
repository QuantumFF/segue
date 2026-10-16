package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.data.download.downloadedWhileOffline
import com.quantumff.segue.core.data.repository.AlbumFeedRepository
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.data.repository.ScrobbleRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.playback.cache.AdjustableLruCacheEvictor
import com.quantumff.segue.core.playback.cache.NetworkMonitor
import com.quantumff.segue.core.playback.cache.NextTrackPrefetcher
import com.quantumff.segue.core.playback.cache.SegueUriResolver
import com.quantumff.segue.core.playback.cache.playbackDataSourceFactory
import com.quantumff.segue.core.playback.nowplaying.NowPlayingPublisher
import com.quantumff.segue.core.playback.replaygain.ReplayGainProcessor
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/** Everything the playback service needs, constructor-injected in one piece. */
@Suppress("LongParameterList")
class PlaybackComponents @Inject constructor(
    val queueSync: QueueSyncEngine,
    val bridge: PlaybackServiceBridge,
    val scrobbles: ScrobbleRepository,
    val starRatings: StarRatingRepository,
    val library: LibraryRepository,
    val snapshots: QueueSnapshotRepository,
    val preferences: UserPreferencesRepository,
    val cacheEvictor: AdjustableLruCacheEvictor,
    /** Android Auto browsing and search. */
    val mediaBrowse: MediaBrowseRepository,
    val albumShuffle: AlbumShuffleRepository,
    /** "Play albums" / "Shuffle albums" feeds, persisted with the queue. */
    val albumFeeds: AlbumFeedRepository,
    val search: SearchRepository,
    private val offlineMode: OfflineModeRepository,
    private val downloads: DownloadRepository,
    /** Widgets. */
    val nowPlaying: NowPlayingPublisher,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val resolver: SegueUriResolver,
    private val network: NetworkMonitor,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope val appScope: CoroutineScope,
    @Dispatcher(SegueDispatchers.Main) val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    val networkType get() = network.networkType

    /** What is downloaded, while offline; null online. For browse lists that narrow offline. */
    suspend fun downloadedWhileOffline(): DownloadedLibrary? = offlineMode.downloadedWhileOffline(downloads).first()

    /**
     * ExoPlayer for music: audio focus, pause on headphone unplug, a CPU wake lock while playing
     * (the service adds a Wi-Fi lock only while loading, see [LoadingWifiLock]), and media loaded
     * from `segue://` URIs through the stream cache. Gapless needs nothing extra: ExoPlayer
     * reads encoder delay and padding from the files.
     *
     * The load control buffers up to ten minutes (or its byte budget) and refills only below 30
     * seconds, so the current track arrives in a few long bursts rather than a steady trickle.
     * Dynamic scheduling lets the playback thread sleep between renders, which pays off most with
     * offload.
     *
     * [replayGain] runs in the audio sink for ReplayGain boost; it is skipped while offloaded.
     */
    // Dynamic scheduling lets the playback thread sleep between renders (battery, PLAN.md).
    @OptIn(ExperimentalApi::class)
    fun createExoPlayer(context: Context, replayGain: ReplayGainProcessor): ExoPlayer {
        val dataSourceFactory = playbackDataSourceFactory(context, cacheDataSourceFactory, resolver)
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(replayGain))
                .build()
        }
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
                    .build(),
            )
            .experimentalSetDynamicSchedulingEnabled(true)
            .build()
    }

    /** Loads `segue://cover` artwork for the notification and lock screen. */
    fun createBitmapLoader(context: Context): BitmapLoader = CacheBitmapLoader(
        DataSourceBitmapLoader.Builder(context)
            .setDataSourceFactory(ResolvingDataSource.Factory(OkHttpDataSource.Factory(okHttpClient), resolver))
            .build(),
    )

    fun createPrefetcher(scope: CoroutineScope) =
        NextTrackPrefetcher(scope, ioDispatcher, cacheDataSourceFactory, resolver, network)

    private companion object {
        const val MIN_BUFFER_MS = 30_000
        const val MAX_BUFFER_MS = 10 * 60_000
        const val START_BUFFER_MS = 2_500
        const val REBUFFER_MS = 5_000
    }
}
