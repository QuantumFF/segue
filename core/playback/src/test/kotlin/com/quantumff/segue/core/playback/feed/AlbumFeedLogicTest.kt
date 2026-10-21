package com.quantumff.segue.core.playback.feed

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.Song
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AlbumFeedOrderTest {
    private val ids = (1..40).map { "al$it" }

    @Test
    fun `in order keeps the source order`() {
        assertThat(AlbumFeedOrder.order(ids, AlbumOrder.InOrder, seed = 9)).isEqualTo(ids)
    }

    @Test
    fun `a shuffle is a permutation, the same for the same seed, and different for another seed`() {
        val first = AlbumFeedOrder.order(ids, AlbumOrder.Shuffled, seed = 42)

        assertThat(first).containsExactlyElementsIn(ids)
        assertThat(first).isNotEqualTo(ids)
        assertThat(AlbumFeedOrder.order(ids, AlbumOrder.Shuffled, seed = 42)).isEqualTo(first)
        // The input order does not matter: only the seed and the album IDs do.
        assertThat(AlbumFeedOrder.order(ids.reversed(), AlbumOrder.Shuffled, seed = 42)).isEqualTo(first)
        assertThat(AlbumFeedOrder.order(ids, AlbumOrder.Shuffled, seed = 43)).isNotEqualTo(first)
    }

    @Test
    fun `an album added to the library does not reshuffle the others`() {
        val before = AlbumFeedOrder.order(ids, AlbumOrder.Shuffled, seed = 7)
        val after = AlbumFeedOrder.order(ids + "new", AlbumOrder.Shuffled, seed = 7)

        assertThat(after - "new").isEqualTo(before)
    }

    @Test
    fun `a tracks shuffle is a seeded permutation too`() {
        val songs = (1..40).map { "s$it" }
        val first = AlbumFeedOrder.order(songs, AlbumOrder.ShuffledTracks, seed = 42)

        assertThat(first).containsExactlyElementsIn(songs)
        assertThat(first).isNotEqualTo(songs)
        assertThat(AlbumFeedOrder.order(songs.reversed(), AlbumOrder.ShuffledTracks, seed = 42)).isEqualTo(first)
    }

    @Test
    fun `duplicates collapse to one album`() {
        assertThat(AlbumFeedOrder.order(listOf("a", "b", "a"), AlbumOrder.InOrder, 0)).containsExactly("a", "b")
        assertThat(AlbumFeedOrder.order(listOf("a", "b", "a"), AlbumOrder.Shuffled, 0)).hasSize(2)
    }
}

class AlbumFeedWindowsTest {
    /** Album `alN` has N tracks, stored out of order to check disc and track sorting. */
    private val library: Map<String, List<Song>> = (1..12).associate { n ->
        val album = "al$n"
        album to (1..n).map { track -> song("$album-t$track", album, disc = 1, track = track) }.reversed()
    } +
        (
            "twoDiscs" to
                listOf(song("d2t1", "twoDiscs", 2, 1), song("d1t2", "twoDiscs", 1, 2), song("d1t1", "twoDiscs", 1, 1))
            ) +
        ("empty" to emptyList())

    private val requests = mutableListOf<List<String>>()
    private val windows = AlbumFeedWindows { albumIds ->
        requests += albumIds
        albumIds.flatMap { library[it].orEmpty() }.shuffled()
    }

    private fun song(id: String, album: String, disc: Int, track: Int) = Song(
        id = id,
        title = id,
        albumId = album,
        album = album,
        artist = null,
        artistId = null,
        discNumber = disc,
        track = track,
    )

    @Test
    fun `a window takes whole albums until it holds enough tracks`() = runTest {
        val order = (1..12).map { "al$it" }

        val window = windows.next(order, cursor = 0, minTracks = 10)

        // 1 + 2 + 3 + 4 = 10 tracks: four albums, each whole and in track order.
        assertThat(window.songs.map { it.id }).containsExactly(
            "al1-t1", "al2-t1", "al2-t2", "al3-t1", "al3-t2", "al3-t3", "al4-t1", "al4-t2", "al4-t3", "al4-t4",
        ).inOrder()
        assertThat(window.cursor).isEqualTo(4)
        assertThat(window.exhausted).isFalse()
    }

    @Test
    fun `the next window continues at the cursor and reports the end of the source`() = runTest {
        val order = listOf("al3", "al1", "al2")

        val first = windows.next(order, cursor = 0, minTracks = 3)
        val second = windows.next(order, cursor = first.cursor, minTracks = 3)

        assertThat(first.songs.map { it.albumId }.distinct()).containsExactly("al3")
        assertThat(second.songs.map { it.albumId }.distinct()).containsExactly("al1", "al2").inOrder()
        assertThat(second.cursor).isEqualTo(3)
        assertThat(second.exhausted).isTrue()
    }

    @Test
    fun `tracks keep disc then track order`() = runTest {
        val window = windows.next(listOf("twoDiscs"), cursor = 0, minTracks = 1)
        assertThat(window.songs.map { it.id }).containsExactly("d1t1", "d1t2", "d2t1").inOrder()
    }

    @Test
    fun `albums with nothing to play, or already queued, are skipped but used up`() = runTest {
        val window = windows.next(listOf("empty", "al2", "al3", "al1"), cursor = 0, minTracks = 1, skip = setOf("al2"))

        assertThat(window.songs.map { it.albumId }.distinct()).containsExactly("al3")
        assertThat(window.cursor).isEqualTo(3)
    }

    @Test
    fun `songs are read in small batches, not for the whole source`() = runTest {
        val order = (1..12).map { "al$it" }

        windows.next(order, cursor = 0, minTracks = 3)

        assertThat(requests.flatten().size).isAtMost(AlbumFeedWindows.BATCH_ALBUMS)
    }
}

class AlbumFeedPolicyTest {
    @Test
    fun `the feed extends only when few tracks are left`() {
        assertThat(AlbumFeedPolicy.shouldExtend(remaining = 0)).isTrue()
        assertThat(AlbumFeedPolicy.shouldExtend(remaining = AlbumFeedPolicy.LOW_WATER_TRACKS - 1)).isTrue()
        assertThat(AlbumFeedPolicy.shouldExtend(remaining = AlbumFeedPolicy.LOW_WATER_TRACKS)).isFalse()
    }

    @Test
    fun `the fingerprint follows content and order`() {
        val queue = listOf("a", "b", "c")
        assertThat(AlbumFeedPolicy.fingerprint(queue)).isEqualTo(AlbumFeedPolicy.fingerprint(listOf("a", "b", "c")))
        assertThat(AlbumFeedPolicy.fingerprint(queue)).isNotEqualTo(AlbumFeedPolicy.fingerprint(listOf("b", "a", "c")))
        assertThat(AlbumFeedPolicy.fingerprint(queue)).isNotEqualTo(AlbumFeedPolicy.fingerprint(listOf("a", "b")))
        assertThat(AlbumFeedPolicy.fingerprint(listOf("ab", "c")))
            .isNotEqualTo(AlbumFeedPolicy.fingerprint(listOf("a", "bc")))
    }
}
