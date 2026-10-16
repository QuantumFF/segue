package com.quantumff.segue.core.playback

import android.app.PendingIntent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
import com.google.common.util.concurrent.SettableFuture
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.playback.browse.SessionLibrary
import com.quantumff.segue.core.playback.browse.launchFuture
import com.quantumff.segue.core.playback.cache.NextTrackPrefetcher
import com.quantumff.segue.core.playback.cache.megabytes
import com.quantumff.segue.core.playback.feed.AlbumFeedController
import com.quantumff.segue.core.playback.media.coverArtId
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.media.withPlayableUri
import com.quantumff.segue.core.playback.media.withStarred
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import com.quantumff.segue.core.playback.replaygain.ReplayGainController
import com.quantumff.segue.core.playback.replaygain.ReplayGainProcessor
import com.quantumff.segue.core.playback.replaygain.ReplayGainSettings
import com.quantumff.segue.core.playback.scrobble.Scrobbler
import com.quantumff.segue.core.playback.service.LoadingWifiLock
import com.quantumff.segue.core.playback.service.PlaybackComponents
import com.quantumff.segue.core.playback.service.ScreenOffOffload
import com.quantumff.segue.core.playback.service.SeguePlayer
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.playback.sleep.SleepTimerController
import com.quantumff.segue.core.playback.sync.QueueRestore
import com.quantumff.segue.core.playback.sync.QueueState
import com.quantumff.segue.core.playback.sync.QueueSyncListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the player and the media session for the app, the notification, and other controllers.
 *
 * - Media3 runs it in the foreground only while playing; after [UserPreferences.stopWhenPausedMinutes]
 *   paused it stops itself.
 * - At start it restores the local queue snapshot (paused, offline-capable).
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
    private lateinit var albumFeed: AlbumFeedController
    private var session: MediaLibrarySession? = null
    private var preferences = UserPreferences()
    private var idleStop: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + components.mainDispatcher)
        val replayGainProcessor = ReplayGainProcessor()
        exoPlayer = components.createExoPlayer(this, replayGainProcessor)
        player = SeguePlayer(exoPlayer)
        queueSyncListener = QueueSyncListener(player, components.queueSync)
        albumFeed = AlbumFeedController(
            player,
            components.albumShuffle,
            components.albumFeeds,
            scope,
            offline = components::downloadedWhileOffline,
        )
        sleepTimer = SleepTimerController(exoPlayer, scope) { updateSessionState() }
        prefetcher = components.createPrefetcher(scope)
        offload = ScreenOffOffload(this, exoPlayer).also { it.start() }
        replayGain = ReplayGainController(exoPlayer, replayGainProcessor, offload)
        library = SessionLibrary(
            this,
            scope,
            components.mediaBrowse,
            components.albumShuffle,
            components.search,
            downloadedWhileOffline = components::downloadedWhileOffline,
        )

        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setBitmapLoader(components.createBitmapLoader(this))
            .apply { launchIntent()?.let(::setSessionActivity) }
            .build()

        exoPlayer.addListener(PlayerEvents())
        exoPlayer.addListener(replayGain)
        exoPlayer.addListener(LoadingWifiLock(exoPlayer))
        exoPlayer.addListener(queueSyncListener)
        exoPlayer.addListener(albumFeed)
        exoPlayer.addListener(sleepTimer)
        exoPlayer.addListener(Scrobbler(player, components.scrobbles, scope, components.appScope))
        components.bridge.player = player

        scope.launch {
            components.preferences.preferences.collect { prefs ->
                preferences = prefs
                components.cacheEvictor.maxBytes = megabytes(prefs.streamCacheSizeMb)
                replayGain.update(ReplayGainSettings.from(prefs))
            }
        }
        scope.launch { components.networkType.drop(1).collect { updatePrefetch() } }
        scope.launch {
            restoreSnapshot()
            albumFeed.restore()
        }
        updateSessionState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        if (components.bridge.player === player) components.bridge.player = null
        if (player.mediaItemCount > 0) components.queueSync.onAppBackground(player.queueState())
        // Widgets keep showing the track, paused: the next tap restarts the service.
        components.nowPlaying.publish(nowPlaying().copy(isPlaying = false))
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
            updatePrefetch()
            updateSessionState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) updatePrefetch()
            updateIdleStop()
            publishNowPlaying()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateIdleStop()
            publishNowPlaying()
        }
    }

    /** Prefetch only while the user means to play; a paused queue does not need the radio. */
    private fun updatePrefetch() {
        if (exoPlayer.playWhenReady) prefetcher.update(player.upNext(PREFETCH_LOOKAHEAD))
    }

    /** Paused, or stopped at the end of the queue: nothing will play until the user acts. */
    private fun isIdle() = !exoPlayer.playWhenReady ||
        exoPlayer.playbackState == Player.STATE_ENDED ||
        exoPlayer.playbackState == Player.STATE_IDLE

    /** Arms one timer while idle; stops the service if it is still idle when it fires. */
    private fun updateIdleStop() {
        if (!isIdle()) {
            idleStop?.cancel()
            idleStop = null
            return
        }
        if (idleStop?.isActive == true) return
        idleStop = scope.launch {
            delay(preferences.stopWhenPausedMinutes * MILLIS_PER_MINUTE)
            if (isIdle()) pauseAllPlayersAndStopSelf()
        }
    }

    // --- Restore ---------------------------------------------------------------------------------

    private data class Restored(val items: List<MediaItem>, val state: QueueState)

    /** The local snapshot as playable items, from Room only, so it works offline. */
    private suspend fun loadSnapshot(): Restored? = components.snapshots.snapshot.first()?.let { load(it) }

    /** [snapshot] as playable items, from Room only. */
    private suspend fun load(snapshot: QueueSnapshot): Restored? {
        // In queue order with duplicates; songs missing from the library are dropped (see QueueRestore).
        val songs = components.library.songsByIds(snapshot.songIds)
        val state = QueueRestore.plan(snapshot, songs.mapTo(HashSet()) { it.id }) ?: return null
        return Restored(songs.map { it.toMediaItem() }, state)
    }

    private suspend fun restoreSnapshot() {
        val restored = loadSnapshot() ?: return
        if (player.mediaItemCount > 0) return // Something was queued while we read the snapshot.
        player.restore(restored.items, restored.state)
        components.queueSync.markRestored(player.queueState())
    }

    /**
     * Undo of an automatic remote adoption: the replaced local queue comes back, paused. The queue
     * edit is then saved and uploaded like any other.
     */
    private suspend fun undoAdoption() {
        val previous = components.queueSync.takeAdoptionUndo() ?: return
        val restored = load(previous) ?: return
        player.pause()
        player.restore(restored.items, restored.state)
    }

    // --- Session state: extras and buttons -------------------------------------------------------

    private fun updateSessionState() {
        val session = session ?: return
        session.sessionExtras = SegueSessionCommands.extras(sleepTimer.timer)
        session.setMediaButtonPreferences(mediaButtons())
        publishNowPlaying()
    }

    /** Widgets follow player events: track and play/pause. Unchanged snapshots are dropped. */
    private fun publishNowPlaying() = components.nowPlaying.publish(nowPlaying())

    private fun nowPlaying(): NowPlayingSnapshot {
        val item = exoPlayer.currentMediaItem ?: return NowPlayingSnapshot.Empty
        return NowPlayingSnapshot(
            songId = item.mediaId,
            title = item.mediaMetadata.title?.toString(),
            artist = item.mediaMetadata.artist?.toString(),
            coverArtId = item.coverArtId,
            isPlaying = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED,
        )
    }

    private fun mediaButtons(): List<CommandButton> {
        val starred = exoPlayer.currentMediaItem?.isStarred == true
        val favorite = CommandButton.Builder(
            if (starred) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setDisplayName(getString(if (starred) R.string.playback_unfavorite else R.string.playback_favorite))
            .setSessionCommand(SegueSessionCommands.command(SegueSessionCommands.TOGGLE_FAVORITE))
            // Overflow too: the platform session (System UI, Android Auto) only takes back,
            // forward and overflow buttons as custom actions, and drops secondary-only ones.
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        // No shuffle button: Segue shuffles only when music is queued, never a running queue.
        return listOf(favorite)
    }

    private fun toggleFavorite() {
        val index = exoPlayer.currentMediaItemIndex
        val item = exoPlayer.currentMediaItem ?: return
        val starred = !item.isStarred
        // Updates metadata in place; playback is not interrupted.
        player.replaceMediaItem(index, item.withStarred(starred))
        updateSessionState()
        components.appScope.launch {
            runCatching { components.starRatings.setStarred(MediaKind.Song, item.mediaId, starred) }
        }
    }

    private fun notAllowed(controller: MediaSession.ControllerInfo) =
        SecurityException("${controller.packageName} may not play from the library")

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
                SegueSessionCommands.TOGGLE_FAVORITE -> toggleFavorite()

                SegueSessionCommands.SET_SLEEP_TIMER -> sleepTimer.set(SegueSessionCommands.sleepTimer(args))

                SegueSessionCommands.UNDO_ADOPTION -> scope.launch { undoAdoption() }

                SegueSessionCommands.PLAY_ALBUMS -> {
                    val (request, seed) = SegueSessionCommands.playAlbums(args)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    return scope.launchFuture {
                        val started = albumFeed.start(request, seed)
                        SessionResult(SessionResult.RESULT_SUCCESS, SegueSessionCommands.startedResult(started))
                    }
                }

                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            if (!library.hasBrowseNodes(mediaItems)) {
                return Futures.immediateFuture(mediaItems.map { it.withPlayableUri() })
            }
            // Browse nodes (Android Auto) stand for songs from Room; only allowed browsers may use them.
            if (!library.mayBrowse(controller)) return Futures.immediateFailedFuture(notAllowed(controller))
            return scope.launchFuture {
                mediaItems.flatMap { item ->
                    library.songsFor(item)?.map { it.toMediaItem() } ?: listOf(item.withPlayableUri())
                }
            }
        }

        /** A browse node or a voice "play <query>": the whole list, from the chosen track. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (!library.resolves(mediaItems)) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            if (!library.mayBrowse(controller)) return Futures.immediateFailedFuture(notAllowed(controller))
            val item = mediaItems.single()
            return scope.launchFuture {
                if (item.requestMetadata.searchQuery?.isBlank() == true && player.mediaItemCount > 0) {
                    // "Play music on Segue" with a queue: carry on with it.
                    return@launchFuture currentQueue()
                }
                val request = library.playRequest(item)
                    ?: throw UnsupportedOperationException("Nothing to play for ${item.mediaId}")
                MediaSession.MediaItemsWithStartPosition(request.songs.map { it.toMediaItem() }, request.startIndex, 0)
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
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val restored = loadSnapshot()
                if (restored == null) {
                    result.setException(UnsupportedOperationException("Nothing to resume"))
                } else {
                    components.queueSync.markRestored(restored.state)
                    result.set(
                        MediaSession.MediaItemsWithStartPosition(
                            restored.items,
                            restored.state.currentIndex,
                            restored.state.positionMillis,
                        ),
                    )
                }
            }
            return result
        }
    }

    private companion object {
        const val PREFETCH_LOOKAHEAD = 3
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
