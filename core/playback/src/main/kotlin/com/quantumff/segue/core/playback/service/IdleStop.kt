package com.quantumff.segue.core.playback.service

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stops the service after it has been idle for a while: paused, or stopped at the end of the queue,
 * nothing will play until the user acts. Going idle arms one timer of [timeoutMillis] (read then);
 * playing again disarms it. If the player is still idle when it fires, [onIdleTimeout] runs.
 *
 * Register it as a player listener; runs on the player's thread, as does [scope].
 */
class IdleStop(
    private val player: Player,
    private val scope: CoroutineScope,
    private val timeoutMillis: () -> Long,
    private val onIdleTimeout: () -> Unit,
) : Player.Listener {
    private var timer: Job? = null

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = update()

    override fun onPlaybackStateChanged(playbackState: Int) = update()

    private fun isIdle() = !player.playWhenReady ||
        player.playbackState == Player.STATE_ENDED ||
        player.playbackState == Player.STATE_IDLE

    private fun update() {
        if (!isIdle()) {
            timer?.cancel()
            timer = null
            return
        }
        if (timer?.isActive == true) return
        timer = scope.launch {
            delay(timeoutMillis())
            if (isIdle()) onIdleTimeout()
        }
    }
}
