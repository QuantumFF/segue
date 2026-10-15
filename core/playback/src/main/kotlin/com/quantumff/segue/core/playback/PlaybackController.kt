package com.quantumff.segue.core.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.AutoAdoption
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.media.toQueueItem
import com.quantumff.segue.core.playback.mix.InstantMix
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.playback.shuffle.AlbumPicker
import com.quantumff.segue.core.playback.sync.QueueState
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import com.quantumff.segue.core.playback.sync.QueueSyncEngine.Companion.deviceName
import com.quantumff.segue.core.playback.sync.RemoteAdoption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The one way features control playback. Wraps a Media3 [MediaController] to
 * [SeguePlaybackService], connected on first use, and exposes [state] for the UI.
 *
 * Commands return at once and run on the main thread, where MediaController lives. Queue-editing
 * commands take [QueueItem.timelineIndex]; [move] takes positions in play order, as the queue
 * screen shows them.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueSync: QueueSyncEngine,
    private val albumShuffle: AlbumShuffleRepository,
    private val artists: ArtistRepository,
    @Dispatcher(SegueDispatchers.Main) private val mainDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val random = Random.Default
    private val sessionExtras = MutableStateFlow(Bundle.EMPTY)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** The connected controller, or null while disconnected (for example after the service stopped). */
    private val connected = MutableStateFlow<MediaController?>(null)
    private val queueCache = QueueCache()

    private class Removal(val item: MediaItem, val timelineIndex: Int, val playPosition: Int)

    private var lastRemoval: Removal? = null

    private val autoAdoptionEvents = Channel<AutoAdoption>(Channel.BUFFERED)

    /** Each automatic adoption of another device's queue, for the "Continued from" snackbar. */
    val autoAdoptions: Flow<AutoAdoption> = autoAdoptionEvents.receiveAsFlow()

    /**
     * Player state as of the last player event: [PlayerUiState.positionMillis] does not tick.
     * For surfaces that show no position, so observing them never runs the position ticker.
     * Observing it connects to the service. While disconnected it is empty; the next command
     * reconnects.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val eventState: StateFlow<PlayerUiState> = flow {
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
                        .combine(sessionExtras) { _, extras -> snapshot(controller, extras) }
                }
            },
        )
    }
        .combine(queueSync.remoteOffer) { state, offer -> state.copy(remoteQueue = offer) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlayerUiState())

    /**
     * [eventState] plus a position that updates every [POSITION_TICK_MILLIS] while someone observes
     * and music plays (the battery rule: the ticker runs only for a visible position).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlayerUiState> = eventState
        .flatMapLatest { snapshot ->
            val controller = connected.value
            if (snapshot.isPlaying && controller != null) ticking(controller, snapshot) else flowOf(snapshot)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlayerUiState())

    // --- Queue -------------------------------------------------------------------------------------

    /**
     * An explicit "Play" or "Shuffle" of [songs]: in order from [startIndex] with shuffle Off, or
     * with [shuffle] in track-shuffle order from a random song.
     */
    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) = command { controller ->
        if (songs.isEmpty()) return@command
        val start = if (shuffle) random.nextInt(songs.size) else startIndex.coerceIn(songs.indices)
        // Before the new items, so the fresh track shuffle starts with the start item.
        controller.sendCustomCommand(
            SegueSessionCommands.command(SegueSessionCommands.SET_SHUFFLE),
            SegueSessionCommands.shuffleArgs(if (shuffle) ShuffleMode.Tracks else ShuffleMode.Off),
        )
        controller.setMediaItems(songs.map { it.toMediaItem() }, start, 0)
        controller.prepare()
        controller.play()
    }

    /** Inserts [songs] right after the current track (after the current album in album shuffle). */
    fun playNext(songs: List<Song>) = command { controller ->
        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(songs.map { it.toMediaItem() })
            controller.prepare()
        } else {
            controller.addMediaItems(controller.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
        }
    }

    /** Appends [songs] to the end of the queue. */
    fun addToQueue(songs: List<Song>) = command { controller ->
        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(songs.map { it.toMediaItem() })
            controller.prepare()
        } else {
            controller.addMediaItems(songs.map { it.toMediaItem() })
        }
    }

    /**
     * Moves [count] items at play position [from] so the first lands at play position [to] (an
     * album block when [count] > 1). With shuffle on, only the play order changes.
     */
    fun move(from: Int, to: Int, count: Int = 1) = command { controller ->
        if (controller.shuffleModeEnabled) {
            controller.sendCustomCommand(
                SegueSessionCommands.command(SegueSessionCommands.MOVE_IN_PLAY_ORDER),
                SegueSessionCommands.moveArgs(from, to, count),
            )
        } else {
            controller.moveMediaItems(from, from + count, to)
        }
    }

    /** Removes the item at [timelineIndex]. [undoRemove] puts the last removed item back. */
    fun remove(timelineIndex: Int) = command { controller ->
        if (timelineIndex !in 0 until controller.mediaItemCount) return@command
        val (order, _) = queueCache.get(controller)
        lastRemoval = Removal(controller.getMediaItemAt(timelineIndex), timelineIndex, order.indexOf(timelineIndex))
        controller.removeMediaItem(timelineIndex)
    }

    /** Puts the last removed item back where it was, in the queue and in play order. */
    fun undoRemove() = command { controller ->
        val removal = lastRemoval ?: return@command
        lastRemoval = null
        val result = controller.sendCustomCommand(
            SegueSessionCommands.command(SegueSessionCommands.REINSERT),
            SegueSessionCommands.reinsertArgs(removal.item, removal.timelineIndex, removal.playPosition),
        ).await()
        if (result.resultCode != SessionResult.RESULT_SUCCESS) Log.w(TAG, "Undo failed: ${result.resultCode}")
    }

    fun skipTo(timelineIndex: Int) = command { it.seekTo(timelineIndex, 0) }

    fun clearQueue() = command { it.clearMediaItems() }

    // --- Transport ---------------------------------------------------------------------------------

    fun play() = command { controller ->
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    fun pause() = command { it.pause() }

    fun togglePlayPause() = command { controller ->
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
    }

    fun seekTo(positionMillis: Long) = command { it.seekTo(positionMillis) }

    fun skipToNext() = command { it.seekToNext() }

    fun skipToPrevious() = command { it.seekToPrevious() }

    fun setShuffleMode(mode: ShuffleMode) = command { controller ->
        controller.sendCustomCommand(
            SegueSessionCommands.command(SegueSessionCommands.SET_SHUFFLE),
            SegueSessionCommands.shuffleArgs(mode),
        )
    }

    /** Off → Tracks → Albums → Off, from the service's current mode (for widgets, which may show a stale one). */
    fun cycleShuffleMode() = command { controller ->
        controller.sendCustomCommand(SegueSessionCommands.command(SegueSessionCommands.CYCLE_SHUFFLE), Bundle.EMPTY)
    }

    fun setRepeatMode(mode: RepeatMode) = command { controller ->
        controller.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_ALL
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
    }

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

    /** "Continue from <device>": takes over the offered queue, paused at its saved position. */
    fun adoptRemoteQueue() {
        val queue = queueSync.acceptOffer() ?: return
        scope.launch { adopt(queue) }
    }

    fun dismissRemoteQueue() = queueSync.dismissOffer()

    /** Adopts a queue the auto-continue setting took over, then reports it for the snackbar. */
    internal suspend fun adoptAutomatically(adoption: RemoteAdoption) {
        adopt(adoption.queue)
        autoAdoptionEvents.send(AutoAdoption(adoption.queue.deviceName, canUndo = adoption.undo != null))
    }

    /** The snackbar's Undo: puts back the local queue the last automatic adoption replaced. */
    fun undoAutoAdoption() = command { controller ->
        controller.sendCustomCommand(SegueSessionCommands.command(SegueSessionCommands.UNDO_ADOPTION), Bundle.EMPTY)
    }

    internal suspend fun adopt(queue: RemotePlayQueue): Unit = withContext(mainDispatcher) {
        try {
            val controller = controller()
            controller.pause()
            // The server's queue is already in play order.
            controller.sendCustomCommand(
                SegueSessionCommands.command(SegueSessionCommands.SET_SHUFFLE),
                SegueSessionCommands.shuffleArgs(ShuffleMode.Off),
            )
            val index = queue.currentIndex.coerceIn(queue.songs.indices)
            controller.setMediaItems(queue.songs.map { it.toMediaItem() }, index, queue.positionMillis)
            queueSync.markAdopted(QueueState(queue.songs.map { it.id }, index, queue.positionMillis))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Could not adopt the remote queue", e)
        }
    }

    // --- Generated queues ----------------------------------------------------------------------------

    /**
     * "Shuffle albums": random albums from [source], each queued whole in disc and track order, in
     * plain (unshuffled) play order. A library or genre picks [AlbumPicker.DEFAULT_ALBUM_COUNT]
     * albums; an artist or playlist uses all of its albums. Reads Room only. With [offline] (what is
     * downloaded, passed while offline) only downloaded albums are picked and only their
     * downloaded songs queued. False if there is nothing to play.
     */
    suspend fun shuffleAlbums(source: AlbumShuffleSource, offline: DownloadedLibrary? = null): Boolean {
        val albumIds = OfflineLibrary.shuffleAlbumIds(albumShuffle.albumIds(source), offline)
        val count = when (source) {
            AlbumShuffleSource.Library, is AlbumShuffleSource.Genre -> AlbumPicker.DEFAULT_ALBUM_COUNT
            is AlbumShuffleSource.Artist, is AlbumShuffleSource.Playlist -> albumIds.size
        }
        val picked = AlbumPicker.pick(albumIds, count, random)
        val songs = OfflineLibrary.shuffleSongs(albumShuffle.songs(source, picked), offline)
        val queue = AlbumPicker.queueInAlbumOrder(songs, picked)
        if (queue.isEmpty()) return false
        play(queue, startIndex = 0, shuffle = false)
        return true
    }

    /**
     * Instant mix from [seed]: the song, then songs similar to its artist (`getSimilarSongs2`),
     * topped up with the artist's top songs. Throws RemoteException. False if nothing came back.
     */
    suspend fun playInstantMix(seed: Song): Boolean = playMix(seed, seed.artistId ?: return false)

    /** Instant mix for an artist, without a seed song. Throws RemoteException. */
    suspend fun playArtistMix(artistId: String): Boolean = playMix(null, artistId)

    private suspend fun playMix(seed: Song?, artistId: String): Boolean {
        val similar = artists.similarSongs(artistId, InstantMix.DEFAULT_SIZE)
        val top = if (similar.size < InstantMix.DEFAULT_SIZE) artists.topSongs(artistId) else emptyList()
        val mix = InstantMix.build(seed, similar, top)
        if (mix.size <= (if (seed == null) 0 else 1)) return false
        play(mix, startIndex = 0, shuffle = false)
        return true
    }

    // --- Connection ----------------------------------------------------------------------------------

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

    private fun snapshot(controller: MediaController, extras: Bundle): PlayerUiState {
        val (order, queue) = queueCache.get(controller)
        val queueIndex = order.indexOf(controller.currentMediaItemIndex)
        val current = queue.getOrNull(queueIndex)
        return PlayerUiState(
            currentItem = current,
            currentQueueIndex = queueIndex,
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            positionMillis = controller.currentPosition,
            durationMillis = controller.duration.takeIf { it != C.TIME_UNSET } ?: current?.durationMillis ?: 0,
            queue = queue,
            shuffleMode = SegueSessionCommands.shuffleMode(extras)
                ?: if (controller.shuffleModeEnabled) ShuffleMode.Tracks else ShuffleMode.Off,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                else -> RepeatMode.Off
            },
            sleepTimer = SegueSessionCommands.sleepTimer(extras),
        )
    }

    /** Rebuilds the queue only when the timeline or shuffle changes, not on every position tick. */
    private class QueueCache {
        private var timeline: Timeline? = null
        private var shuffle = false
        private var order: List<Int> = emptyList()
        private var queue: ImmutableList<QueueItem> = persistentListOf()

        fun get(controller: MediaController): Pair<List<Int>, ImmutableList<QueueItem>> {
            val current = controller.currentTimeline
            if (current != timeline || controller.shuffleModeEnabled != shuffle) {
                timeline = current
                shuffle = controller.shuffleModeEnabled
                order = playOrder(current, shuffle)
                queue = order.map { index -> controller.getMediaItemAt(index).toQueueItem(index) }.toImmutableList()
            }
            return order to queue
        }

        private fun playOrder(timeline: Timeline, shuffle: Boolean): List<Int> {
            val order = ArrayList<Int>(timeline.windowCount)
            var index = timeline.getFirstWindowIndex(shuffle)
            while (index != C.INDEX_UNSET) {
                order += index
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
            }
            return order
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val POSITION_TICK_MILLIS = 500L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T {
    if (isDone) return get()
    return suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: ExecutionException) {
                    continuation.resumeWithException(e)
                }
            },
            MoreExecutors.directExecutor(),
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
}
