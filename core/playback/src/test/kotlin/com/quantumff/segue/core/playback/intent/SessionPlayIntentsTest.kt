package com.quantumff.segue.core.playback.intent

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.data.play.PlayTargetSongs
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionPlayIntentsTest {
    private val albums = FakeAlbumRepository()
    private val offline = FakeOfflineModeRepository()
    private val session = FakeIntentSession()
    private val library = FakeLibraryRepository()
    private val songs = listOf(TestData.song("s1"), TestData.song("s2"))

    private fun TestScope.intents() = SessionPlayIntents(
        session,
        PlayTargetSongs(albums, FakePlaylistRepository(), FakeArtistRepository()),
        library,
        offline,
        backgroundScope,
    )

    @Test
    fun `an album's songs go to the session at the placement`() = runTest {
        albums.songsByAlbum.value = mapOf("al" to songs)

        intents().play(PlayTarget.Album("al"), QueuePlacement.Next)

        assertThat(session.queued.single()).isEqualTo(Queued(songs, QueuePlacement.Next, 0, false))
    }

    @Test
    fun `songs replace the queue from their start, or shuffled`() = runTest {
        val intents = intents()

        intents.play(PlayTarget.Songs(songs, startIndex = 1))
        intents.play(PlayTarget.Songs(songs), shuffle = true)

        assertThat(session.queued).containsExactly(
            Queued(songs, QueuePlacement.Replace, 1, false),
            Queued(songs, QueuePlacement.Replace, 0, true),
        ).inOrder()
    }

    @Test
    fun `a play intent hands its queue source to the session`() = runTest {
        albums.songsByAlbum.value = mapOf("al" to songs)
        val album = QueueSource.Album("al", "In Rainbows")
        val intents = intents()

        intents.play(PlayTarget.Album("al"), source = album)
        intents.playAlbums(AlbumPlayRequest(AlbumSource.Genre("Rock"), AlbumOrder.Shuffled), QueueSource.Genre("Rock"))

        assertThat(session.queued.single()).isEqualTo(Queued(songs, QueuePlacement.Replace, 0, false, album))
        assertThat(session.albumPlays.single().second).isEqualTo(QueueSource.Genre("Rock"))
    }

    @Test
    fun `nothing queued says nothing to play`() = runTest {
        val intents = intents()

        intents.play(PlayTarget.Album("empty"))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.NothingToPlay)
        assertThat(session.queued).isEmpty()

        session.answer = false
        intents.play(PlayTarget.Songs(songs))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.NothingToPlay)

        intents.playAlbums(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.Shuffled), QueueSource.Library(true))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.NothingToPlay)
    }

    @Test
    fun `songs the server could not send say the load failed`() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("down"))
        val intents = intents()

        intents.play(PlayTarget.Album("al"))

        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.LoadFailed)
        assertThat(session.queued).isEmpty()
    }

    @Test
    fun `offline there is no instant mix`() = runTest {
        offline.isOffline.value = true
        val intents = intents()

        intents.instantMix(InstantMixSeed.OfSong(songs[0]))

        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.InstantMixOffline)
        assertThat(session.mixes).isEmpty()
    }

    @Test
    fun `an instant mix the server fails says so`() = runTest {
        session.mixError = RemoteException.Unreachable(IOException("down"))
        val intents = intents()

        intents.instantMix(InstantMixSeed.OfArtist("ar", "Radiohead"))

        assertThat(session.mixes).containsExactly(Mix(null, "ar", QueueSource.InstantMix("Radiohead")))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.InstantMixFailed)
    }

    @Test
    fun `a song seeds a mix of its artist, from the library when known only by id`() = runTest {
        library.songsState.value = listOf(songs[1])
        val intents = intents()

        intents.instantMix(InstantMixSeed.OfSong(songs[0]))
        intents.instantMix(InstantMixSeed.OfLibrarySong("s2"))

        assertThat(session.mixes).containsExactly(
            Mix(songs[0], "artist-1", QueueSource.InstantMix(songs[0].title)),
            Mix(songs[1], "artist-1", QueueSource.InstantMix(songs[1].title)),
        ).inOrder()
    }

    @Test
    fun `an instant mix with no seed to start from says so`() = runTest {
        val intents = intents()

        intents.instantMix(InstantMixSeed.OfLibrarySong("not-synced"))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.InstantMixFailed)

        intents.instantMix(InstantMixSeed.OfSong(TestData.song("no-artist", artistId = null)))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.InstantMixFailed)
        assertThat(session.mixes).isEmpty()
    }

    @Test
    fun `an intent finishes when its caller stops waiting`() = runTest {
        val intents = intents()
        session.gate = CompletableDeferred()

        val caller = launch { intents.play(PlayTarget.Songs(songs)) }
        advanceUntilIdle()
        caller.cancel()
        session.gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(session.queued).hasSize(1)
    }
}

private data class Queued(
    val songs: List<Song>,
    val placement: QueuePlacement,
    val startIndex: Int,
    val shuffle: Boolean,
    val source: QueueSource = QueueSource.Unknown,
)

private data class Mix(val seed: Song?, val artistId: String, val source: QueueSource)

private class FakeIntentSession : IntentSession {
    val queued = mutableListOf<Queued>()
    val mixes = mutableListOf<Mix>()
    val albumPlays = mutableListOf<Pair<AlbumPlayRequest, QueueSource>>()
    var answer = true
    var mixError: RemoteException? = null

    /** When set, queueing waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun queue(
        songs: List<Song>,
        placement: QueuePlacement,
        startIndex: Int,
        shuffle: Boolean,
        source: QueueSource,
    ): Boolean {
        gate?.await()
        queued += Queued(songs, placement, startIndex, shuffle, source)
        return answer
    }

    override suspend fun playAlbums(request: AlbumPlayRequest, source: QueueSource): Boolean {
        albumPlays += request to source
        return answer
    }

    override suspend fun instantMix(seed: Song?, artistId: String, source: QueueSource): Boolean {
        mixes += Mix(seed, artistId, source)
        mixError?.let { throw it }
        return answer
    }
}
