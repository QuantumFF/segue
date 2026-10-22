package com.quantumff.segue.core.playback

import android.app.PendingIntent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.playback.album.AlbumPlays
import com.quantumff.segue.core.playback.browse.SessionLibrary
import com.quantumff.segue.core.playback.browse.SessionPlay
import com.quantumff.segue.core.playback.browse.launchFuture
import com.quantumff.segue.core.playback.cache.NextTrackPrefetcher
import com.quantumff.segue.core.playback.cache.megabytes
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import com.quantumff.segue.core.playback.replaygain.ReplayGainController
import com.quantumff.segue.core.playback.replaygain.ReplayGainProcessor
import com.quantumff.segue.core.playback.replaygain.ReplayGainSettings
import com.quantumff.segue.core.playback.service.FavoriteToggle
import com.quantumff.segue.core.playback.service.IdleStop
import com.quantumff.segue.core.playback.service.LoadingWifiLock
import com.quantumff.segue.core.playback.service.PlaybackComponents
import com.quantumff.segue.core.playback.service.QueueEditor
import com.quantumff.segue.core.playback.service.ScreenOffOffload
import com.quantumff.segue.core.playback.service.SeguePlayer
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.playback.service.SessionPlayer
import com.quantumff.segue.core.playback.sleep.SleepTimerController
import com.quantumff.segue.core.playback.sync.QueueSyncListener
import com.quantumff.segue.core.playback.sync.SavedQueueLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Hosts the player and the media session for the app, the notification, and other controllers.
 *
 * - Media3 runs it in the foreground only while playing; after [UserPreferences.stopWhenPausedMinutes]
 *   paused it stops itself.
 * - Saved queues reach the player through [SavedQueueLoader]: this device's at start (paused,
 *   offline-capable) and for playback resumption, and another client's on adoption. Play pressed
 *   outside the app first checks for another client's newer queue ([SessionPlayer]).
 * - Player events drive queue sync ([com.quantumff.segue.core.playback.sync.QueueSyncEngine]),
 *   scrobbling, ReplayGain, prefetching, and the sleep timer. None of them polls.
 */
@AndroidEntryPoint
class SeguePlaybackService : MediaLibraryService() {
    @Inject
    lateinit var components: PlaybackComponents

