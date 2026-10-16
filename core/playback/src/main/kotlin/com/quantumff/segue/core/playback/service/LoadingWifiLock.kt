package com.quantumff.segue.core.playback.service

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Holds ExoPlayer's Wi-Fi lock only while it is loading. [C.WAKE_MODE_NETWORK] would hold it for
 * all of playback, which keeps Wi-Fi out of power save (a high-performance lock before Android 14)
 * even while the track plays from the long buffer or the stream cache. The CPU wake lock
 * ([C.WAKE_MODE_LOCAL]) stays for all of playback, and ExoPlayer still drops it while sleeping for
 * audio offload.
 */
internal class LoadingWifiLock(private val player: ExoPlayer) : Player.Listener {
    override fun onIsLoadingChanged(isLoading: Boolean) {
        player.setWakeMode(if (isLoading) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
    }
}
