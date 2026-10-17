package com.quantumff.segue.core.playback.intent

import android.util.Log
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.play.PlayTargetSongs
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * [PlayIntents] over the playback session: resolves a [PlayTarget] to songs ([PlayTargetSongs])
 * and hands them to the session, which queues what can play (offline only downloaded songs).
 */
@Singleton
internal class SessionPlayIntents @Inject constructor(
    private val session: IntentSession,
    private val targets: PlayTargetSongs,
    private val library: LibraryRepository,
    private val offlineMode: OfflineModeRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : PlayIntents {
    private val messageChannel = Channel<PlayIntentMessage>(Channel.BUFFERED)
    override val messages: Flow<PlayIntentMessage> = messageChannel.receiveAsFlow()

    override suspend fun play(target: PlayTarget, placement: QueuePlacement, shuffle: Boolean) = runIntent {
        val songs = try {
            targets.songsFor(target)
        } catch (e: RemoteException) {
            Log.w(TAG, "Could not load the songs of $target", e)
            return@runIntent report(PlayIntentMessage.LoadFailed)
        }
        val startIndex = (target as? PlayTarget.Songs)?.startIndex ?: 0
        if (songs.isEmpty() || !session.queue(songs, placement, startIndex, shuffle)) {
            report(PlayIntentMessage.NothingToPlay)
        }
    }

    override suspend fun playAlbums(request: AlbumPlayRequest) = runIntent {
        if (!session.playAlbums(request)) report(PlayIntentMessage.NothingToPlay)
    }

    override suspend fun instantMix(seed: InstantMixSeed) = runIntent {
        // The mix comes from the server (getSimilarSongs2), so there is none offline.
        if (offlineMode.isOffline.value) return@runIntent report(PlayIntentMessage.InstantMixOffline)
        val started = try {
            when (seed) {
                is InstantMixSeed.OfSong -> songMix(seed.song)
                is InstantMixSeed.OfLibrarySong -> songMix(library.songsByIds(listOf(seed.songId)).firstOrNull())
                is InstantMixSeed.OfArtist -> session.instantMix(seed = null, seed.artistId)
            }
        } catch (e: RemoteException) {
            Log.w(TAG, "Instant mix failed", e)
            false
        }
        if (!started) report(PlayIntentMessage.InstantMixFailed)
    }

    /** A mix from [song], of its artist: none without the song (not in the library) or its artist. */
    private suspend fun songMix(song: Song?): Boolean {
        val artistId = song?.artistId ?: return false
        return session.instantMix(song, artistId)
    }

    /** Runs [intent] in the application scope, so the caller going away does not cancel it. */
    private suspend fun runIntent(intent: suspend () -> Unit) = scope.launch { intent() }.join()

    private suspend fun report(message: PlayIntentMessage) = messageChannel.send(message)

    private companion object {
        const val TAG = "PlayIntents"
    }
}

/**
 * What a play intent asks of the playback session ([PlaybackController]); a seam so play intents
 * can be tested without a session. Each call answers whether anything was queued.
 */
internal interface IntentSession {
    suspend fun queue(songs: List<Song>, placement: QueuePlacement, startIndex: Int, shuffle: Boolean): Boolean

    suspend fun playAlbums(request: AlbumPlayRequest): Boolean

    /** An instant mix of songs similar to [artistId], after [seed] if given. Throws RemoteException. */
    suspend fun instantMix(seed: Song?, artistId: String): Boolean
}

internal class ControllerIntentSession @Inject constructor(private val playback: PlaybackController) : IntentSession {
    override suspend fun queue(songs: List<Song>, placement: QueuePlacement, startIndex: Int, shuffle: Boolean) =
        playback.queue(songs, placement, startIndex, shuffle)

    override suspend fun playAlbums(request: AlbumPlayRequest) = playback.playAlbums(request)

    override suspend fun instantMix(seed: Song?, artistId: String) = playback.playInstantMix(seed, artistId)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PlayIntentsModule {
    @Binds
    fun playIntents(impl: SessionPlayIntents): PlayIntents

    @Binds
    fun intentSession(impl: ControllerIntentSession): IntentSession
}
