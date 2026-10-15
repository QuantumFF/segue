package com.quantumff.segue.core.playback.sync

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.quantumff.segue.core.playback.service.SeguePlayer

/** Where [QueueSyncListener] reports; implemented by [QueueSyncEngine]. */
interface QueueSyncSink {
    fun onQueueEvent(event: QueueEvent, state: QueueState)

    fun onQueueCleared()

    fun onLocalPlaybackStarted()
}

/**
 * Turns player events into queue sync events (docs/PLAN.md, "Server queue sync"): track change,
 * queue edit (including any shuffle change), pause (also a transient one such as a call, and the
 * end of the queue), and seek. App backgrounding is reported by the app, not the player.
 *
 * Playback that simply runs raises none of these, so nothing is saved periodically.
 */
class QueueSyncListener(private val player: SeguePlayer, private val sink: QueueSyncSink) : Player.Listener {
    private var hadItems = false

    /** [SeguePlayer]'s mode callback: Tracks ↔ Albums keeps shuffle enabled, so no player event reports it. */
    fun onShuffleModeChanged() = event(QueueEvent.QueueEdit)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = event(QueueEvent.TrackTransition)

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        val hasItems = player.mediaItemCount > 0
        // Empty after having songs is a deliberate clear, not the empty player before restore.
        if (hadItems && !hasItems) sink.onQueueCleared()
        hadItems = hasItems
        event(QueueEvent.QueueEdit)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = event(QueueEvent.QueueEdit)

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) event(QueueEvent.Seek)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) sink.onLocalPlaybackStarted() else event(QueueEvent.Pause)
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) event(QueueEvent.Pause)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) event(QueueEvent.Pause)
    }

    private fun event(event: QueueEvent) {
        if (player.mediaItemCount > 0) sink.onQueueEvent(event, player.queueState())
    }
}
