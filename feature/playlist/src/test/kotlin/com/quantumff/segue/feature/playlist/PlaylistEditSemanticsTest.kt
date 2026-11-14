package com.quantumff.segue.feature.playlist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack can reorder and remove playlist edit rows without dragging or swiping. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp") // Every row on screen, below the hero.
class PlaylistEditSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val moves = mutableListOf<Pair<Int, Int>>()
    private val removed = mutableListOf<String>()

    private val songs = listOf(TestData.song("a"), TestData.song("b"), TestData.song("c"))
    private val editing = PlaylistUiState(
        playlist = TestData.playlist("p1", name = "Road trip"),
        entries = persistentListOf(PlaylistEntry(songs[0]), PlaylistEntry(songs[1]), PlaylistEntry(songs[2])),
        loadState = LoadState.Loaded,
        isEditing = true,
    )

    @Test
    fun `each edit row offers Move up, Move down and Remove where they apply`() {
        show(editing)

        assertThat(rowActionLabels()).containsExactly(
            listOf("Move down", "Remove"),
            listOf("Move up", "Move down", "Remove"),
            listOf("Move up", "Remove"),
        ).inOrder()
    }

    @Test
    fun `the actions move and remove the row they are on`() {
        show(editing)

        row(1).performCustomAction("Move up")
        row(1).performCustomAction("Move down")
        row(1).performCustomAction("Remove")

        assertThat(moves).containsExactly(1 to 0, 1 to 2).inOrder()
        assertThat(removed).containsExactly(editing.entries[1].key)
    }

    @Test
    fun `edit rows are not announced as clickable`() {
        show(editing)

        rowNodes().assertCountEquals(songs.size)
        songs.forEachIndexed { index, song ->
            row(index).assertHasNoClickAction().assert(hasContentDescription(song.title, substring = true))
        }
    }

    @Test
    fun `rows outside edit mode have no edit actions`() {
        show(editing.copy(isEditing = false))

        rowNodes().assertCountEquals(0)
    }

    /** The edit rows: the nodes carrying a Remove custom action. */
    private fun rowNodes() = compose.onAllNodes(
        SemanticsMatcher("has a Remove custom action") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Remove" }
        },
    )

    private fun row(index: Int) = rowNodes()[index]

    private fun rowActionLabels() = rowNodes().fetchSemanticsNodes().map { node ->
        node.config[SemanticsActions.CustomActions].map { it.label }
    }

    private fun SemanticsNodeInteraction.performCustomAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { action.action() }
    }

    private fun show(state: PlaylistUiState) {
        compose.setContent {
            SegueTheme {
                PlaylistScreenContent(
                    uiState = state,
                    onBackClick = {},
                    onPlayClick = {},
                    onShuffleClick = {},
                    onShuffleAlbumsClick = {},
                    onTrackClick = {},
                    onTrackAction = { _, _, _ -> },
                    onTrackSwipe = { _, _ -> },
                    onPlaylistAction = {},
                    onEditClick = {},
                    onCancelEditClick = {},
                    onSaveClick = {},
                    onEditBack = {},
                    onKeepEditingClick = {},
                    onMove = { from, to -> moves += from to to },
                    onRemoveEntry = { removed += it },
                    onRename = {},
                    onDelete = {},
                    onRetryClick = {},
                    onMessageDismiss = {},
                    onOfflineModeSettingClick = {},
                )
            }
        }
    }
}
