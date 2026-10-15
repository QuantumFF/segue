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
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.playback.browse.SessionLibrary
import com.quantumff.segue.core.playback.cache.NextTrackPrefetcher
import com.quantumff.segue.core.playback.cache.megabytes
import com.quantumff.segue.core.playback.media.coverArtId
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.media.replayGain
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.media.withPlayableUri
import com.quantumff.segue.core.playback.media.withStarred
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import com.quantumff.segue.core.playback.replaygain.ReplayGainVolume
import com.quantumff.segue.core.playback.scrobble.Scrobbler
import com.quantumff.segue.core.playback.service.PlaybackComponents
import com.quantumff.segue.core.playback.service.ScreenOffOffload
import com.quantumff.segue.core.playback.service.SeguePlayer
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.playback.sleep.SleepTimerController
import com.quantumff.segue.core.playback.sync.QueueEvent
import com.quantumff.segue.core.playback.sync.QueueState
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
    private lateinit var offload: ScreenOffOffload
    private lateinit var sleepTimer: SleepTimerController
    private lateinit var prefetcher: NextTrackPrefetcher
    private lateinit var library: SessionLibrary
    private var session: MediaLibrarySession? = null
    private var preferences = UserPreferences()
    private var idleStop: Job? = null
    private var hadItems = false

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + components.mainDispatcher)
        exoPlayer = components.createExoPlayer(this)
        player = SeguePlayer(exoPlayer) { updateSessionState() }
        sleepTimer = SleepTimerController(exoPlayer, scope) { updateSessionState() }
        prefetcher = components.createPrefetcher(scope)
        offload = ScreenOffOffload(this, exoPlayer).also { it.start() }
        library = SessionLibrary(this, scope, components.mediaBrowse, components.albumShuffle, components.search)

        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setBitmapLoader(components.createBitmapLoader(this))
            .apply { launchIntent()?.let(::setSessionActivity) }
            .build()

        exoPlayer.addListener(PlayerEvents())
        exoPlayer.addListener(sleepTimer)
        exoPlayer.addListener(Scrobbler(player, components.scrobbles, scope, components.appScope))
        components.bridge.player = player

        scope.launch {
            components.preferences.preferences.collect { prefs ->
                preferences = prefs
                player.preferredShuffleMode = prefs.defaultShuffleMode
                components.cacheEvictor.maxBytes = megabytes(prefs.streamCacheSizeMb)
                applyReplayGain()
            }
        }
        scope.launch { components.networkType.drop(1).collect { updatePrefetch() } }
        scope.launch { restoreSnapshot() }
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

    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyReplayGain()
            updateSessionState()
            queueEvent(QueueEvent.TrackTransition)
            updatePrefetch()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            val hasItems = player.mediaItemCount > 0
            // Empty after having songs is a deliberate clear, not the empty player before restore.
            if (hadItems && !hasItems) components.queueSync.onQueueCleared()
            hadItems = hasItems
            queueEvent(QueueEvent.QueueEdit)
            updatePrefetch()
            updateSessionState()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            queueEvent(QueueEvent.QueueEdit)
            updatePrefetch()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) queueEvent(QueueEvent.Seek)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                updatePrefetch()
            } else {
                queueEvent(QueueEvent.Pause)
            }
            updateIdleStop()
            publishNowPlaying()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateIdleStop()
            publishNowPlaying()
        }
    }

    private fun queueEvent(event: QueueEvent) {
        if (player.mediaItemCount > 0) components.queueSync.onQueueEvent(event, player.queueState())
    }

    private fun applyReplayGain() {
        val gain = exoPlayer.currentMediaItem?.replayGain
        exoPlayer.volume = ReplayGainVolume.of(gain, preferences.replayGainMode, preferences.replayGainPreampDb)
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
    private suspend fun loadSnapshot(): Restored? {
        val snapshot = components.snapshots.snapshot.first() ?: return null
        val songs = components.library.songsByIds(snapshot.songIds)
        if (songs.isEmpty()) return null
        // Songs missing from the library are dropped; keep pointing at the same current song.
        val found = songs.mapTo(HashSet()) { it.id }
        val index = snapshot.songIds.take(snapshot.currentIndex).count { it in found }.coerceAtMost(songs.lastIndex)
        val currentFound = snapshot.songIds.getOrNull(snapshot.currentIndex) in found
        val position = if (currentFound) snapshot.positionMillis else 0
        return Restored(songs.map { it.toMediaItem() }, QueueState(songs.map { it.id }, index, position))
    }

    private suspend fun restoreSnapshot() {
        val restored = loadSnapshot() ?: return
        if (player.mediaItemCount > 0) return // Something was queued while we read the snapshot.
        // The snapshot is in effective play order, so it restores with shuffle off.
        player.setMediaItems(restored.items, restored.state.currentIndex, restored.state.positionMillis)
        components.queueSync.markRestored(restored.state)
    }

    // --- Session state: extras and buttons -------------------------------------------------------

    private fun updateSessionState() {
        val session = session ?: return
        session.sessionExtras = SegueSessionCommands.extras(player.shuffleMode, sleepTimer.timer)
        session.setMediaButtonPreferences(mediaButtons())
        publishNowPlaying()
    }

    /** Widgets follow player events: track, play/pause, shuffle mode. Unchanged snapshots are dropped. */
    private fun publishNowPlaying() = components.nowPlaying.publish(nowPlaying())

    private fun nowPlaying(): NowPlayingSnapshot {
        val item = exoPlayer.currentMediaItem ?: return NowPlayingSnapshot.Empty
        return NowPlayingSnapshot(
            songId = item.mediaId,
            title = item.mediaMetadata.title?.toString(),
            artist = item.mediaMetadata.artist?.toString(),
            coverArtId = item.coverArtId,
            isPlaying = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED,
            shuffleMode = player.shuffleMode,
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
        val (icon, label) = when (player.shuffleMode) {
            ShuffleMode.Off -> CommandButton.ICON_SHUFFLE_OFF to R.string.playback_shuffle_off
            ShuffleMode.Tracks -> CommandButton.ICON_SHUFFLE_ON to R.string.playback_shuffle_tracks
            ShuffleMode.Albums -> CommandButton.ICON_SHUFFLE_STAR to R.string.playback_shuffle_albums
        }
        val shuffle = CommandButton.Builder(icon)
            .setDisplayName(getString(label))
            .setSessionCommand(SegueSessionCommands.command(SegueSessionCommands.CYCLE_SHUFFLE))
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        return listOf(favorite, shuffle)
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
                SegueSessionCommands.CYCLE_SHUFFLE -> player.cycleShuffleMode()

                SegueSessionCommands.SET_SHUFFLE -> SegueSessionCommands.shuffleMode(args)?.let(player::setShuffleMode)

                SegueSessionCommands.TOGGLE_FAVORITE -> toggleFavorite()

                SegueSessionCommands.SET_SLEEP_TIMER -> sleepTimer.set(SegueSessionCommands.sleepTimer(args))

                SegueSessionCommands.MOVE_IN_PLAY_ORDER -> player.moveInPlayOrder(
                    args.getInt(SegueSessionCommands.ARG_FROM),
                    args.getInt(SegueSessionCommands.ARG_TO),
                )

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
            // Browse nodes (Android Auto) stand for songs from Room.
            val result = SettableFuture.create<List<MediaItem>>()
            scope.launch {
                result.set(
                    mediaItems.flatMap { item ->
                        library.songsFor(item)?.map { it.toMediaItem() } ?: listOf(item.withPlayableUri())
                    },
                )
            }
            return result
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
            val item = mediaItems.single()
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                if (item.requestMetadata.searchQuery?.isBlank() == true && player.mediaItemCount > 0) {
                    // "Play music on Segue" with a queue: carry on with it.
                    result.set(currentQueue())
                    return@launch
                }
                val request = try {
                    library.playRequest(item)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    result.setException(e)
                    return@launch
                }
                if (request == null) {
                    result.setException(UnsupportedOperationException("Nothing to play for ${item.mediaId}"))
                    return@launch
                }
                request.shuffleMode?.let(player::setShuffleMode)
                result.set(
                    MediaSession.MediaItemsWithStartPosition(
                        request.songs.map { it.toMediaItem() },
                        request.startIndex,
                        0,
                    ),
                )
            }
            return result
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