    private lateinit var scope: CoroutineScope
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: SeguePlayer
    private lateinit var queueSyncListener: QueueSyncListener
    private lateinit var offload: ScreenOffOffload
    private lateinit var replayGain: ReplayGainController
    private lateinit var sleepTimer: SleepTimerController
    private lateinit var prefetcher: NextTrackPrefetcher
    private lateinit var library: SessionLibrary
    private lateinit var queueEditor: QueueEditor
    private lateinit var albumPlays: AlbumPlays
    private lateinit var savedQueues: SavedQueueLoader
    private lateinit var favorite: FavoriteToggle
    private var session: MediaLibrarySession? = null
    private var preferences = UserPreferences()

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + components.mainDispatcher)
        val replayGainProcessor = ReplayGainProcessor()
        exoPlayer = components.createExoPlayer(this, replayGainProcessor)
        player = SeguePlayer(exoPlayer)
        queueSyncListener = QueueSyncListener(player, components.queueSync)
        queueEditor = components.createQueueEditor(player)
        albumPlays = components.createAlbumPlays(queueEditor)
        savedQueues = components.createSavedQueueLoader(player, queueEditor)
        favorite = components.createFavoriteToggle(player, scope) { updateSessionState() }
        sleepTimer = SleepTimerController(exoPlayer, scope) { updateSessionState() }
        prefetcher = components.createPrefetcher(scope)
        offload = ScreenOffOffload(this, exoPlayer).also { it.start() }
        replayGain = ReplayGainController(exoPlayer, replayGainProcessor, offload)
        library = components.createSessionLibrary(this, scope)

        val sessionPlayer = SessionPlayer(player, scope, components::shouldSyncBeforePlay) {
            val canNotify = NotificationManagerCompat.from(this).areNotificationsEnabled()
            if (components.queueSync.syncForResume(canNotify)) savedQueues.adopt()
        }
        session = MediaLibrarySession.Builder(this, sessionPlayer, SessionCallback())
            .setBitmapLoader(components.createBitmapLoader(this))
            .apply { launchIntent()?.let(::setSessionActivity) }
            .build()

        exoPlayer.addListener(PlayerEvents())
        exoPlayer.addListener(replayGain)
        exoPlayer.addListener(LoadingWifiLock(exoPlayer))
        exoPlayer.addListener(queueSyncListener)
        exoPlayer.addListener(sleepTimer)
        exoPlayer.addListener(components.createScrobbler(player, scope))
        exoPlayer.addListener(
            IdleStop(player, scope, { preferences.stopWhenPausedMinutes * MILLIS_PER_MINUTE }) {
                pauseAllPlayersAndStopSelf()
            },
        )
        components.bridge.attach(player)

        scope.launch {
            components.preferences.preferences.collect { prefs ->
                preferences = prefs
                components.cacheEvictor.maxBytes = megabytes(prefs.streamCacheSizeMb)
                replayGain.update(ReplayGainSettings.from(prefs))
            }
        }
        scope.launch { components.networkType.drop(1).collect { updatePrefetch() } }
        scope.launch { savedQueues.restore() }
        // A replaced queue publishes its source with its timeline change; this catches the rest.
        scope.launch { queueEditor.source.drop(1).collect { updateSessionState() } }
        updateSessionState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        components.bridge.detach(player)
        if (player.mediaItemCount > 0) components.queueSync.flush(player.queueState())
        // Widgets keep showing the track, paused: the next tap restarts the service.
        components.nowPlaying.publish(NowPlayingSnapshot.of(player).copy(isPlaying = false))
        offload.stop()
        prefetcher.stop()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // --- Player events ---------------------------------------------------------------------------

    // Queue sync events come from QueueSyncListener; these are the service's other reactions.
    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateSessionState()
            updatePrefetch()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            // A cleared queue came from nowhere; songs added to it next keep that.
            if (timeline.isEmpty) queueEditor.setSource(QueueSource.Unknown)
            updatePrefetch()
            updateSessionState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) updatePrefetch()
            publishNowPlaying()
        }

        override fun onPlaybackStateChanged(playbackState: Int) = publishNowPlaying()

        // The current song has loaded, so the resolver knows what it asked for: the stream format.
        override fun onTracksChanged(tracks: Tracks) = updateSessionState()
    }

    /** Prefetch only while the user means to play; a paused queue does not need the radio. */
    private fun updatePrefetch() {
        if (exoPlayer.playWhenReady) prefetcher.update(player.upNext(PREFETCH_LOOKAHEAD))
    }

    // --- Session state: extras and buttons -------------------------------------------------------

    private fun updateSessionState() {
        val session = session ?: return
        session.sessionExtras = sessionExtras()
        session.setMediaButtonPreferences(mediaButtons())
        publishNowPlaying()
    }

    /** Everything the app's controller reads from the session extras, in one bundle: add to it, never replace it. */
    private fun sessionExtras() = Bundle().apply {
        putAll(SegueSessionCommands.sleepArgs(sleepTimer.timer))
        putAll(SegueSessionCommands.queueSourceArgs(queueEditor.source.value))
        streamRequestArgs()?.let(::putAll)
    }

    /** What the player asked for when it opened the current song, for the stream format. */
    private fun streamRequestArgs(): Bundle? {
        val songId = exoPlayer.currentMediaItem?.mediaId ?: return null
        val request = components.streamRequest(songId) ?: return null
        return SegueSessionCommands.streamRequestArgs(songId, request)
    }

    /** Widgets follow player events: track and play/pause. Unchanged snapshots are dropped. */
    private fun publishNowPlaying() = components.nowPlaying.publish(NowPlayingSnapshot.of(player))

    private fun mediaButtons(): List<CommandButton> {
        val starred = exoPlayer.currentMediaItem?.isStarred == true
        val heart = CommandButton.Builder(
            if (starred) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setDisplayName(getString(if (starred) R.string.playback_unfavorite else R.string.playback_favorite))
            .setSessionCommand(SegueSessionCommands.command(SegueSessionCommands.TOGGLE_FAVORITE))
            // Overflow too: the platform session (System UI, Android Auto) only takes back,
            // forward and overflow buttons as custom actions, and drops secondary-only ones.
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        // No shuffle button: Segue shuffles only when music is queued, never a running queue.
        return listOf(heart)
    }

    private fun notAllowed(controller: MediaSession.ControllerInfo) =
        SecurityException("${controller.packageName} may not play from the library")

    /** Fails a set or add request: Media3 then leaves the queue alone. */
    private fun nothingToPlay() = UnsupportedOperationException("Nothing to play")

    /**
     * A session request's album play ("Shuffle albums", "play some music"): the songs requested with
     * it, then every album, for the session to queue. Null if there is nothing to play.
     */
    private suspend fun albumItems(play: SessionPlay.Albums): List<MediaItem>? =
        albumPlays.items(play.request, play.songs).ifEmpty { null }

    /** The queue source of a controller's own album play: the whole library is the only one named without a lookup. */
    private fun sourceOf(request: AlbumPlayRequest): QueueSource = when (request.source) {
        AlbumSource.Library -> QueueSource.Library(shuffled = request.order != AlbumOrder.InOrder)
        else -> QueueSource.Unknown
    }

    private fun currentQueue() = MediaSession.MediaItemsWithStartPosition(
        List(player.mediaItemCount) { player.getMediaItemAt(it) },
        player.currentMediaItemIndex,
        player.currentPosition,
    )

    private fun launchIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Playback controls for everyone; the library only for allowed browsers (Auto, System UI, us).
            val base = if (library.mayBrowse(controller)) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            }
            val commands = base.buildUpon()
                .apply { SegueSessionCommands.all.forEach(::add) }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(mediaButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                SegueSessionCommands.TOGGLE_FAVORITE -> favorite.toggle()

                SegueSessionCommands.SET_SLEEP_TIMER -> sleepTimer.set(SegueSessionCommands.sleepTimer(args))

                SegueSessionCommands.ADOPT_QUEUE -> {
                    return scope.launchFuture {
                        val adopted = savedQueues.adopt()
                        // The service's own player: queue sync just took the queue it plays.
                        if (adopted && SegueSessionCommands.adoptPlays(args)) player.play()
                        SessionResult(SessionResult.RESULT_SUCCESS, SegueSessionCommands.startedResult(adopted))
                    }
                }

                SegueSessionCommands.UNDO_ADOPTION -> scope.launch { savedQueues.undoAdoption() }

                SegueSessionCommands.PLAY_ALBUMS -> {
                    val request = SegueSessionCommands.playAlbums(args)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    val source = SegueSessionCommands.queueSource(args)
                    return scope.launchFuture {
                        val started = albumPlays.start(request, source)
                        SessionResult(SessionResult.RESULT_SUCCESS, SegueSessionCommands.startedResult(started))
                    }
                }

                SegueSessionCommands.UNDO_REMOVE -> {
                    val removed = SegueSessionCommands.undoRemove(args)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    queueEditor.putBack(removed.item, removed.index)
                }

                SegueSessionCommands.QUEUE_SONGS -> {
                    val songs = SegueSessionCommands.queueSongs(args)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    return scope.launchFuture {
                        val queued = with(songs) { queueEditor.queue(items, placement, startIndex, shuffle, source) }
                        SessionResult(SessionResult.RESULT_SUCCESS, SegueSessionCommands.startedResult(queued))
                    }
                }

                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Plain songs from any controller, or a browse node from an allowed browser: what can play of them. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val allowed = library.mayBrowse(controller)
            return scope.launchFuture {
                when (val play = library.plays.resolveAdd(mediaItems, allowed)) {
                    is SessionPlay.Items -> queueEditor.playable(play.items, shuffle = play.shuffle)?.mediaItems

                    // "Shuffle albums": every album, after any songs added with it.
                    is SessionPlay.Albums -> albumItems(play)

                    SessionPlay.KeepQueue -> emptyList()

                    // Only a set keeps the queue.

                    SessionPlay.NotAllowed -> throw notAllowed(controller)

                    SessionPlay.NothingToPlay -> null
                } ?: throw nothingToPlay()
            }
        }

        /** Plain songs, a browse node or a voice "play <query>": what can play of the list, from the chosen track. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val allowed = library.mayBrowse(controller)
            return scope.launchFuture {
                val hasQueue = player.mediaItemCount > 0
                when (val play = library.plays.resolveSet(mediaItems, startIndex, startPositionMs, allowed, hasQueue)) {
                    is SessionPlay.Items ->
                        queueEditor.playable(play.items, play.startIndex, play.startPositionMs, play.shuffle)
                            ?.also { queueEditor.setSource(QueueSource.Unknown) }

                    is SessionPlay.Albums ->
                        albumItems(play)?.let { items ->
                            queueEditor.setSource(sourceOf(play.request))
                            MediaSession.MediaItemsWithStartPosition(items, 0, 0)
                        }

                    // "Play music on Segue" with a queue: carry on with it.
                    SessionPlay.KeepQueue -> currentQueue()

                    SessionPlay.NotAllowed -> throw notAllowed(controller)

                    SessionPlay.NothingToPlay -> null
                } ?: throw nothingToPlay()
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = library.root(session, browser)

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            library.children(parentId, page, pageSize, params)

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = library.item(mediaId)

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = library.search(session, browser, query, params)

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            library.searchResult(query, page, pageSize, params)

        /** System "resume playback" (e.g. a Bluetooth play button with nothing queued): the local snapshot. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.launchFuture {
            val resumed = savedQueues.resumption() ?: throw UnsupportedOperationException("Nothing to resume")
            queueEditor.setSource(QueueSource.Unknown)
            resumed
        }
    }

    private companion object {
        const val PREFETCH_LOOKAHEAD = 3
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
