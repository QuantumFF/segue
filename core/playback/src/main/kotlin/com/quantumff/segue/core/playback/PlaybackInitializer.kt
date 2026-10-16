package com.quantumff.segue.core.playback

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.datasource.cache.Cache
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.playback.cache.StreamCache
import com.quantumff.segue.core.playback.service.PlaybackServiceBridge
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide playback wiring. Call [start] once from `Application.onCreate`.
 *
 * - App start and return to the foreground: check the server for a newer queue.
 * - App to the background: flush queue sync at once.
 * - Auto-adopted remote queues are handed to the player.
 * - Back online (connectivity callback or offline mode turned off): a queue save that failed
 *   while offline is uploaded. Pending scrobbles flush by themselves: their WorkManager job waits
 *   for a network.
 * - The stream cache is opened on an IO thread, so the service (which the UI starts at once to
 *   show the player) does not do that disk work on the main thread.
 */
@Singleton
class PlaybackInitializer @Inject constructor(
    private val queueSync: QueueSyncEngine,
    private val bridge: PlaybackServiceBridge,
    private val controller: PlaybackController,
    private val offlineMode: OfflineModeRepository,
    @StreamCache private val streamCache: Provider<Cache>,
    @ApplicationScope private val appScope: CoroutineScope,
    @Dispatcher(SegueDispatchers.Main) private val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch(ioDispatcher) {
            // Only a warm-up: if the cache cannot open, the service hits (and reports) it itself.
            try {
                streamCache.get()
            } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                Log.w(TAG, "Could not open the stream cache", e)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    val playing = bridge.isPlaying()
                    appScope.launch { queueSync.checkRemote(isPlayingLocally = playing) }
                }

                override fun onStop(owner: LifecycleOwner) {
                    queueSync.onAppBackground(bridge.queueState())
                }
            },
        )
        appScope.launch { queueSync.adoptions.collect { controller.adoptAutomatically(it) } }
        appScope.launch {
            var wasOffline = offlineMode.isOffline.value
            offlineMode.isOffline.collect { isOffline ->
                if (wasOffline && !isOffline) {
                    withContext(mainDispatcher) { queueSync.onNetworkRestored(bridge.queueState()) }
                }
                wasOffline = isOffline
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackInitializer"
    }
}
