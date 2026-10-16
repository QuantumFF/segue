package com.quantumff.segue.core.playback

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.cache.NetworkType
import com.quantumff.segue.core.playback.cache.PrefetchPolicy
import com.quantumff.segue.core.playback.mix.InstantMix
import com.quantumff.segue.core.playback.sleep.SleepTimerPolicy
import org.junit.Test

class SleepTimerPolicyTest {
    @Test
    fun `end of track always pauses at the end of the current item`() {
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(SleepTimer.EndOfTrack, "a", "a", hasNext = true)).isTrue()
    }

    @Test
    fun `end of album pauses only when the next track is from another album or there is none`() {
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(SleepTimer.EndOfAlbum, "a", "a", hasNext = true)).isFalse()
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(SleepTimer.EndOfAlbum, "a", "b", hasNext = true)).isTrue()
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(SleepTimer.EndOfAlbum, "a", null, hasNext = false)).isTrue()
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(SleepTimer.EndOfAlbum, null, null, hasNext = true)).isTrue()
    }

    @Test
    fun `a timed timer or none does not pause at item ends`() {
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(SleepTimer.AtTime(5), "a", "b", true)).isFalse()
        assertThat(SleepTimerPolicy.pauseAtEndOfCurrentItem(null, "a", "b", true)).isFalse()
    }
}

class InstantMixTest {
    private fun song(id: String) =
        Song(id = id, title = id, albumId = null, album = null, artist = null, artistId = null)

    @Test
    fun `starts with the seed, then similar songs, then top songs to fill up, without duplicates`() {
        val mix = InstantMix.build(
            seed = song("seed"),
            similar = listOf(song("s1"), song("seed"), song("s2"), song("s1")),
            top = listOf(song("t1"), song("s2"), song("t2")),
            limit = 5,
        )
        assertThat(mix.map { it.id }).containsExactly("seed", "s1", "s2", "t1", "t2").inOrder()
    }

    @Test
    fun `top songs are only used when similar songs run short`() {
        val mix = InstantMix.build(null, similar = (1..10).map { song("s$it") }, top = listOf(song("t")), limit = 4)
        assertThat(mix.map { it.id }).containsExactly("s1", "s2", "s3", "s4").inOrder()
    }
}

class PrefetchPolicyTest {
    @Test
    fun `prefetches two tracks ahead on unmetered networks and one on metered`() {
        val next = listOf("n1", "n2", "n3")
        assertThat(PrefetchPolicy.targets(next, NetworkType.Unmetered)).containsExactly("n1", "n2").inOrder()
        assertThat(PrefetchPolicy.targets(next, NetworkType.Metered)).containsExactly("n1")
        assertThat(PrefetchPolicy.targets(next, NetworkType.None)).isEmpty()
    }
}
