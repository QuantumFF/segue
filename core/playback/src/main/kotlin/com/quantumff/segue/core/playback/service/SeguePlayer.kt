package com.quantumff.segue.core.playback.service

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
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
    private var expectedRestore: QueueState? = null
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
        applyMode(mode)
    }

    /**
     * Restores a saved queue exactly: [items] in queue order (they match [state]'s songs), the
     * current item and position, and the shuffle with its saved play order, so album blocks and
     * any manual reordering come back as they were instead of being reshuffled.
     */
    fun restore(items: List<MediaItem>, state: QueueState) {
        val playOrder = state.playOrder?.takeIf {
            state.shuffleMode != ShuffleMode.Off && it.sorted() == items.indices.toList()
        }
        if (playOrder == null) setShuffleMode(ShuffleMode.Off)
        setMediaItems(items, state.currentIndex, state.positionMillis)
        if (playOrder == null) return
        val order = when (state.shuffleMode) {
            ShuffleMode.Albums -> AlbumShuffleOrder.fromPlayOrder(currentAlbumKeys(), playOrder, random, this)
            else -> DefaultShuffleOrder(playOrder.toIntArray(), random.nextLong())
        }
        exo.setShuffleOrder(order)
        applyMode(state.shuffleMode)
    }

    /**
     * Puts back an item removed from queue position [timelineIndex] and play position
     * [playPosition] (the queue screen's undo), so it plays exactly where it did.
     */
    fun reinsert(item: MediaItem, timelineIndex: Int, playPosition: Int) {
        val index = timelineIndex.coerceIn(0, exo.mediaItemCount)
        addMediaItems(index, listOf(item))
        if (shuffleMode == ShuffleMode.Off) return
        val order = playOrder()
        val from = order.indexOf(index)
        val to = playPosition.coerceIn(0, order.lastIndex)
        if (from >= 0 && from != to) moveInPlayOrder(from, to)
    }

    private fun applyMode(mode: ShuffleMode) {
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

    /**
     * Moves [count] items at play position [from] so the first lands at play position [to]: in the
     * queue order when shuffle is off, in the shuffle order otherwise. A count above 1 moves an
     * album block in album shuffle.
     */
    fun moveInPlayOrder(from: Int, to: Int, count: Int = 1) {
        if (count < 1 || from < 0 || from + count > exo.mediaItemCount || to < 0 ||
            to + count > exo.mediaItemCount
        ) {
            return
        }
        if (shuffleMode == ShuffleMode.Off) {
            exo.moveMediaItems(from, from + count, to)
        } else {
            exo.setShuffleOrder(ShuffleOrders.moveInPlayOrder(exo.shuffleOrder, from, to, count, random))
        }
    }

    /**
     * Appends [items] so they play last and in this order, whatever the shuffle: Media3's track
     * shuffle would scatter them, which would break up the albums an album feed appends. (Album
     * shuffle already plays appended albums last, each whole.)
     */
    fun appendInPlayOrder(items: List<MediaItem>) {
        val from = exo.mediaItemCount
        addMediaItems(from, items)
        if (shuffleMode != ShuffleMode.Tracks || from == 0) return
        val inserted = from until from + items.size
        val order = playOrder().filterNot { it in inserted } + inserted
        exo.setShuffleOrder(ShuffleOrders.withPlayOrder(exo.shuffleOrder, order, random))
    }

    /**
     * Inserts [items] right after the current item so they play next, in this order: in track
     * shuffle too, even when the current item is the last in the queue (which [addMediaItems]
     * treats as an append). Album shuffle plays them after the current album.
     */
    fun insertNextInPlayOrder(items: List<MediaItem>) {
        val index = exo.currentMediaItemIndex + 1
        addMediaItems(index, items)
        if (shuffleMode != ShuffleMode.Tracks) return
        exo.setShuffleOrder(
            ShuffleOrders.playInsertedNext(exo.shuffleOrder, index, items.size, exo.currentMediaItemIndex, random),
        )
    }

    /** Queue indices in the order they play. */
    fun playOrder(): List<Int> = ShuffleOrders.effectivePlayOrder(shuffleMode, exo.shuffleOrder)

    /** The queue with its shuffle, for queue sync and the snapshot. See [QueueState.inPlayOrder] for uploads. */
    fun queueState(): QueueState = QueueState(
        songIds = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId },
        currentIndex = exo.currentMediaItemIndex,
        positionMillis = exo.currentPosition,
        shuffleMode = shuffleMode,
        playOrder = if (shuffleMode == ShuffleMode.Off) null else playOrder(),
    )

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
        val restoring = expectedRestore?.takeIf { it.songIds == mediaItems.map(MediaItem::mediaId) }
        expectedRestore = null
        if (restoring != null) {
            restore(mediaItems, restoring.copy(currentIndex = startIndex, positionMillis = startPositionMs))
            return
        }
        inserting(mediaItems) { super.setMediaItems(mediaItems, startIndex, startPositionMs) }
        afterReplacingPlaylist()
    }

    /**
     * The session is about to set [state]'s songs (system playback resumption): restore its
     * shuffle along with them. Ignored if different items arrive.
     */
    fun expectRestore(state: QueueState) {
        expectedRestore = state
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
