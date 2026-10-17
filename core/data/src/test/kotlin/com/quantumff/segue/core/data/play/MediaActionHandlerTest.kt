package com.quantumff.segue.core.data.play

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaActionHandlerTest {
    private val intents = RecordingPlayIntents()
    private val downloads = FakeDownloadRepository()
    private val stars = FakeStarRatingRepository()
    private val albums = FakeAlbumRepository().apply {
        songsByAlbum.value = mapOf("al" to listOf(TestData.song("s1"), TestData.song("s2")))
    }
    private val handler = mediaActionHandler(intents, downloads, stars, albums)

    /** Everything the screen was asked to do. */
    private val hostCalls = mutableListOf<Any>()
    private val host = MediaActionHost(
        onAlbumClick = { hostCalls += "album $it" },
        onArtistClick = { hostCalls += "artist $it" },
        onAddToPlaylist = { hostCalls.add(it) },
        onScreenAction = { hostCalls += it },
    )

    private fun effects() =
        listOf(intents.intents.toList(), stars.starred.toMap(), downloads.pins.value, hostCalls.toList())

    /**
     * Runs [action] on an entity pinned or not ([pin]) and asserts it did something: a play intent,
     * a star, a pin change or a host call.
     */
    private suspend fun assertHandled(
        menu: String,
        action: MediaAction,
        pin: Pair<PinKind, String>?,
        run: suspend () -> MediaActionFailure?,
    ) {
        stars.starred.clear()
        downloads.pins.value = pin?.let { mapOf(it to DownloadStatus.Downloaded) }.orEmpty()
        val before = effects()
        assertThat(run()).isNull()
        assertWithMessage("$action in the $menu menu does nothing").that(effects()).isNotEqualTo(before)
    }

    @Test
    fun `every album action offered is handled`() = runTest {
        for (starred in BOOLEANS) {
            for (hasArtist in BOOLEANS) {
                for (downloaded in BOOLEANS) {
                    val album = TestData.album("al", artistId = if (hasArtist) "ar" else null)
                    for (action in MediaActions.album(starred, hasArtist, downloaded)) {
                        val pin = (PinKind.Album to album.id).takeIf { downloaded }
                        assertHandled("album", action, pin) { handler.album(album, action, host) }
                    }
                }
            }
        }
    }

    @Test
    fun `every song action offered is handled`() = runTest {
        for (starred in BOOLEANS) {
            for (hasAlbum in BOOLEANS) {
                for (hasArtist in BOOLEANS) {
                    for (inPlaylist in BOOLEANS) {
                        val song = TestData.song(
                            "s",
                            albumId = if (hasAlbum) "al" else null,
                            artistId = if (hasArtist) "ar" else null,
                        )
                        for (action in MediaActions.song(starred, hasAlbum, hasArtist, inPlaylist)) {
                            assertHandled("song", action, pin = null) { handler.song(song, action, host) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `every artist action offered is handled`() = runTest {
        val artist = TestData.artist("ar")
        for (starred in BOOLEANS) {
            for (downloaded in BOOLEANS) {
                for (action in MediaActions.artist(starred, downloaded)) {
                    val pin = (PinKind.Artist to artist.id).takeIf { downloaded }
                    assertHandled("artist", action, pin) { handler.artist(artist, action, host) }
                }
            }
        }
    }

    @Test
    fun `every playlist action offered is handled`() = runTest {
        val playlist = TestData.playlist("p")
        for (editable in BOOLEANS) {
            for (downloaded in BOOLEANS) {
                for (action in MediaActions.playlist(editable, downloaded)) {
                    val pin = (PinKind.Playlist to playlist.id).takeIf { downloaded }
                    assertHandled("playlist", action, pin) { handler.playlist(playlist, action, host) }
                }
            }
        }
    }

    @Test
    fun `every selection action offered is handled`() = runTest {
        for (kind in MediaKind.entries) {
            for (starred in BOOLEANS) {
                val starredAt = 1L.takeIf { starred }
                val albumList = listOf("al", "al2").map { TestData.album(it, starredAt = starredAt) }
                val artistList = listOf("ar", "ar2").map { TestData.artist(it, starredAt = starredAt) }
                val songList = listOf("s", "s2").map { TestData.song(it, starredAt = starredAt) }
                for (downloaded in BOOLEANS) {
                    val pinKind = PinKind.valueOf(kind.name)
                    val pin = (pinKind to if (kind == MediaKind.Album) "al" else "ar").takeIf { downloaded }
                    for (action in MediaActions.selection(kind, starred, downloaded)) {
                        assertHandled("$kind selection", action, pin) {
                            when (kind) {
                                MediaKind.Album -> handler.albums(albumList, action, host)
                                MediaKind.Artist -> handler.artists(artistList, action, host)
                                MediaKind.Song -> handler.songs(songList, action, host)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `selected albums play in the order given, shuffle albums keeps each one whole`() = runTest {
        val selected = listOf("c", "a", "b").map { TestData.album(it) }

        handler.albums(selected, MediaAction.Play, host)
        handler.albums(selected, MediaAction.AddToQueue, host)
        handler.albums(selected, MediaAction.ShuffleAlbums, host)

        val inOrder = PlayTarget.Each(listOf("c", "a", "b").map { PlayTarget.Album(it) })
        assertThat(intents.plays.take(2)).containsExactly(
            RecordingPlayIntents.Play(inOrder, QueuePlacement.Replace, false),
            RecordingPlayIntents.Play(inOrder, QueuePlacement.End, false),
        ).inOrder()
        val shuffled = intents.plays[2]
        assertThat(shuffled.shuffle).isFalse()
        assertThat((shuffled.target as PlayTarget.Each).targets).containsExactlyElementsIn(inOrder.targets)
    }

    @Test
    fun `selected songs play as one list`() = runTest {
        val songs = listOf(TestData.song("s2"), TestData.song("s1"))

        handler.songs(songs, MediaAction.Shuffle, host)

        assertThat(intents.plays).containsExactly(
            RecordingPlayIntents.Play(PlayTarget.Songs(songs), QueuePlacement.Replace, true),
        )
    }

    @Test
    fun `a selection's favorite and download skip the items already starred or pinned`() = runTest {
        downloads.pins.value = mapOf((PinKind.Album to "pinned") to DownloadStatus.Downloaded)
        val selected = listOf(TestData.album("starred", starredAt = 1L), TestData.album("pinned"))

        handler.albums(selected, MediaAction.Favorite, host)
        handler.albums(selected, MediaAction.Download, host)

        assertThat(stars.starred).containsExactly(MediaKind.Album to "pinned", true)
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Album to "pinned", PinKind.Album to "starred")
    }

    @Test
    fun `a selection's remove download unpins every item`() = runTest {
        downloads.pins.value = mapOf(
            (PinKind.Artist to "a") to DownloadStatus.Downloaded,
            (PinKind.Artist to "b") to DownloadStatus.Downloaded,
        )

        handler.artists(listOf(TestData.artist("a"), TestData.artist("b")), MediaAction.RemoveDownload, host)

        assertThat(downloads.pins.value).isEmpty()
    }

    @Test
    fun `a selection reports a failure once and still tries every item`() = runTest {
        stars.error = RemoteException.Unreachable(IOException("down"))

        val failure = handler.songs(listOf(TestData.song("a"), TestData.song("b")), MediaAction.Favorite, host)

        assertThat(failure).isEqualTo(MediaActionFailure.FavoriteFailed)
        assertThat(stars.starCalls).isEqualTo(2)
    }

    @Test
    fun `album play actions queue the album, shuffle albums queues its artist's`() = runTest {
        val album = TestData.album("al", artistId = "ar")

        for (action in listOf(MediaAction.Play, MediaAction.Shuffle, MediaAction.PlayNext, MediaAction.AddToQueue)) {
            handler.album(album, action, host)
        }
        handler.album(album, MediaAction.ShuffleArtistAlbums, host)

        val target = PlayTarget.Album("al")
        assertThat(intents.plays).containsExactly(
            RecordingPlayIntents.Play(target, QueuePlacement.Replace, false),
            RecordingPlayIntents.Play(target, QueuePlacement.Replace, true),
            RecordingPlayIntents.Play(target, QueuePlacement.Next, false),
            RecordingPlayIntents.Play(target, QueuePlacement.End, false),
        ).inOrder()
        assertThat(intents.albumRequests).containsExactly(
            AlbumPlayRequest(AlbumSource.Artist("ar"), AlbumOrder.Shuffled),
        )
    }

    @Test
    fun `adding an album to a playlist hands its song IDs to the screen`() = runTest {
        handler.album(TestData.album("al"), MediaAction.AddToPlaylist, host)

        assertThat(hostCalls).containsExactly(listOf("s1", "s2"))
    }

    @Test
    fun `adding an album without songs to a playlist says there is nothing to add`() = runTest {
        val failure = handler.album(TestData.album("empty"), MediaAction.AddToPlaylist, host)

        assertThat(failure).isEqualTo(MediaActionFailure.NothingToAdd)
        assertThat(hostCalls).isEmpty()
    }

    @Test
    fun `an artist plays as an artist, so the artist page's rule applies everywhere`() = runTest {
        handler.artist(TestData.artist("ar"), MediaAction.Play, host)

        assertThat(intents.plays.single().target).isEqualTo(PlayTarget.Artist("ar"))
    }

    @Test
    fun `download pins the song and favourite stars it`() = runTest {
        val song = TestData.song("s")

        handler.song(song, MediaAction.Download, host)
        handler.song(song, MediaAction.Favorite, host)

        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Song to "s")
        assertThat(stars.starred).containsExactly(MediaKind.Song to "s", true)
    }

    @Test
    fun `failures come back for the screen to report`() = runTest {
        val error = RemoteException.Unreachable(IOException("down"))
        stars.error = error
        albums.refreshError = error

        assertThat(handler.album(TestData.album("al"), MediaAction.Favorite, host))
            .isEqualTo(MediaActionFailure.FavoriteFailed)
        assertThat(handler.album(TestData.album("al"), MediaAction.AddToPlaylist, host))
            .isEqualTo(MediaActionFailure.LoadFailed)
        assertThat(hostCalls).isEmpty()
    }

    private companion object {
        val BOOLEANS = listOf(false, true)
    }
}
