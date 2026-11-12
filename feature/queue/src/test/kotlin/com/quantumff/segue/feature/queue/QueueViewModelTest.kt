package com.quantumff.segue.feature.queue

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.play.FakePlayerStateSource
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

/** The queue's row actions (DESIGN.md §4.8): swipes and the ⋮ menu. */
class QueueViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun item(id: String, position: Int, starred: Boolean = false) = QueueItem(
        songId = id,
        timelineIndex = position,
        title = id.uppercase(),
        artist = "Artist",
        album = "Album",
        albumId = "al-$id",
        coverArtId = null,
        durationMillis = 200_000,
        isStarred = starred,
        artistId = "ar-$id",
    )

    private val player = FakePlayerStateSource(
        PlayerUiState(
            queue = persistentListOf(item("a", 0), item("b", 1), item("c", 2, starred = true)),
            currentQueueIndex = 0,
        ),
    )
    private val controls = RecordingQueueControls()
    private val snackbars = Snackbars()

    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { QueueViewModel(player, controls, snackbars) }

    private val opened = mutableListOf<String>()
    private val host = QueueMenuHost(
        onAddToPlaylist = { opened += "add to playlist $it" },
        onAlbumClick = { opened += "album $it" },
        onArtistClick = { opened += "artist $it" },
        onInfo = { opened += "info $it" },
    )
    private val actions by lazy { viewModel.actions(host) }

    private fun row(id: String) =
        QueueList.build(player.state.value.queue, 0, isPlaying = false, historyExpanded = false)
            .filterIsInstance<QueueListItem.Track>()
            .single { it.song.id == id }

    /** Removes [row] one way, then reports the player's commands and the snackbar, and taps its Undo. */
    private fun removalBy(remove: (QueueListItem.Track) -> Unit): Pair<List<String>, SnackbarText?> {
        controls.commands.clear()
        remove(row("b"))
        val shown = snackbars.current.value
        shown?.let(snackbars::onAction)
        return controls.commands.toList() to shown?.text
    }

    @Test
    fun `Remove from the menu removes and offers Undo just like the swipe`() {
        val swiped = removalBy(actions.onRemove)
        val fromMenu = removalBy { actions.onMenuAction(it, QueueMenuAction.Remove) }

        assertThat(swiped.first).containsExactly("remove 1", "undoRemove").inOrder()
        assertThat(swiped.second).isEqualTo(SnackbarText.of(R.string.feature_queue_removed, "B"))
        assertThat(fromMenu).isEqualTo(swiped)
    }

    @Test
    fun `Play next from the menu moves the track like the swipe`() {
        actions.onMenuAction(row("c"), QueueMenuAction.PlayNext)

        assertThat(controls.commands).containsExactly("move 2 1")
    }

    @Test
    fun `Favorite stars that queued song`() {
        actions.onMenuAction(row("b"), QueueMenuAction.Favorite)

        assertThat(controls.commands).containsExactly("toggleFavorite b")
    }

    @Test
    fun `the other menu rows open what the app shows for the song`() {
        val c = row("c")
        actions.onMenuAction(c, QueueMenuAction.AddToPlaylist)
        actions.onMenuAction(c, QueueMenuAction.GoToAlbum)
        actions.onMenuAction(c, QueueMenuAction.GoToArtist)
        actions.onMenuAction(c, QueueMenuAction.Info)

        assertThat(opened).containsExactly("add to playlist [c]", "album al-c", "artist ar-c", "info c").inOrder()
        assertThat(controls.commands).isEmpty()
    }
}
