package com.quantumff.segue.core.data.play

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlayTargetSongsTest {
    private val albums = FakeAlbumRepository()
    private val playlists = FakePlaylistRepository()
    private val artists = FakeArtistRepository()
    private val targets = PlayTargetSongs(albums, playlists, artists)

    private val songs = listOf(TestData.song("s1"), TestData.song("s2"))

    @Test
    fun `songs play as given`() = runTest {
        assertThat(targets.songsFor(PlayTarget.Songs(songs, startIndex = 1))).isEqualTo(songs)
    }

    @Test
    fun `an album plays its songs`() = runTest {
        albums.songsByAlbum.value = mapOf("al" to songs)

        assertThat(targets.songsFor(PlayTarget.Album("al"))).isEqualTo(songs)
    }

    @Test
    fun `a playlist without cached songs is fetched first`() = runTest {
        playlists.songsByPlaylist.value = mapOf("p" to songs)

        assertThat(targets.songsFor(PlayTarget.Playlist("p"))).isEqualTo(songs)
        assertThat(playlists.refreshed).isEmpty()

        assertThat(targets.songsFor(PlayTarget.Playlist("empty"))).isEmpty()
        assertThat(playlists.refreshed).containsExactly("empty")
    }

    @Test
    fun `an artist plays their top songs`() = runTest {
        artists.topSongs = songs
        artists.albumsByArtist.value = mapOf("ar" to listOf(TestData.album("al", artistId = "ar")))
        albums.songsByAlbum.value = mapOf("al" to listOf(TestData.song("other")))

        assertThat(targets.songsFor(PlayTarget.Artist("ar"))).isEqualTo(songs)
    }

    @Test
    fun `an artist without top songs plays their albums in page order`() = runTest {
        artists.albumsByArtist.value = mapOf(
            "ar" to listOf(TestData.album("guest", artistId = "other"), TestData.album("own", artistId = "ar")),
        )
        albums.songsByAlbum.value = mapOf(
            "own" to listOf(TestData.song("o1")),
            "guest" to listOf(TestData.song("g1")),
        )

        assertThat(targets.songsFor(PlayTarget.Artist("ar")).map { it.id }).containsExactly("o1", "g1").inOrder()
    }

    @Test
    fun `an artist plays their albums when top songs cannot be fetched`() = runTest {
        artists.error = RemoteException.Unreachable(IOException("offline"))
        artists.albumsByArtist.value = mapOf("ar" to listOf(TestData.album("own", artistId = "ar")))
        albums.songsByAlbum.value = mapOf("own" to songs)

        assertThat(targets.songsFor(PlayTarget.Artist("ar"))).isEqualTo(songs)
    }

    @Test
    fun `several targets play each one's songs in turn`() = runTest {
        albums.songsByAlbum.value = mapOf("a" to listOf(TestData.song("a1")), "b" to listOf(TestData.song("b1")))
        val target = PlayTarget.Each(
            listOf(PlayTarget.Album("b"), PlayTarget.Songs(listOf(TestData.song("s"))), PlayTarget.Album("a")),
        )

        assertThat(targets.songsFor(target).map { it.id }).containsExactly("b1", "s", "a1").inOrder()
    }
}
