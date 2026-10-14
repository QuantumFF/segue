package com.quantumff.segue.core.playback.service

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.playback.media.albumKey
import com.quantumff.segue.core.playback.shuffle.AlbumShuffleOrder
import com.quantumff.segue.core.playback.shuffle.ShuffleOrders
import com.quantumff.segue.core.playback.sync.QueueState
import kotlin.random.Random

/**
 * The session's player: ExoPlayer plus Segue's three shuffle modes.
 *
 * Media3 knows only shuffle on/off and calls [androidx.media3.exoplayer.source.ShuffleOrder] with
 * bare indices. This wrapper records the album keys (and the current item) of items about to be
 * added, so [AlbumShuffleOrder] can place them, and maps the boolean shuffle toggle of generic
 * controllers (notification, Bluetooth, Android Auto) onto a mode.
 */
class SeguePlayer(
    private val exo: ExoPlayer,
    private val random: Random = Random.Default,
    private val onShuffleModeChanged: (ShuffleMode) -> Unit = {},
) : ForwardingPlayer(exo),
    AlbumShuffleOrder.InsertionSource {
    var shuffleMode: ShuffleMode = ShuffleMode.Off
        private set

    /** The mode a generic "shuffle on" picks. Kept in sync with the default shuffle setting. */
    var preferredShuffleMode: ShuffleMode = ShuffleMode.Tracks

    private var pendingKeys: List<String?>? = null
    private var pendingCurrent = C.INDEX_UNSET

    override fun albumKeys(count: Int): List<String?>? = pendingKeys

    override fun currentIndex(): Int = pendingCurrent

    fun setShuffleMode(mode: ShuffleMode) {
        val current = exo.currentMediaItemIndex.takeIf { exo.mediaItemCount > 0 } ?: C.INDEX_UNSET
        when (mode) {
            ShuffleMode.Off -> Unit

            ShuffleMode.Tracks -> exo.setShuffleOrder(ShuffleOrders.tracks(exo.mediaItemCount, current, random))

            ShuffleMode.Albums -> exo.setShuffleOrder(
                AlbumShuffleOrder.create(currentAlbumKeys(), current, random, this),
            )
        }
        exo.shuffleModeEnabled = mode != ShuffleMode.Off
        if (shuffleMode != mode) {
            shuffleMode = mode
            onShuffleModeChanged(mode)
        }
    }

    /** Off → Tracks → Albums → Off, for the notification button. */
    fun cycleShuffleMode() = setShuffleMode(ShuffleMode.entries[(shuffleMode.ordinal + 1) % ShuffleMode.entries.size])

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        val mode = if (!shuffleModeEnabled) ShuffleMode.Off else preferredShuffleMode.takeIf { it != ShuffleMode.Off }
        setShuffleMode(mode ?: ShuffleMode.Tracks)
    }

    /** Moves an item in play order: the queue order when shuffle is off, the shuffle order otherwise. */
    fun moveInPlayOrder(from: Int, to: Int) {
        if (shuffleMode == ShuffleMode.Off) {
            exo.moveMediaItem(from, to)
        } else {
            exo.setShuffleOrder(ShuffleOrders.moveInPlayOrder(exo.shuffleOrder, from, to, random))
        }
    }

    /** Queue indices in the order they play. */
    fun playOrder(): List<Int> = ShuffleOrders.effectivePlayOrder(shuffleMode, exo.shuffleOrder)

    /** The queue in effective play order, for queue sync. */
    fun queueState(): QueueState {
        val order = playOrder()
        return QueueState(
            songIds = order.map { exo.getMediaItemAt(it).mediaId },
            currentIndex = order.indexOf(exo.currentMediaItemIndex),
            positionMillis = exo.currentPosition,
        )
    }

    /** Song IDs after the current item in play order, at most [limit]. */
    fun upNext(limit: Int): List<String> {
        val order = playOrder()
        val position = order.indexOf(exo.currentMediaItemIndex)
        if (position < 0) return emptyList()
        return order.drop(position + 1).take(limit).map { exo.getMediaItemAt(it).mediaId }
    }

    // --- Playlist changes: record album keys for the shuffle order ------------------------------

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        inserting(mediaItems) { super.setMediaItems(mediaItems, resetPosition) }
        afterReplacingPlaylist()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        inserting(mediaItems) { super.setMediaItems(mediaItems, startIndex, startPositionMs) }
        afterReplacingPlaylist()
    }

    override fun setMediaItem(mediaItem: MediaItem) = setMediaItems(listOf(mediaItem))

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
        setMediaItems(listOf(mediaItem), resetPosition)

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
        setMediaItems(listOf(mediaItem), 0, startPositionMs)

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(exo.mediaItemCount, listOf(mediaItem))

    override fun addMediaItem(index: Int, mediaItem: MediaItem) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) = addMediaItems(exo.mediaItemCount, mediaItems)

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        val currentBefore = exo.currentMediaItemIndex
        val playNext = exo.mediaItemCount > 0 && index == currentBefore + 1 && index < exo.mediaItemCount
        inserting(mediaItems) { super.addMediaItems(index, mediaItems) }
        // DefaultShuffleOrder scatters inserts randomly; "play next" should still play next.
        if (playNext && shuffleMode == ShuffleMode.Tracks) {
            exo.setShuffleOrder(
                ShuffleOrders.playInsertedNext(
                    exo.shuffleOrder,
                    index,
                    mediaItems.size,
                    exo.currentMediaItemIndex,
                    random,
                ),
            )
        }
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) =
        replaceMediaItems(index, index + 1, listOf(mediaItem))

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) =
        inserting(mediaItems) { super.replaceMediaItems(fromIndex, toIndex, mediaItems) }

    private inline fun inserting(items: List<MediaItem>, block: () -> Unit) {
        pendingKeys = items.map { it.albumKey }
        pendingCurrent = if (exo.mediaItemCount > 0) exo.currentMediaItemIndex else C.INDEX_UNSET
        try {
            block()
        } finally {
            pendingKeys = null
            pendingCurrent = C.INDEX_UNSET
        }
    }

    /**
     * A new playlist gets a fresh shuffle that starts with the start item. [AlbumShuffleOrder]
     * does this itself in `cloneAndSet`; Media3's track shuffle does not, so it is rebuilt.
     */
    private fun afterReplacingPlaylist() {
        if (shuffleMode == ShuffleMode.Tracks && exo.mediaItemCount > 0) {
            exo.setShuffleOrder(ShuffleOrders.tracks(exo.mediaItemCount, exo.currentMediaItemIndex, random))
        }
    }

    private fun currentAlbumKeys(): List<String?> = List(exo.mediaItemCount) { exo.getMediaItemAt(it).albumKey }
}
