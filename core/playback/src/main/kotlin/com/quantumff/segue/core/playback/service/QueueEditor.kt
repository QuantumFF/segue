package com.quantumff.segue.core.playback.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.playback.media.withPlayableUri
import kotlin.random.Random

/**
 * Turns a play intent into a queue edit, in the playback service. Every way music enters the queue
 * goes through here, so these rules hold for every controller (the app, Android Auto, Bluetooth):
 *
 * - **Offline:** only downloaded songs are queued, since nothing else can play (DESIGN.md §1). The
 *   start moves to the requested song if it is downloaded, else to the next downloaded song after
 *   it, else to the first one.
 * - **Shuffle at enqueue** (docs/PLAN.md, "Album shuffle"): a shuffled intent is queued in a fresh
 *   order, from its first song. A running queue is never reshuffled.
 * - **Placement:** replace (and play), next (right after the current item) or end. Into an empty
 *   queue, next and end set the queue and prepare it, without playing.
 *
 * [queue] does all three. A media session applies set and add requests itself; [playable] gives it
 * what to apply. [place] is the bare placement, for the album feed, which queues playable songs.
 * Call on the player's thread.
 */
class QueueEditor(
    private val player: Player,
    /** What is downloaded, while offline; null online. */
    private val offline: suspend () -> DownloadedLibrary?,
    private val random: Random = Random.Default,
) {
    /**
     * Queues [items] at [placement], from [startIndex] (ignored with [shuffle]). False if none of
     * them can play; the queue is then left alone.
     */
    suspend fun queue(
        items: List<MediaItem>,
        placement: QueuePlacement,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    ): Boolean {
        val playable = playable(items, startIndex, shuffle = shuffle) ?: return false
        place(playable.mediaItems, placement, playable.startIndex)
        return true
    }

    /**
     * [items] as they may be queued now: narrowed offline, shuffled with [shuffle], each with its
     * playable URI. [startIndex] and [startPositionMs] carry through (including Media3's "unset"
     * values) unless the start song was left out, in which case the new start begins at 0. Null if
     * nothing can play.
     */
    suspend fun playable(
        items: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
        shuffle: Boolean = false,
    ): MediaItemsWithStartPosition? {
        val downloaded = offline()?.songIds
        val kept = items.withIndex().filter { downloaded == null || it.value.mediaId in downloaded }
        if (kept.isEmpty()) return null
        val songs = kept.map { it.value.withPlayableUri() }
        if (shuffle) return MediaItemsWithStartPosition(songs.shuffled(random), 0, 0)
        if (startIndex == C.INDEX_UNSET) return MediaItemsWithStartPosition(songs, C.INDEX_UNSET, startPositionMs)
        val start = kept.indexOfFirst { it.index >= startIndex }.takeIf { it >= 0 } ?: 0
        val position = if (kept[start].index == startIndex) startPositionMs else 0
        return MediaItemsWithStartPosition(songs, start, position)
    }

    /**
     * Puts [items], which must be playable, into the queue at [placement]; a replaced queue starts
     * at [startIndex] and plays. Returns the queue position of the first item.
     */
    fun place(items: List<MediaItem>, placement: QueuePlacement, startIndex: Int = 0): Int = when {
        placement == QueuePlacement.Replace -> {
            player.setMediaItems(items, startIndex, 0)
            player.prepare()
            player.play()
            0
        }

        player.mediaItemCount == 0 -> {
            player.setMediaItems(items)
            player.prepare()
            0
        }

        placement == QueuePlacement.Next -> {
            val first = player.currentMediaItemIndex + 1
            player.addMediaItems(first, items)
            first
        }

        else -> {
            val first = player.mediaItemCount
            player.addMediaItems(items)
            first
        }
    }
}
