package com.quantumff.segue.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.SegueThemeConfig
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Swiping between the Library's tabs (DESIGN.md §4.2). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(emptyLibrary)
    private val events = mutableListOf<LibraryEvent>()

    @Test
    fun `a swipe left on an empty tab moves to the next tab once it settles`() {
        show()

        compose.onNodeWithTag(LIBRARY_PAGER_TAG).performTouchInput { swipeLeft() }

        compose.onNodeWithText("No artists yet").assertIsDisplayed()
        assertThat(events.filterIsInstance<LibraryEvent.SelectTab>())
            .containsExactly(LibraryEvent.SelectTab(LibraryTab.Artists))
        assertThat(state.tab).isEqualTo(LibraryTab.Artists)
    }

    @Test
    fun `a short swipe snaps back and keeps the selection`() {
        state = state.copy(selection = LibrarySelection.Albums(persistentListOf(TestData.album(id = "1"))))
        show()

        compose.onNodeWithTag(LIBRARY_PAGER_TAG).performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * SHORT_SWIPE, durationMillis = SLOW_SWIPE_MILLIS)
        }

        compose.onNodeWithText("No albums yet").assertIsDisplayed()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        assertThat(events).isEmpty()
    }

    @Test
    fun `mid-swipe only the tab and the neighbour it moves towards are composed`() {
        state = state.copy(tab = LibraryTab.Artists)
        show()

        compose.onNodeWithTag(LIBRARY_PAGER_TAG).performTouchInput {
            down(center)
            moveBy(Offset(-width * HALF_SWIPE, 0f))
        }

        compose.onNodeWithText("No artists yet").assertExists()
        compose.onNodeWithText("No songs yet").assertExists()
        compose.onNodeWithText("No albums yet").assertDoesNotExist()
        compose.onNodeWithText("No genres yet").assertDoesNotExist()
        compose.onNodeWithTag(LIBRARY_PAGER_TAG).performTouchInput { up() }
        finishAnimations()
    }

    @Test
    fun `tapping a far tab glides the pager there and settles only there`() {
        show()

        tab("Genres").performScrollTo().performClick()

        compose.onNodeWithText("No genres yet").assertIsDisplayed()
        tab("Genres").assertIsSelected()
        assertThat(events).containsExactly(LibraryEvent.SelectTab(LibraryTab.Genres))
    }

    @Test
    fun `tapping a tab with reduce motion on switches at once`() {
        show(reduceMotion = true)
        compose.mainClock.autoAdvance = false

        tab("Songs").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        tab("Songs").assertIsSelected()
        compose.onNodeWithText("No songs yet").assertExists()
        finishAnimations()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `with reduce motion a far tab crossfades in place of the tab it leaves`() {
        lateinit var libraryPager: LibraryPager
        compose.setContent {
            SegueTheme(config = SegueThemeConfig(reduceMotion = true)) {
                libraryPager = rememberLibraryPager(LibraryTab.Albums)
                LibraryPages(libraryPager, Modifier.fillMaxSize().background(Color.Black).testTag(LIBRARY_PAGER_TAG)) {
                        tab,
                        modifier,
                    ->
                    Box(modifier.fillMaxSize().background(colorOf(tab)).testTag(tab.name))
                }
            }
        }
        compose.mainClock.autoAdvance = false

        compose.runOnIdle { libraryPager.show(LibraryTab.Downloads) }
        compose.mainClock.advanceTimeBy(MID_CROSSFADE_MILLIS)

        compose.onNodeWithTag(LibraryTab.Albums.name).assertLeftPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithTag(LibraryTab.Downloads.name).assertLeftPositionInRootIsEqualTo(0.dp)
        val pixels = compose.onNodeWithTag(LIBRARY_PAGER_TAG).captureToImage().toPixelMap()
        val middle = pixels[pixels.width / 2, pixels.height / 2]
        assertThat(middle.red).isIn(Range.open(PARTLY, 1f - PARTLY))
        assertThat(middle.blue).isIn(Range.open(PARTLY, 1f - PARTLY))
        assertThat(middle.green).isLessThan(PARTLY)
        finishAnimations()
        compose.onNodeWithTag(LibraryTab.Albums.name).assertIsNotDisplayed()
        compose.onNodeWithTag(LibraryTab.Downloads.name).assertLeftPositionInRootIsEqualTo(0.dp)
    }

    /** The tab left (red) and the far tab tapped (blue) mix; the tabs passed over (green) stay hidden. */
    private fun colorOf(tab: LibraryTab) = when (tab) {
        LibraryTab.Albums -> Color.Red
        LibraryTab.Downloads -> Color.Blue
        else -> Color.Green
    }

    @Test
    fun `tapping a tab without reduce motion glides there`() {
        show()
        compose.mainClock.autoAdvance = false

        tab("Songs").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        tab("Albums").assertIsSelected()
        finishAnimations()
    }

    /** A test torn down mid-animation stalls the next one's paging. */
    private fun finishAnimations() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test
    fun `TalkBack changes tabs with the tab row, not the pager`() {
        show()

        compose.onNodeWithTag(LIBRARY_PAGER_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        compose.onNodeWithText("No albums yet").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility),
        )
        tab("Artists").assertIsNotSelected().performClick()
        tab("Artists").assertIsSelected()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())

    /** Plays the ViewModel's part for the tab: a selected tab becomes the state's and ends selecting. */
    private fun show(reduceMotion: Boolean = false) {
        compose.setContent {
            SegueTheme(config = SegueThemeConfig(reduceMotion = reduceMotion)) {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
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
        const val SHORT_SWIPE = 0.2f
        const val HALF_SWIPE = 0.4f
        const val SLOW_SWIPE_MILLIS = 1_000L

        /** A third of the way through the `medium` crossfade. */
        const val MID_CROSSFADE_MILLIS = 64L

        /** How far from none or all of a colour counts as a partial mix. */
        const val PARTLY = 0.1f
        val emptyLibrary = LibraryUiState(
            albumCount = 0,
            artistCount = 0,
            songCount = 0,
            syncProgress = SyncProgress.Succeeded,
        )
    }
}
