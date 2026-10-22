package com.quantumff.segue.core.data.play

/**
 * Pause and play of the queue as it is, for screens outside the player: an album, playlist or
 * artist page's hero Play while the page is the queue source ([com.quantumff.segue.core.model.HeroPlayback]).
 * To queue music, use [PlayIntents]; to read whether music plays, [PlayerStateSource].
 *
 * The playback module implements it over the playback session. Commands return at once. Tests use
 * the recording fake in `core:testing`.
 */
interface PlayerTransport {
    /** Plays the queue where it is: resumes it, preparing a player that stopped. */
    fun play()

    fun pause()
}
