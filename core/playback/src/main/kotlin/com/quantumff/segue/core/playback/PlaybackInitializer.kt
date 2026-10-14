package com.quantumff.segue.core.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.playback.service.PlaybackServiceBridge
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide playback wiring. Call [start] once from `Application.onCreate`.
 *
 * - App start and return to the foreground: check the server for a newer queue.
 * - App to the background: flush queue sync at once.
 * - Auto-adopted remote queues are handed to the player.
 */
@Singleton
class PlaybackInitializer @Inject constructor(
    private val queueSync: QueueSyncEngine,
    private val bridge: PlaybackServiceBridge,
    private val controller: PlaybackController,
    @ApplicationScope private val appScope: CoroutineScope,
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
    }
}
