package com.quantumff.segue.core.playback.service

import androidx.media3.common.Player
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.media.withStarred
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The session's favorite (heart) button: stars or unstars the current song. The heart changes at
 * once, on every queue item of that song, without interrupting playback. If the server call fails,
 * [StarRatingRepository] rolls Room back, and once the song's last toggle has an answer the hearts
 * show what the server last accepted.
 *
 * Call [toggle] on the player's thread.
 */
class FavoriteToggle(
    private val player: Player,
    private val starRatings: StarRatingRepository,
    /** Runs the server call; it outlives the service, so a toggle just before the service stops still lands. */
    private val callScope: CoroutineScope,
    /** The player's thread, for putting the hearts back; cancelled with the player. */
    private val playerScope: CoroutineScope,
    /** A heart changed: refresh the session's buttons. */
    private val onChanged: () -> Unit,
) {
    /** A song's toggles still waiting for the server: the latest one, and the state the server last accepted. */
    private class Toggles(var latest: Int, var accepted: Boolean)

    /** Per song with a server call on its way. Player's thread only, like [count]. */
    private val waiting = HashMap<String, Toggles>()
    private var count = 0

    fun toggle() {
        val item = player.currentMediaItem ?: return
        val songId = item.mediaId
        val starred = !item.isStarred
        val toggle = ++count
        waiting.getOrPut(songId) { Toggles(toggle, accepted = item.isStarred) }.latest = toggle
        show(songId, starred)
        onChanged()
        callScope.launch {
            val succeeded = try {
                starRatings.setStarred(MediaKind.Song, songId, starred)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Signed out or the server refused.
                false
            }
            playerScope.launch { answered(songId, toggle, starred, succeeded) }
        }
    }

    /**
     * The server answered [toggle] of [songId]. Once the song's latest toggle is answered its hearts
     * show what the server last accepted: a failed call does not just flip the heart back, since a
     * quick star and unstar that both fail would then end starred.
     */
    private fun answered(songId: String, toggle: Int, starred: Boolean, succeeded: Boolean) {
        val toggles = waiting[songId] ?: return
        if (succeeded) toggles.accepted = starred
        if (toggle != toggles.latest) return // A later toggle is on its way; its answer settles the hearts.
        waiting.remove(songId)
        if (show(songId, toggles.accepted)) onChanged()
    }

    /** Shows [songId] as [starred] on every queue item of it. True if any item changed. */
    private fun show(songId: String, starred: Boolean): Boolean {
        var changed = false
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            if (item.mediaId != songId || item.isStarred == starred) continue
            // Updates metadata in place; playback is not interrupted.
            player.replaceMediaItem(index, item.withStarred(starred))
            changed = true
        }
        return changed
    }
}
