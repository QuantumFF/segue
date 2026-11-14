package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Horizontal gestures inside a Library tab win over swiping between tabs (DESIGN.md §4.2). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryInTabGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = List(3) { TestData.album(id = "$it") }
    private val songs = List(3) { TestData.song(id = "$it") }
    private var state by mutableStateOf(
        LibraryUiState(
            tab = LibraryTab.Songs,
            albumCount = albums.size,
            artistCount = 0,
            songCount = songs.size,
            syncProgress = SyncProgress.Succeeded,
        ),
    )
    private val events = mutableListOf<LibraryEvent>()

    @Test
    fun `swiping a song row acts on the song instead of changing tabs`() {
        show()

        compose.onNodeWithText(songs[0].title).performTouchInput { swipeRight() }
        compose.onNodeWithText(songs[1].title).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertThat(events).containsExactly(
            LibraryEvent.SongSwipe(songs[0], QueuePlacement.Next),
            LibraryEvent.SongSwipe(songs[1], QueuePlacement.End),
        ).inOrder()
        compose.onNodeWithText(songs[0].title).assertIsDisplayed()
    }

    @Test
    fun `swiping on the filter row's count changes tabs`() {
        show()

        swipeLeftAcrossScreen(at = compose.onNodeWithText("3 songs"))
        compose.waitForIdle()

        assertThat(events).containsExactly(LibraryEvent.SelectTab(LibraryTab.Playlists))
    }

    @Test
    fun `swiping on the empty space under the songs changes tabs`() {
        show()

        compose.onRoot().performTouchInput { swipe(Offset(right - 1f, bottom - 1f), Offset(left + 1f, bottom - 1f)) }
        compose.waitForIdle()

        assertThat(events).containsExactly(LibraryEvent.SelectTab(LibraryTab.Playlists))
    }

    @Test
    @Config(qualifiers = "w260dp-h800dp")
    fun `dragging the chips scrolls them, and changes tabs only once they are at their end`() {
        state = state.copy(tab = LibraryTab.Albums, albumCount = 1234)
        show()
        val overflow = chipScroll().maxValue()
        assertThat(overflow).isGreaterThan(0f)

        // A slow drag shorter than the chips' overflow only scrolls them.
        compose.onNodeWithText("Favorites").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - overflow / 2, durationMillis = SLOW_SWIPE_MILLIS)
        }
        compose.waitForIdle()
        assertThat(chipScroll().value()).isGreaterThan(0f)
        assertThat(chipScroll().value()).isLessThan(overflow)
        assertThat(events).isEmpty()

        // A swipe across the screen takes them to their end, and what's left changes tabs.
        swipeLeftAcrossScreen(at = compose.onNodeWithText("Favorites"))
        compose.waitForIdle()
        assertThat(events).containsExactly(LibraryEvent.SelectTab(LibraryTab.Artists))
    }

    @Test
    fun `pinching the album grid sideways resizes the cards instead of changing tabs`() {
        state = state.copy(tab = LibraryTab.Albums)
        show()

        compose.onNodeWithText(albums[0].name).performTouchInput {
            // Both fingers move sideways, one of them as far as a swipe that would change tabs.
            pinch(center - PINCH_NEAR, center - PINCH_FAR, center + PINCH_NEAR, center + PINCH_FAR)
        }
        // Lets the cards settle: a test torn down mid-spring stalls the next one's paging.
        compose.waitForIdle()

        // Two columns are usual here; spreading far zooms in to one.
        assertThat(events).containsExactly(LibraryEvent.SetGridSize(LibraryTab.Albums, GridSize(-1)))
        compose.onNodeWithText(albums[0].name).assertIsDisplayed()
    }

    @Test
    fun `dragging to the screen's edges after a long-press picks albums instead of changing tabs`() {
        state = state.copy(tab = LibraryTab.Albums)
        show()
        val first = compose.onNodeWithText(albums[0].name).fetchSemanticsNode().boundsInRoot.center
        val second = compose.onNodeWithText(albums[1].name).fetchSemanticsNode().boundsInRoot.center

        compose.onRoot().performTouchInput {
            down(first)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            // Two columns: the second album is beside the first. Each edge is past the albums.
            moveTo(second)
            moveTo(Offset(right - 1f, first.y))
            moveTo(first)
            moveTo(Offset(left + 1f, first.y))
            up()
        }
        compose.waitForIdle()

        assertThat(events).containsExactly(
            LibraryEvent.ToggleSelected(LibraryItem.OfAlbum(albums[0])),
            LibraryEvent.SetPicked(LibraryItem.OfAlbum(albums[1]), picked = true),
            LibraryEvent.SetPicked(LibraryItem.OfAlbum(albums[1]), picked = false),
        ).inOrder()
        compose.onNodeWithText(albums[0].name).assertIsDisplayed()
    }

    /** Swipes from the right of the screen to its left, level with the middle of [at]. */
    private fun swipeLeftAcrossScreen(at: SemanticsNodeInteraction) {
        val y = at.fetchSemanticsNode().boundsInRoot.center.y
        compose.onRoot().performTouchInput { swipe(Offset(right - 1f, y), Offset(left + 1f, y)) }
    }

    /** How far the filter chips scroll, in pixels. */
    private fun chipScroll() = compose
        .onNode(hasAnyDescendant(hasText("Favorites")) and SemanticsMatcher.keyIsDefined(HorizontalScrollAxisRange))
        .fetchSemanticsNode()
        .config[HorizontalScrollAxisRange]

    /** Plays the ViewModel's part for the tab: a selected tab becomes the state's and ends selecting. */
    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.from(albums)).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.from(songs)).collectAsLazyPagingItems(),
                    onEvent = { event ->
                        events += event
                        if (event is LibraryEvent.SelectTab && event.tab != state.tab) {
                            state = state.copy(tab = event.tab, selection = null)
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val SLOW_SWIPE_MILLIS = 1_000L
        const val LONG_PRESS_MARGIN_MILLIS = 100L
        val PINCH_NEAR = Offset(10f, 0f)
        val PINCH_FAR = Offset(200f, 0f)
    }
}
