package com.quantumff.segue.core.playback.scrobble

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.quantumff.segue.core.data.repository.ScrobbleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feeds player events to [ScrobblePolicy] and carries out its actions. While a track plays, one
 * timer is armed for the moment it will count as played; nothing ticks.
 *
 * [timerScope] runs on the player thread and dies with the service; [sendScope] outlives it so a
 * play recorded right before the service stops is still stored.
 */
class Scrobbler(
    private val player: Player,
    private val repository: ScrobbleRepository,
    private val timerScope: CoroutineScope,
    private val sendScope: CoroutineScope,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : Player.Listener {
    private val policy = ScrobblePolicy()
    private var timer: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val duration = mediaItem?.mediaMetadata?.durationMs ?: player.duration.takeIf { it != C.TIME_UNSET } ?: 0
        handle(policy.onTrackChanged(mediaItem?.mediaId, duration, player.isPlaying, elapsedClock(), wallClock()))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handle(policy.onPlayingChanged(isPlaying, elapsedClock(), wallClock()))
    }

    private fun handle(actions: List<ScrobbleAction>) {
        actions.forEach { action ->
            sendScope.launch {
                when (action) {
                    is ScrobbleAction.NowPlaying -> repository.nowPlaying(action.songId)
                    is ScrobbleAction.Submit -> repository.submit(action.songId, action.playedAtMillis)
                }
            }
        }
        timer?.cancel()
        timer = policy.millisUntilSubmission(elapsedClock())?.let { wait ->
            timerScope.launch {
                delay(wait)
                handle(policy.check(elapsedClock()))
            }
        }
    }
}
