package com.quantumff.segue.core.playback.service

import com.quantumff.segue.core.playback.sync.QueueState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What app-level code needs from the running service's player: the queue to flush when the app goes
 * to the background, and whether music is playing (auto adopt must not interrupt it). Set by the
 * service while it lives; read and written on the main thread.
 */
@Singleton
class PlaybackServiceBridge @Inject constructor() {
    internal var player: SeguePlayer? = null

    fun queueState(): QueueState? = player?.takeIf { it.mediaItemCount > 0 }?.queueState()

    fun isPlaying(): Boolean = player?.isPlaying == true
}
