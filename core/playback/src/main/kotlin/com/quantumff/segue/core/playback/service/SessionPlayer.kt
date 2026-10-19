package com.quantumff.segue.core.playback.service

import androidx.media3.common.ForwardingPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The player the media session drives, so every controller's play goes through it: the app, the
 * notification, widgets, Bluetooth and Android Auto. The service's own modules use [player]
 * directly.
 *
 * When [checkFirst] says so (play pressed outside the app, online), play waits for [beforePlay],
 * which lets queue sync put another device's newer queue in the player (docs/adr/0004), and then
 * plays whatever is queued. A pause meanwhile wins: the check still finishes, but nothing plays.
 * Call on the player's thread.
 */
class SessionPlayer(
    private val player: SeguePlayer,
    private val scope: CoroutineScope,
    private val checkFirst: () -> Boolean,
    private val beforePlay: suspend () -> Unit,
) : ForwardingPlayer(player) {
    private var check: Job? = null
    private var playWhenChecked = false

    override fun play() {
        if (check?.isActive == true) {
            playWhenChecked = true
            return
        }
        if (player.playWhenReady || !checkFirst()) {
            player.play()
            return
        }
        playWhenChecked = true
        check = scope.launch {
            beforePlay()
            if (playWhenChecked) player.play()
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()

    override fun pause() {
        playWhenChecked = false
        player.pause()
    }

    override fun stop() {
        playWhenChecked = false
        player.stop()
    }
}
