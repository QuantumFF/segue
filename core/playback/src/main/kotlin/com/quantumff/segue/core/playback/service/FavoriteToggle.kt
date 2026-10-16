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
 * [StarRatingRepository] rolls Room back and the hearts go back with it.
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
    fun toggle() {
        val item = player.currentMediaItem ?: return
        val songId = item.mediaId
        val starred = !item.isStarred
        show(songId, starred)
        onChanged()
        callScope.launch {
            try {
                starRatings.setStarred(MediaKind.Song, songId, starred)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Signed out or the server refused. A toggle back meanwhile already shows this state.
                playerScope.launch { if (show(songId, !starred)) onChanged() }
            }
        }
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
