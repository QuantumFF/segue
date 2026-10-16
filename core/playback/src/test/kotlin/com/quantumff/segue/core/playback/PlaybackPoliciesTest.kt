package com.quantumff.segue.core.playback

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.cache.NetworkType
import com.quantumff.segue.core.playback.cache.PrefetchPolicy
import com.quantumff.segue.core.playback.mix.InstantMix
import com.quantumff.segue.core.playback.shuffle.AlbumPicker
import com.quantumff.segue.core.playback.sleep.SleepTimerPolicy
import kotlin.random.Random
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

class AlbumPickerTest {
    @Test
    fun `picks distinct random albums up to the limit`() {
        val picked = AlbumPicker.pick((1..100).map { "al$it" }, count = 10, random = Random(1))
        assertThat(picked).hasSize(10)
        assertThat(picked.toSet()).hasSize(10)
        assertThat(AlbumPicker.pick((1..100).map { "al$it" }, 10, Random(2))).isNotEqualTo(picked)
    }

    @Test
    fun `fewer albums than the limit are all used, shuffled`() {
        val picked = AlbumPicker.pick(listOf("a", "b", "c"), count = 10, random = Random(1))
        assertThat(picked).containsExactly("a", "b", "c")
    }

    @Test
    fun `songs are grouped into whole albums in the picked order, keeping disc and track order`() {
        fun song(id: String, album: String, disc: Int, track: Int) = Song(
            id = id,
            title = id,
            albumId = album,
            album = album,
            artist = null,
            artistId = null,
            discNumber = disc,
            track = track,
        )
        val songs = listOf(
            song("b2", "B", 1, 2),
            song("a1", "A", 1, 1),
            song("b1", "B", 1, 1),
            song("a3", "A", 2, 1),
            song("a2", "A", 1, 2),
        )
        val queue = AlbumPicker.queueInAlbumOrder(songs, albumOrder = listOf("B", "A"))
        assertThat(queue.map { it.id }).containsExactly("b1", "b2", "a1", "a2", "a3").inOrder()
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
