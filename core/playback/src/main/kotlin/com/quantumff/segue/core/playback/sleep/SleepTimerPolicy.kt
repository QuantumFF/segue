package com.quantumff.segue.core.playback.sleep

import com.quantumff.segue.core.model.SleepTimer

object SleepTimerPolicy {
    /**
     * Whether the player should pause when the current item ends. [nextAlbumId] is the album of
     * the next item in play order; [hasNext] is false at the end of the queue. A track without an
     * album counts as an album of its own.
     */
    fun pauseAtEndOfCurrentItem(
        timer: SleepTimer?,
        currentAlbumId: String?,
        nextAlbumId: String?,
        hasNext: Boolean,
    ): Boolean = when (timer) {
        SleepTimer.EndOfTrack -> true
        SleepTimer.EndOfAlbum -> !hasNext || currentAlbumId == null || currentAlbumId != nextAlbumId
        is SleepTimer.AtTime, null -> false
    }
}
