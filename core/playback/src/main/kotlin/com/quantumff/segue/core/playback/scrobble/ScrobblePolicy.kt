package com.quantumff.segue.core.playback.scrobble

import kotlin.math.max
import kotlin.math.min

sealed interface ScrobbleAction {
    /** `scrobble?submission=false`: shows the track as playing now. */
    data class NowPlaying(val songId: String) : ScrobbleAction

    /** `scrobble?submission=true`: records a play that started at [playedAtMillis] (epoch). */
    data class Submit(val songId: String, val playedAtMillis: Long) : ScrobbleAction
}

/**
 * When to scrobble, following the Last.fm rules Navidrome forwards to: "now playing" when a track
 * starts playing; a play once it has played for half its length or four minutes, whichever comes
 * first. Only time spent actually playing counts, so pauses and seeks do not. Tracks shorter
 * than 30 seconds are never submitted; an unknown duration waits the full four minutes.
 *
 * Times are passed in: elapsed is a monotonic clock for measuring play time, wall is epoch time
 * for the play's timestamp. Not thread-safe; call from the player thread.
 */
class ScrobblePolicy {
    private var songId: String? = null
    private var thresholdMillis: Long? = null
    private var playedMillis = 0L
    private var playingSince: Long? = null
    private var startedAtWall: Long? = null
    private var submitted = false

    /** A new track (or a repeat of the same one) became current. [songId] null means nothing is current. */
    fun onTrackChanged(
        songId: String?,
        durationMillis: Long,
        isPlaying: Boolean,
        elapsedMillis: Long,
        wallMillis: Long,
    ): List<ScrobbleAction> {
        val actions = stopPlaying(elapsedMillis).toMutableList()
        this.songId = songId
        thresholdMillis = threshold(durationMillis)
        playedMillis = 0
        playingSince = null
        startedAtWall = null
        submitted = false
        if (songId != null && isPlaying) actions += startPlaying(elapsedMillis, wallMillis)
        return actions
    }

    fun onPlayingChanged(isPlaying: Boolean, elapsedMillis: Long, wallMillis: Long): List<ScrobbleAction> = when {
        songId == null -> emptyList()
        isPlaying && playingSince == null -> startPlaying(elapsedMillis, wallMillis)
        !isPlaying -> stopPlaying(elapsedMillis)
        else -> emptyList()
    }

    /** Submits if the threshold has been reached; call when [millisUntilSubmission] has passed. */
    fun check(elapsedMillis: Long): List<ScrobbleAction> = dueActions(played(elapsedMillis))

    /** How long until the play counts, if playback continues; null when paused, ineligible, or done. */
    fun millisUntilSubmission(elapsedMillis: Long): Long? {
        val threshold = thresholdMillis ?: return null
        if (playingSince == null || submitted) return null
        return max(0, threshold - played(elapsedMillis))
    }

    private fun startPlaying(elapsedMillis: Long, wallMillis: Long): List<ScrobbleAction> {
        playingSince = elapsedMillis
        if (startedAtWall != null) return emptyList()
        startedAtWall = wallMillis
        return listOf(ScrobbleAction.NowPlaying(checkNotNull(songId)))
    }

    private fun stopPlaying(elapsedMillis: Long): List<ScrobbleAction> {
        val since = playingSince ?: return emptyList()
        playedMillis += elapsedMillis - since
        playingSince = null
        return dueActions(playedMillis)
    }

    private fun dueActions(played: Long): List<ScrobbleAction> {
        val threshold = thresholdMillis ?: return emptyList()
        val id = songId ?: return emptyList()
        val startedAt = startedAtWall ?: return emptyList()
        if (submitted || played < threshold) return emptyList()
        submitted = true
        return listOf(ScrobbleAction.Submit(id, startedAt))
    }

    private fun played(elapsedMillis: Long): Long = playedMillis + (playingSince?.let { elapsedMillis - it } ?: 0)

    private fun threshold(durationMillis: Long): Long? = when {
        durationMillis <= 0 -> MAX_THRESHOLD_MILLIS
        durationMillis < MIN_DURATION_MILLIS -> null
        else -> min(durationMillis / 2, MAX_THRESHOLD_MILLIS)
    }

    private companion object {
        const val MAX_THRESHOLD_MILLIS = 4 * 60_000L
        const val MIN_DURATION_MILLIS = 30_000L
    }
}
