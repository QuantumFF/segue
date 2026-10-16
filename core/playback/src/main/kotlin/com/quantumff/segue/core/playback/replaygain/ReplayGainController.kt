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
 * Switching boost moves the gain between player volume and the processor. During playback that
 * comes with a seek in place: it flushes the audio already buffered at the old level, and the
 * processor joins or leaves the audio chain at that flush. Mode and target changes need no seek.
 */
class ReplayGainController(
    private val player: Player,
    private val processor: ReplayGainProcessor,
    private val offload: ScreenOffOffload,
) : Player.Listener {
    private var settings = ReplayGainSettings()

    fun update(settings: ReplayGainSettings) {
        val boostChanged = settings.boost != this.settings.boost
        this.settings = settings
        processor.settings = settings
        offload.processingRequired = settings.boost
        applyVolume()
        if (boostChanged && player.isLoaded()) {
            Log.d(REPLAY_GAIN_TAG, "Boost ${if (settings.boost) "on" else "off"}: seeking in place to flush")
            player.seekTo(player.currentMediaItemIndex, player.currentPosition)
        }
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

    /** Media is prepared, so the audio sink may hold buffered audio. */
    private fun Player.isLoaded() = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
}
