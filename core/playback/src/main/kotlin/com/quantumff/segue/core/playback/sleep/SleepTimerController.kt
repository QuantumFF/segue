package com.quantumff.segue.core.playback.sleep

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.playback.media.albumKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the sleep timer inside the service. A timed timer is one delayed pause. "End of track" and
 * "end of album" use ExoPlayer's pause-at-end-of-item, switched on only for the item after which
 * playback should stop, so the pause lands exactly at the boundary.
 */
class SleepTimerController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val onChanged: (SleepTimer?) -> Unit,
) : Player.Listener {
    var timer: SleepTimer? = null
        private set
    private var job: Job? = null

    fun set(newTimer: SleepTimer?) {
        job?.cancel()
        job = null
        timer = newTimer
        if (newTimer is SleepTimer.AtTime) {
            job = scope.launch {
                delay(newTimer.endsAtMillis - wallClock())
                player.pause()
                set(null)
            }
        }
        updatePauseAtEnd()
        onChanged(newTimer)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updatePauseAtEnd()

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = updatePauseAtEnd()

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && timer != null) {
            set(null)
        }
    }

    private fun updatePauseAtEnd() {
        val next = player.nextMediaItemIndex
        val hasNext = next != C.INDEX_UNSET
        val pause = SleepTimerPolicy.pauseAtEndOfCurrentItem(
            timer = timer,
            currentAlbumId = player.currentMediaItem?.albumKey,
            nextAlbumId = if (hasNext) player.getMediaItemAt(next).albumKey else null,
            hasNext = hasNext,
        )
        if (player.pauseAtEndOfMediaItems != pause) player.pauseAtEndOfMediaItems = pause
    }
}
