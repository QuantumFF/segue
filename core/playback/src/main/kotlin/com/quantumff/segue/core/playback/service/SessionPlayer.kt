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
 * When [shouldSyncFirst] says so (play pressed outside the app, online), play waits for
 * [syncQueue], which lets queue sync put another device's newer queue in the player
 * (docs/adr/0004), and then plays whatever is queued. A pause meanwhile wins: the sync still
 * finishes, but nothing plays. Call on the player's thread.
 */
class SessionPlayer(
    private val player: SeguePlayer,
    private val scope: CoroutineScope,
    private val shouldSyncFirst: () -> Boolean,
    private val syncQueue: suspend () -> Unit,
) : ForwardingPlayer(player) {
    private var sync: Job? = null
    private var playAfterSync = false

    override fun play() {
        if (sync?.isActive == true) {
            playAfterSync = true
            return
        }
        if (player.playWhenReady || !shouldSyncFirst()) {
            player.play()
            return
        }
        playAfterSync = true
        sync = scope.launch {
            syncQueue()
            if (playAfterSync) player.play()
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()

    override fun pause() {
        playAfterSync = false
        player.pause()
    }

    override fun stop() {
        playAfterSync = false
        player.stop()
    }
}
