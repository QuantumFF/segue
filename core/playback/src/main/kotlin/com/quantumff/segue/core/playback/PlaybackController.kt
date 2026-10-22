package com.quantumff.segue.core.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.data.play.PlayerTransport
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AutoAdoption
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.browse.await
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.mix.InstantMix
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.playback.sync.QueueState
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import com.quantumff.segue.core.playback.sync.QueueSyncEngine.Companion.deviceName
import com.quantumff.segue.core.playback.sync.RemoteAdoption
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one way features control playback. Wraps a Media3 [MediaController] to
 * [SeguePlaybackService], connected on first use. It is also the [PlayerStateSource] screens read
 * player state from, and the [PlayerTransport] pages pause and resume the queue with.
 *
 * Commands return at once and run on the main thread, where MediaController lives. Queue-editing
 * commands take queue positions ([com.quantumff.segue.core.model.QueueItem.timelineIndex]). The
 * queue order is the play order: Segue shuffles only when songs are queued, never a running queue.
 * Play intents ([queue], [playAlbums], [playInstantMix]) suspend until the service answers whether
 * it queued anything.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueSync: QueueSyncEngine,
    private val artists: ArtistRepository,
    @Dispatcher(SegueDispatchers.Main) private val mainDispatcher: CoroutineDispatcher,
) : PlayerStateSource,
    PlayerTransport {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val sessionExtras = MutableStateFlow(Bundle.EMPTY)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** The connected controller, or null while disconnected (for example after the service stopped). */
    private val connected = MutableStateFlow<MediaController?>(null)
    private val projection = PlayerUiStateProjection()

    private var lastRemoval: SegueSessionCommands.Removed? = null

    private val autoAdoptionEvents = Channel<AutoAdoption>(Channel.BUFFERED)

    /** Each automatic adoption of another device's queue, for the "Continued from" snackbar. */
    val autoAdoptions: Flow<AutoAdoption> = autoAdoptionEvents.receiveAsFlow()

    private val continueFailureEvents = Channel<Unit>(Channel.BUFFERED)

    /** Each "Continue from <device>" ([adoptRemoteQueue]) that adopted nothing, for a snackbar. */
    val continueFailures: Flow<Unit> = continueFailureEvents.receiveAsFlow()

    /**
     * Player state as of the last player event: [PlayerUiState.positionMillis] does not tick.
     * Observing it connects to the service. While disconnected it is empty; the next command
     * reconnects.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: StateFlow<PlayerUiState> = flow {
        try {
            controller()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Could not connect to the playback service", e)
        }
        emitAll(
            connected.flatMapLatest { controller ->
                if (controller == null) {
                    flowOf(PlayerUiState())
                } else {
                    controller.events()
                        .combine(sessionExtras) { _, extras -> projection.project(controller, extras) }
                }
            },
        )
    }
        .combine(queueSync.remoteOffer) { player, offer -> player.copy(remoteQueue = offer) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlayerUiState())

    /**
     * [state] plus a position that updates every [POSITION_TICK_MILLIS] while someone observes
     * and music plays (the battery rule: the ticker runs only for a visible position).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val tickingState: StateFlow<PlayerUiState> = state
        .flatMapLatest { snapshot ->
            val controller = connected.value
            if (snapshot.isPlaying && controller != null) ticking(controller, snapshot) else flowOf(snapshot)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlayerUiState())

    // --- Queue -------------------------------------------------------------------------------------

    /**
     * Queues [songs] at [placement]. Replacing the queue plays them: in order from [startIndex], or
     * with [shuffle] in a fresh shuffled order from its first song. The service queues them
     * ([com.quantumff.segue.core.playback.service.QueueEditor]): shuffled only now, never as a
     * running queue, and offline only the downloaded songs. Replacing the queue makes [source] the
     * queue source. False if there is nothing to play.
     */
    suspend fun queue(
        songs: List<Song>,
        placement: QueuePlacement,
        startIndex: Int,
        shuffle: Boolean,
        source: QueueSource = QueueSource.Unknown,
    ): Boolean {
        val items = songs.map { it.toMediaItem() }
        val request = SegueSessionCommands.QueueSongs(items, placement, startIndex, shuffle, source)
        return send(SegueSessionCommands.QUEUE_SONGS, SegueSessionCommands.queueSongsArgs(request))
    }

    /** Moves the item at queue position [from] to position [to]. */
    fun move(from: Int, to: Int) = command { controller -> controller.moveMediaItem(from, to) }

    /** Removes the item at [timelineIndex]. [undoRemove] puts the last removed item back. */
    fun remove(timelineIndex: Int) = command { controller ->
        if (timelineIndex !in 0 until controller.mediaItemCount) return@command
        lastRemoval = SegueSessionCommands.Removed(controller.getMediaItemAt(timelineIndex), timelineIndex)
        controller.removeMediaItem(timelineIndex)
    }

    /**
     * Puts the last removed item back where it was. It was the user's own queue, so unlike a play
     * intent (an add, which the service narrows offline) it comes back even if not downloaded. If the
     * service does not put it back, Undo stays possible, unless something else was removed meanwhile.
     */
    fun undoRemove() = command { controller ->
        val removal = lastRemoval ?: return@command
        // Taken now, so a second tap before the answer does not put it back twice.
        lastRemoval = null
        val putBack = try {
            controller.sendCustomCommand(
                SegueSessionCommands.command(SegueSessionCommands.UNDO_REMOVE),
                SegueSessionCommands.undoRemoveArgs(removal),
            ).await().resultCode == SessionResult.RESULT_SUCCESS
        } catch (e: ExecutionException) {
            Log.w(TAG, "Could not put the removed item back", e)
            false
        }
        if (!putBack && lastRemoval == null) lastRemoval = removal
    }

    fun skipTo(timelineIndex: Int) = command { it.seekTo(timelineIndex, 0) }

    fun clearQueue() = command { it.clearMediaItems() }

    // --- Transport ---------------------------------------------------------------------------------

    override fun play() = command { controller ->
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override fun pause() = command { it.pause() }

    /** Pauses while the player plays or buffers towards playing ([playsWhenReady]); otherwise plays. */
    fun togglePlayPause() = command { controller ->
        if (controller.playsWhenReady) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
    }

    fun seekTo(positionMillis: Long) = command { it.seekTo(positionMillis) }

    fun skipToNext() = command { it.seekToNext() }

    fun skipToPrevious() = command { it.seekToPrevious() }

    /**
     * A playback error's Skip ([PlayerUiState.playbackError]): plays the next item. A failed player
     * stays idle, so it is prepared again. Does nothing after the last item.
     */
    fun skipPastError() = command { controller ->
        if (!controller.hasNextMediaItem()) return@command
        controller.seekToNextMediaItem()
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    /** A playback error's Retry: prepares the failed song again and plays it. Success clears the error. */
    fun retry() = command { controller ->
        controller.prepare()
        controller.play()
    }

    fun setRepeatMode(mode: RepeatMode) = command { it.repeatMode = mode.playerRepeatMode }

    /** Starts, replaces, or (with null) cancels the sleep timer. */
    fun setSleepTimer(timer: SleepTimer?) = command { controller ->
        controller.sendCustomCommand(
            SegueSessionCommands.command(SegueSessionCommands.SET_SLEEP_TIMER),
            SegueSessionCommands.sleepArgs(timer),
        )
    }

    fun toggleFavorite() = command { controller ->
        controller.sendCustomCommand(SegueSessionCommands.command(SegueSessionCommands.TOGGLE_FAVORITE), Bundle.EMPTY)
    }

    // --- Remote queue --------------------------------------------------------------------------------

    /**
     * "Continue from <device>": the service takes over the offered queue, paused at its saved
     * position ([com.quantumff.segue.core.playback.sync.SavedQueueLoader.adopt]). If it cannot,
     * [continueFailures] says so.
     */
    fun adoptRemoteQueue() {
        val adoption = queueSync.acceptOffer() ?: return
        scope.launch { if (!adopt(adoption)) continueFailureEvents.send(Unit) }
    }

    fun dismissRemoteQueue() = queueSync.dismissOffer()

    /**
     * The "Continue from <device>" notification's Continue: the service takes over the offered
     * queue at its saved position and plays it. False if it did not.
     */
    suspend fun continueRemoteQueue(): Boolean {
        val adoption = queueSync.acceptOffer() ?: return false
        return adopt(adoption, play = true)
    }

    /** Has the service adopt a queue the auto-continue setting took over, then reports it for the snackbar. */
    internal suspend fun adoptAutomatically(adoption: RemoteAdoption) {
        if (!adopt(adoption)) return
        autoAdoptionEvents.send(AutoAdoption(adoption.queue.deviceName, canUndo = adoption.undo != null))
    }

    /**
     * Has the service adopt [adoption], which queue sync staged. False if it did not: offline none
     * of its songs is downloaded, or the service could not be asked, and then it is unstaged.
     */
    private suspend fun adopt(adoption: RemoteAdoption, play: Boolean = false): Boolean {
        val adopted = send(SegueSessionCommands.ADOPT_QUEUE, SegueSessionCommands.adoptArgs(play))
        if (!adopted) queueSync.abandonAdoption(adoption)
        return adopted
    }

    /** The snackbar's Undo: puts back the local queue the last automatic adoption replaced. */
    fun undoAutoAdoption() = command { controller ->
        controller.sendCustomCommand(SegueSessionCommands.command(SegueSessionCommands.UNDO_ADOPTION), Bundle.EMPTY)
    }

    /**
     * "Upload queue": saves the queue to the server now. False if the queue is empty or the player
     * could not be reached. Throws RemoteException.
     */
    suspend fun uploadQueue(): Boolean = withContext(mainDispatcher) {
        val queueState = try {
            val controller = controller()
            QueueState(
                songIds = List(controller.mediaItemCount) { controller.getMediaItemAt(it).mediaId },
                currentIndex = controller.currentMediaItemIndex,
                positionMillis = controller.currentPosition,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Could not read the queue to upload", e)
            null
        }
        if (queueState == null || queueState.songIds.isEmpty()) return@withContext false
        queueSync.uploadNow(queueState)
        true
    }

    /**
     * "Download queue": the queue saved on the server, for [adoptDownloadedQueue]; null if none.
     * Throws RemoteException.
     */
    suspend fun fetchRemoteQueue(): RemotePlayQueue? = queueSync.fetchNow()

    /**
     * Has the service replace the local queue with [queue] from [fetchRemoteQueue], paused
     * ([com.quantumff.segue.core.playback.sync.SavedQueueLoader.adopt]). False if it did not.
     */
    suspend fun adoptDownloadedQueue(queue: RemotePlayQueue): Boolean =
        adopt(queueSync.stageDownload(queue)).also { adopted -> if (adopted) queueSync.markDownloaded(queue) }

    // --- Generated queues ----------------------------------------------------------------------------

    /**
     * "Play albums" / "Shuffle albums" / "Shuffle all" ([request]): every album of the source whole
     * in disc and track order, the albums in the source's order or shuffled, or every song shuffled,
     * replacing the queue or placed in it. The service reads the songs and queues them all at once
     * ([com.quantumff.segue.core.playback.album.AlbumPlays]). Reads Room only; offline only
     * downloaded songs are queued. Replacing the queue makes [source] the queue source. False if
     * there is nothing to play.
     */
    suspend fun playAlbums(request: AlbumPlayRequest, source: QueueSource): Boolean =
        send(SegueSessionCommands.PLAY_ALBUMS, SegueSessionCommands.playAlbumsArgs(request, source))

    /**
     * Instant mix: [seed] if given, then songs similar to [artistId] (`getSimilarSongs2`), topped
     * up with the artist's top songs, replacing the queue with [source] as its source. Throws
     * RemoteException. False if nothing came back.
     */
    suspend fun playInstantMix(seed: Song?, artistId: String, source: QueueSource): Boolean {
        val similar = artists.similarSongs(artistId, InstantMix.DEFAULT_SIZE)
        val top = if (similar.size < InstantMix.DEFAULT_SIZE) artists.topSongs(artistId) else emptyList()
        val mix = InstantMix.build(seed, similar, top)
        if (mix.size <= (if (seed == null) 0 else 1)) return false
        return queue(mix, QueuePlacement.Replace, startIndex = 0, shuffle = false, source = source)
    }

    // --- Connection ----------------------------------------------------------------------------------

    /** Sends a queueing or adopting command ([action]) and answers whether the service queued anything. */
    private suspend fun send(action: String, args: Bundle): Boolean = withContext(mainDispatcher) {
        try {
            val result = controller().sendCustomCommand(SegueSessionCommands.command(action), args).await()
            result.resultCode == SessionResult.RESULT_SUCCESS &&
                result.extras.getBoolean(SegueSessionCommands.ARG_STARTED)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Session command failed ($action)", e)
            false
        }
    }

    private fun command(block: suspend (MediaController) -> Unit) {
        scope.launch {
            try {
                block(controller())
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Playback command failed", e)
            }
        }
    }

    private suspend fun controller(): MediaController = withContext(mainDispatcher) {
        val future = controllerFuture ?: buildController().also { controllerFuture = it }
        try {
            future.await().also {
                sessionExtras.value = it.sessionExtras
                connected.value = it
            }
        } catch (e: ExecutionException) {
            controllerFuture = null
            throw e
        }
    }

    private fun buildController(): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, SeguePlaybackService::class.java))
        return MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                        sessionExtras.value = extras
                    }

                    override fun onDisconnected(controller: MediaController) {
                        controllerFuture = null
                        if (connected.value === controller) connected.value = null
                    }
                },
            )
            .buildAsync()
    }

    // --- State ---------------------------------------------------------------------------------------

    private fun MediaController.events(): Flow<Unit> = callbackFlow {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                trySend(Unit)
            }
        }
        addListener(listener)
        trySend(Unit)
        awaitClose { removeListener(listener) }
    }.conflate()

    private fun ticking(controller: MediaController, snapshot: PlayerUiState): Flow<PlayerUiState> = flow {
        while (true) {
            emit(snapshot.copy(positionMillis = controller.currentPosition))
            delay(POSITION_TICK_MILLIS)
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val POSITION_TICK_MILLIS = 500L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PlayerStateModule {
    @Binds
    fun playerStateSource(impl: PlaybackController): PlayerStateSource

    @Binds
    fun playerTransport(impl: PlaybackController): PlayerTransport
}
