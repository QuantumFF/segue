package com.quantumff.segue.core.playback.scrobble

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.playback.scrobble.ScrobbleAction.NowPlaying
import com.quantumff.segue.core.playback.scrobble.ScrobbleAction.Submit
import org.junit.Test

class ScrobblePolicyTest {
    private val policy = ScrobblePolicy()

    /** Elapsed (monotonic) time; wall time is elapsed + 1_000_000 so they differ. */
    private var now = 0L
    private val wall get() = now + 1_000_000

    private fun start(id: String, durationMillis: Long, playing: Boolean = true) =
        policy.onTrackChanged(id, durationMillis, playing, now, wall)

    private fun play() = policy.onPlayingChanged(true, now, wall)

    private fun pause() = policy.onPlayingChanged(false, now, wall)

    private fun check() = policy.check(now)

    @Test
    fun `now playing is sent when a track starts playing`() {
        assertThat(start("a", 200_000)).containsExactly(NowPlaying("a"))
    }

    @Test
    fun `a track loaded paused sends now playing only once it plays`() {
        assertThat(start("a", 200_000, playing = false)).isEmpty()
        now += 5_000
        assertThat(play()).containsExactly(NowPlaying("a"))
        now += 5_000
        pause()
        assertThat(play()).isEmpty()
    }

    @Test
    fun `submits after half the track has played`() {
        start("a", 200_000)
        now += 99_999
        assertThat(check()).isEmpty()
        now += 1
        assertThat(check()).containsExactly(Submit("a", playedAtMillis = 1_000_000))
        now += 50_000
        assertThat(check()).isEmpty()
    }

    @Test
    fun `long tracks submit after four minutes`() {
        start("a", 30 * 60_000)
        assertThat(policy.millisUntilSubmission(now)).isEqualTo(240_000)
        now += 240_000
        assertThat(check()).containsExactly(Submit("a", 1_000_000))
    }

    @Test
    fun `paused time does not count`() {
        start("a", 200_000)
        now += 60_000
        assertThat(pause()).isEmpty()
        now += 600_000
        assertThat(policy.millisUntilSubmission(now)).isNull()
        play()
        assertThat(policy.millisUntilSubmission(now)).isEqualTo(40_000)
        now += 40_000
        assertThat(check()).containsExactly(Submit("a", 1_000_000))
    }

    @Test
    fun `seeking forward does not count as listening`() {
        start("a", 200_000)
        now += 10_000
        // The user seeks to the end: no time passes, and the track ends.
        assertThat(start("b", 200_000)).containsExactly(NowPlaying("b"))
    }

    @Test
    fun `pausing past the threshold submits`() {
        start("a", 200_000)
        now += 150_000
        assertThat(pause()).containsExactly(Submit("a", 1_000_000))
    }

    @Test
    fun `changing track past the threshold submits the previous track first`() {
        start("a", 200_000)
        now += 120_000
        assertThat(start("b", 200_000)).containsExactly(Submit("a", 1_000_000), NowPlaying("b")).inOrder()
    }

    @Test
    fun `a repeated track is a new play`() {
        start("a", 100_000)
        now += 100_000
        start("a", 100_000)
        now += 50_000
        assertThat(check()).containsExactly(Submit("a", 1_100_000))
    }

    @Test
    fun `tracks shorter than 30 seconds are never submitted`() {
        start("a", 20_000)
        assertThat(policy.millisUntilSubmission(now)).isNull()
        now += 20_000
        assertThat(check()).isEmpty()
        assertThat(pause()).isEmpty()
    }

    @Test
    fun `an unknown duration waits four minutes`() {
        start("a", 0)
        now += 240_000
        assertThat(check()).containsExactly(Submit("a", 1_000_000))
    }

    @Test
    fun `the play time is the moment the track first started playing`() {
        start("a", 200_000, playing = false)
        now += 30_000
        play()
        now += 100_000
        assertThat(check()).containsExactly(Submit("a", 1_030_000))
    }

    @Test
    fun `stopping with no track clears state`() {
        start("a", 200_000)
        now += 10_000
        assertThat(policy.onTrackChanged(null, 0, false, now, wall)).isEmpty()
        assertThat(policy.millisUntilSubmission(now)).isNull()
    }
}
