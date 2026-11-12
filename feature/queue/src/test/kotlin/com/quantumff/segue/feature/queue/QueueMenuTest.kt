package com.quantumff.segue.feature.queue

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A queue track's ⋮ menu (DESIGN.md §4.8). */
@RunWith(RobolectricTestRunner::class)
class QueueMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private fun track(
        albumId: String? = "al",
        artistId: String? = "ar",
        isStarred: Boolean = false,
        canPlayNext: Boolean = true,
    ) = QueueListItem.Track(
        key = "s#1",
        position = 4,
        row = TrackRowState("s#1", "Reckoner", "Radiohead", "In Rainbows", 290),
        song = QueueSong("s", albumId, artistId, isStarred),
        canPlayNext = canPlayNext,
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun menu(track: QueueListItem.Track) = queueMenuItems(track) { context.getString(it) }

    @Test
    fun `the menu lists the queue's actions in order, with Remove from queue in the error color`() {
        val items = menu(track())

        assertThat(items.map { it.label }).containsExactly(
            "Play next",
            "Remove from queue",
            "Add to playlist",
            "Go to album",
            "Go to artist",
            "Favorite",
            "Info",
        ).inOrder()
        assertThat(items.filter { it.destructive }.map { it.label }).containsExactly("Remove from queue")
    }

    @Test
    fun `a starred song offers Unfavorite`() {
        val favorite = menu(track(isStarred = true)).single { it.id == QueueMenuAction.Favorite.name }

        assertThat(favorite.label).isEqualTo("Remove from favorites")
    }

    @Test
    fun `rows with nowhere to go are left out`() {
        val labels = menu(track(albumId = null, artistId = null, canPlayNext = false)).map { it.label }

        assertThat(labels).containsExactly("Remove from queue", "Add to playlist", "Favorite", "Info").inOrder()
    }

    @Test
    fun `every row has a more button that opens its menu`() {
        val chosen = mutableListOf<Pair<String, QueueMenuAction>>()
        compose.setContent {
            SegueTheme {
                QueueContent(
                    state = PreviewQueue,
                    actions = QueueActions(onMenuAction = { track, action -> chosen += track.song.id to action }),
                )
            }
        }
        val more = compose.onAllNodesWithContentDescription("More options")
        more.assertCountEquals(PreviewQueue.items.count { it is QueueListItem.Track })

        more[2].performClick()
        compose.onNodeWithText("Remove from queue").performClick()

        assertThat(chosen).containsExactly("s3" to QueueMenuAction.Remove)
    }

    @Test
    fun `the menu acts on its row where the queue has it now`() {
        var state by mutableStateOf(PreviewQueue)
        val chosen = mutableListOf<QueueListItem.Track>()
        compose.setContent {
            SegueTheme {
                QueueContent(state = state, actions = QueueActions(onMenuAction = { track, _ -> chosen += track }))
            }
        }
        compose.onAllNodesWithContentDescription("More options")[2].performClick()

        // s2 leaves the queue while the menu is open: s3 moves up one place.
        state = PreviewQueue.withoutTrack("s2#1")
        compose.onNodeWithText("Remove from queue").performClick()

        assertThat(chosen.map { it.song.id to it.position }).containsExactly("s3" to 4)
    }

    @Test
    fun `the menu closes when its row leaves the queue`() {
        var state by mutableStateOf(PreviewQueue)
        compose.setContent {
            SegueTheme { QueueContent(state = state, actions = QueueActions()) }
        }
        compose.onAllNodesWithContentDescription("More options")[2].performClick()
        compose.onNodeWithText("Remove from queue").assertExists()

        state = PreviewQueue.withoutTrack("s3#1")

        compose.onNodeWithText("Remove from queue").assertDoesNotExist()
    }

    /** [this] with the track [key] removed, and the tracks after it one place earlier. */
    private fun QueueUiState.withoutTrack(key: String): QueueUiState {
        val removed = items.filterIsInstance<QueueListItem.Track>().single { it.key == key }
        return copy(
            items = items.filterNot { it.key == key }
                .map { if (it is QueueListItem.Track && it.position > removed.position) it.copy(position = it.position - 1) else it }
                .toImmutableList(),
            trackCount = trackCount - 1,
        )
    }

    @Test
    fun `a dragged row hides its more button`() {
        compose.setContent {
            SegueTheme {
                QueueTrackRow(state = track().row, isDragging = true, onClick = {}, onMoreClick = {})
            }
        }

        compose.onAllNodesWithContentDescription("More options").assertCountEquals(0)
    }
}
