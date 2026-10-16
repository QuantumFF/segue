package com.quantumff.segue.core.playback.replaygain

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.quantumff.segue.core.playback.media.replayGain
import com.quantumff.segue.core.playback.service.ScreenOffOffload
import kotlin.math.log10

/**
 * Applies ReplayGain on the main thread: player volume for the current track, the settings for
 * [processor], and no offload while boost needs the processor. Add it as a player listener so the
 * volume follows track changes.
 *
 * Switching boost during a track briefly plays the audio already buffered at the old level: the
 * volume changes at once, the processor from its next buffer.
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
        Log.d(TAG, "Volume %.2f dB for %s (%s)".format(20 * log10(volume), item?.mediaId, settings))
        player.volume = volume
    }

    private companion object {
        const val TAG = "SegueReplayGain"
    }
}
