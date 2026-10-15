package com.quantumff.segue

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadInitializer
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.playback.PlaybackInitializer
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class SegueApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var playbackInitializer: PlaybackInitializer

    @Inject
    lateinit var downloadInitializer: DownloadInitializer

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        playbackInitializer.start()
        downloadInitializer.start()
        // Off the main thread and not awaited: the first frame renders from Room.
        applicationScope.launch {
            try {
                // Re-detects capabilities and schedules an incremental library sync (network + battery constraints).
                serverRepository.refreshActiveServer()
            } catch (e: RemoteException.AuthFailed) {
                Log.w(TAG, "The active server rejected the stored password", e)
            }
        }
        // Cover art memory-cache keys omit the server, so switching servers drops them.
        applicationScope.launch {
            serverRepository.activeServer
                .map { it?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { imageLoader.get().memoryCache?.clear() }
        }
    }

    private companion object {
        const val TAG = "SegueApplication"
    }
}
