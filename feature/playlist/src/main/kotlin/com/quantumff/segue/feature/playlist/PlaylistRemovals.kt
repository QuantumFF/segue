package com.quantumff.segue.feature.playlist

import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarSlot
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.Song
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Songs removed from playlists by their ⋮ "Remove from playlist" (DESIGN.md §4.5). A removal
 * hides its row at once and posts "Removed <title>" with Undo to the app's [Snackbars]. The server
 * write waits until that snackbar is dismissed or times out; Undo cancels it, so nothing reaches
 * the server. The write runs in the app's scope, so it still goes out after the playlist screen
 * has closed. As with the queue (DESIGN.md §4.8), a newer removal takes the earlier one's
 * snackbar down, which writes it.
 *
 * Subsonic removes songs by position, and positions shift as songs are removed. So a removal is
 * held as which copy of which song it is ([PlaylistEntry.copy]), and only turned into a position
 * against the saved order when its write runs. Writes, and the other changes passed to
 * [afterRemovals], run one at a time in the order they were started.
 */
@Singleton
class PlaylistRemovals @Inject constructor(
    private val repository: PlaylistRepository,
    private val snackbars: Snackbars,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val removals = MutableStateFlow<List<Removal>>(emptyList())
    private val writes = Mutex()
    private val ids = AtomicLong()

    /** The removal whose Undo is showing. */
    private val undo = SnackbarSlot<Removal>(snackbars)

    /** [playlistId]'s saved order, without the songs removed from it that are not written yet. */
    fun entries(playlistId: String): Flow<ImmutableList<PlaylistEntry>> =
        combine(repository.playlistSongs(playlistId), removals) { songs, all ->
            val removals = all.filter { it.playlistId == playlistId }
            songs.toEntries().filterNot { entry -> removals.any { it.hides(entry, songs) } }.toImmutableList()
        }

    /** Removes [entry] from [playlistId]'s saved order once its Undo is gone. */
    fun remove(playlistId: String, entry: PlaylistEntry) {
        val removal = Removal(ids.incrementAndGet(), playlistId, entry.song.id, entry.copy)
        removals.update { it + removal }
        undo.post(
            SnackbarText.of(R.string.feature_playlist_removed, entry.song.title),
            SnackbarAction.undo(onDismiss = { write(removal.id) }) { drop(removal) },
            about = removal,
        )
    }

    /** Starts writing [playlistId]'s pending removal now, taking its Undo away; as when editing starts. */
    fun flush(playlistId: String) = undo.takeDown { it.playlistId == playlistId }

    /**
     * Runs [block], another change to [playlistId] such as saving an edit or deleting it, once its
     * removals are written, so they still find their songs.
     */
    suspend fun <T> afterRemovals(playlistId: String, block: suspend () -> T): T {
        flush(playlistId)
        return writes.withLock { block() }
    }

    /**
     * Writes removal [id], turning it into a position in the saved order as it is now. Undispatched,
     * so it queues for [writes] before the caller's next change does.
     */
    private fun write(id: Long) = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        writes.withLock {
            val removal = removals.value.firstOrNull { it.id == id } ?: return@withLock
            val songs = repository.playlistSongs(removal.playlistId).first()
            val position = songs.toEntries().indexOfFirst { it.song.id == removal.songId && it.copy == removal.copy }
            if (position < 0) {
                drop(removal)
                return@withLock
            }
            if (remoteOutcome { repository.removeSongs(removal.playlistId, listOf(position)) }.isFailure) {
                drop(removal)
                snackbars.post(SnackbarText.of(R.string.feature_playlist_error_generic))
                return@withLock
            }
            written(removal, copiesBefore = songs.count { it.id == removal.songId })
        }
        delay(SETTLE_MS)
        removals.value.firstOrNull { it.id == id }?.let(::drop)
    }

    private fun drop(removal: Removal) = removals.update { all -> all.filterNot { it.id == removal.id } }

    /**
     * [removal] reached the server, and the saved order no longer has it, or soon will not: its
     * song's later copies move up one. It keeps hiding its row until the saved order catches up,
     * for at most [SETTLE_MS].
     */
    private fun written(removal: Removal, copiesBefore: Int) = removals.update { all ->
        all.map {
            when {
                it.id == removal.id -> it.copy(copiesBefore = copiesBefore)

                it.playlistId == removal.playlistId && it.songId == removal.songId && it.copy > removal.copy ->
                    it.copy(copy = it.copy - 1)

                else -> it
            }
        }
    }

    /**
     * One removed row: copy [copy] (from 0) of [songId] in [playlistId]. [copiesBefore] is set once
     * it is written: how many copies of the song the saved order had before.
     */
    private data class Removal(
        val id: Long,
        val playlistId: String,
        val songId: String,
        val copy: Int,
        val copiesBefore: Int? = null,
    ) {
        /** Whether this hides [entry] of [songs]; once written, only until [songs] no longer lists it. */
        fun hides(entry: PlaylistEntry, songs: List<Song>): Boolean = entry.song.id == songId &&
            entry.copy == copy &&
            (copiesBefore == null || songs.count { it.id == songId } == copiesBefore)
    }

    private companion object {
        /** How long a written removal waits for the saved order to drop its song. */
        const val SETTLE_MS = 1_000L
    }
}

/** [this] saved order as entries, each numbered among the copies of its song. */
internal fun List<Song>.toEntries(): List<PlaylistEntry> {
    val copies = mutableMapOf<String, Int>()
    return map { song ->
        val copy = copies.getOrDefault(song.id, 0)
        copies[song.id] = copy + 1
        PlaylistEntry(song, copy)
    }
}
