package com.quantumff.segue.core.playback.service

import com.quantumff.segue.core.playback.sync.QueueState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one in-process seam from app-level code to the running service's player, for what has to be
 * answered at once, connected or not: the queue to flush when the app goes to the background, and
 * whether music is playing (auto adopt must not interrupt it). It only reads; everything that
 * changes the player goes through the media session ([com.quantumff.segue.core.playback.PlaybackController]).
 * The service attaches its player while it lives. Use on the main thread.
 */
@Singleton
class PlaybackServiceBridge @Inject constructor() {
    private var player: SeguePlayer? = null

    internal fun attach(player: SeguePlayer) {
        this.player = player
    }

    /** Detaches [player], unless a newer service attached its own since. */
    internal fun detach(player: SeguePlayer) {
        if (this.player === player) this.player = null
    }

    fun queueState(): QueueState? = player?.takeIf { it.mediaItemCount > 0 }?.queueState()

    fun isPlaying(): Boolean = player?.isPlaying == true
}
