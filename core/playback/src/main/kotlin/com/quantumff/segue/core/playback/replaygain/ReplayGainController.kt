package com.quantumff.segue.core.playback.replaygain

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.quantumff.segue.core.playback.media.replayGain
import com.quantumff.segue.core.playback.service.ScreenOffOffload

/**
 * Applies ReplayGain on the main thread: player volume for the current track, the settings for
 * [processor], and no offload while boost needs the processor. Add it as a player listener so the
 * volume follows track changes.
 *
 * Switching boost moves the gain between player volume and the processor. The volume changes at
 * once and the processor from its next buffer, so the audio already in the output buffer (well
 * under a second) briefly plays at the wrong level. A seek in place would flush it, but ExoPlayer
 * skips a seek to the current position, so that is left as is: it only happens on the switch.
 */
class ReplayGainController(
    private val player: Player,
    private val processor: ReplayGainProcessor,
    private val offload: ScreenOffOffload,
) : Player.Listener {
    private var settings = ReplayGainSettings()

    fun update(settings: ReplayGainSettings) {
        this.settings = settings
        processor.settings = settings
        offload.processingRequired = settings.boost
        applyVolume()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = applyVolume()

    private fun applyVolume() {
        val item = player.currentMediaItem
        val volume = ReplayGainLevel.of(item?.replayGain, settings).volume
        Log.d(
            REPLAY_GAIN_TAG,
            "Volume %.2f dB for %s (%s)".format(ReplayGainLevel.decibels(volume), item?.mediaId, settings),
        )
        player.volume = volume
    }
}
