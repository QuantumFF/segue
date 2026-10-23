package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Offline in the Library: the status chip and the locked Downloaded chips (DESIGN.md §4.2, §5.8). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryOfflineTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(LibraryUiState(albumCount = 1, artistCount = 1, songCount = 1))
    private val events = mutableListOf<LibraryEvent>()

    @Test
    fun `the offline chip shows under the tabs only while offline`() {
        show()
        compose.onNodeWithText(OFFLINE_CHIP).assertDoesNotExist()

        state = state.copy(isOffline = true)
        compose.onNodeWithText(OFFLINE_CHIP).assertIsDisplayed()

        state = state.copy(tab = LibraryTab.Downloads)
        compose.onNodeWithText(OFFLINE_CHIP).assertIsDisplayed()
    }

    @Test
    fun `offline every Downloaded chip is locked on and ignores taps`() {
        state = state.copy(isOffline = true)
        show()

        listOf(LibraryTab.Albums, LibraryTab.Artists).forEach { tab ->
            state = state.copy(tab = tab)
            compose.onNodeWithText("Downloaded")
                .assertIsSelected()
                .assert(stateDescription("On while offline"))
                .performClick()
        }

        assertThat(events).isEmpty()
    }

    @Test
    fun `online the Downloaded chip has no locked state`() {
        state = state.copy(tab = LibraryTab.Artists)
        show()

        compose.onNodeWithText("Downloaded").assert(noStateDescription()).performClick()

        assertThat(events).containsExactly(LibraryEvent.CycleDownloaded(LibraryTab.Artists))
    }

    private fun stateDescription(value: String) = SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        value,
    )

    private fun noStateDescription() = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.from(listOf(TestData.album("1")))).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.from(listOf(TestData.artist("1")))).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = { events += it },
                )
            }
        }
    }

    private companion object {
        const val OFFLINE_CHIP = "Offline · showing downloads"
    }
}
