package com.quantumff.segue.core.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.playback.service.PlaybackServiceBridge
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import javax.inject.Inject
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
 */
@Singleton
class PlaybackInitializer @Inject constructor(
    private val queueSync: QueueSyncEngine,
    private val bridge: PlaybackServiceBridge,
    private val controller: PlaybackController,
    private val offlineMode: OfflineModeRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @Dispatcher(SegueDispatchers.Main) private val mainDispatcher: CoroutineDispatcher,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
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
        appScope.launch { queueSync.adoptions.collect { controller.adopt(it) } }
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
}
