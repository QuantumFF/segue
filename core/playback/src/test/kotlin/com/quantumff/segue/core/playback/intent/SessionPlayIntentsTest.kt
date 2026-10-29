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
import com.quantumff.segue.core.playback.mix.InstantMixStart
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val rock = QueueSource.Genre("Rock")
    private val wholeLibrary = QueueSource.Library(shuffled = true)

    private fun TestScope.intents() = SessionPlayIntents(
        session,
        PlayTargetSongs(albums, FakePlaylistRepository(), FakeArtistRepository()),
        library,
        offline,
        backgroundScope,
    )

    /** Every message [intents] sends from now on, as it is sent. */
    private fun TestScope.messagesOf(intents: SessionPlayIntents): List<PlayIntentMessage> {
        val messages = mutableListOf<PlayIntentMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { intents.messages.toList(messages) }
        return messages
    }

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
    fun `play next and add to queue confirm how many songs were queued, and from what`() = runTest {
        albums.songsByAlbum.value = mapOf("al" to songs)
        val intents = intents()
        val messages = messagesOf(intents)

        intents.play(PlayTarget.Album("al"), QueuePlacement.Next, source = QueueSource.Album("al", "In Rainbows"))
        intents.play(PlayTarget.Songs(songs.take(1)), QueuePlacement.End)

        assertThat(messages).containsExactly(
            PlayIntentMessage.Queued(2, QueuePlacement.Next, "In Rainbows"),
            PlayIntentMessage.Queued(1, QueuePlacement.End, songs[0].title),
        ).inOrder()
    }

    @Test
    fun `the count confirmed is what the session queued, offline only the downloaded songs`() = runTest {
        val intents = intents()
        val messages = messagesOf(intents)
        session.answer = 1

        intents.play(PlayTarget.Songs(songs), QueuePlacement.End)

        assertThat(messages).containsExactly(PlayIntentMessage.Queued(1, QueuePlacement.End, null))
    }

    @Test
    fun `play albums next or at the end confirms how many songs were queued`() = runTest {
        val intents = intents()
        val messages = messagesOf(intents)
        session.answer = 24

        intents.playAlbums(AlbumPlayRequest(AlbumSource.Genre("Rock"), AlbumOrder.InOrder, QueuePlacement.Next), rock)
        intents.playAlbums(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.Shuffled, QueuePlacement.End), wholeLibrary)

        assertThat(messages).containsExactly(
            PlayIntentMessage.Queued(24, QueuePlacement.Next, "Rock"),
            PlayIntentMessage.Queued(24, QueuePlacement.End, null),
        ).inOrder()
    }

    @Test
    fun `replacing the queue confirms nothing, the music starting is enough`() = runTest {
        albums.songsByAlbum.value = mapOf("al" to songs)
        val intents = intents()
        val messages = messagesOf(intents)

        intents.play(PlayTarget.Album("al"), source = QueueSource.Album("al", "In Rainbows"))
        intents.play(PlayTarget.Songs(songs), shuffle = true)
        intents.playAlbums(AlbumPlayRequest(AlbumSource.Genre("Rock"), AlbumOrder.Shuffled), rock)

        assertThat(session.queued).hasSize(2)
        assertThat(session.albumPlays).hasSize(1)
        assertThat(messages).isEmpty()
    }

    @Test
    fun `an intent that does not confirm still says when it queued nothing`() = runTest {
        val intents = intents()
        val messages = messagesOf(intents)

        intents.play(PlayTarget.Songs(songs), QueuePlacement.Next, confirm = false)
        session.answer = 0
        intents.play(PlayTarget.Songs(songs), QueuePlacement.End, confirm = false)

        assertThat(session.queued).hasSize(2)
        assertThat(messages).containsExactly(PlayIntentMessage.NothingToPlay)
    }

    @Test
    fun `nothing queued says nothing to play`() = runTest {
        val intents = intents()

        intents.play(PlayTarget.Album("empty"))
        assertThat(intents.messages.first()).isEqualTo(PlayIntentMessage.NothingToPlay)
        assertThat(session.queued).isEmpty()

        session.answer = 0
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
    fun `a started instant mix says whether it replaced a queue`() = runTest {
        val intents = intents()
        val messages = messagesOf(intents)

        session.mixReplaced = true
        intents.instantMix(InstantMixSeed.OfArtist("ar", "Radiohead"))
        session.mixReplaced = false
        intents.instantMix(InstantMixSeed.OfSong(songs[0]))

        assertThat(messages).containsExactly(
            PlayIntentMessage.MixStarted(replacedQueue = true),
            PlayIntentMessage.MixStarted(replacedQueue = false),
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

    /** How many songs the session says it queued; null for all it was given (10 for album plays). */
    var answer: Int? = null
    var mixError: RemoteException? = null

    /** Whether the session says a mix replaced a queue. */
    var mixReplaced = false

    /** When set, queueing waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun queue(
        songs: List<Song>,
        placement: QueuePlacement,
        startIndex: Int,
        shuffle: Boolean,
        source: QueueSource,
    ): Int {
        gate?.await()
        queued += Queued(songs, placement, startIndex, shuffle, source)
        return answer ?: songs.size
    }

    override suspend fun playAlbums(request: AlbumPlayRequest, source: QueueSource): Int {
        albumPlays += request to source
        return answer ?: 10
    }

    override suspend fun instantMix(seed: Song?, artistId: String, source: QueueSource): InstantMixStart {
        mixes += Mix(seed, artistId, source)
        mixError?.let { throw it }
        return InstantMixStart(answer ?: 10, mixReplaced)
    }
}
