package com.quantumff.segue.feature.artist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtonsInset
import com.quantumff.segue.core.designsystem.component.LocalShellSnackbarLift
import com.quantumff.segue.core.designsystem.component.ShellSnackbarLift
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.MediaActionFailure
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The artist page's own behaviors (DESIGN.md §4.4): Play and Shuffle wait while a play resolves, with
 * a spinner on the tapped one; About offers More only for a biography that does not fit; and
 * snackbars clear the floating Play / Shuffle albums buttons.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistPageBehaviorTest {
    @get:Rule
    val compose = createComposeRule()

    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun `while Shuffle resolves, Play and Shuffle are disabled and only Shuffle spins`() {
        show(PreviewArtistState.copy(preparing = PreparingPlayback.Shuffle))

        compose.onNodeWithContentDescription("Play").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Shuffle").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Instant mix").assertIsNotEnabled()
        compose.onAllNodes(spinner, useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(hasContentDescription("Shuffle") and hasAnyDescendant(spinner), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `while Play resolves, the Play button spins`() {
        show(PreviewArtistState.copy(preparing = PreparingPlayback.Play))

        compose.onNodeWithContentDescription("Play").assertIsNotEnabled()
        compose.onAllNodes(spinner, useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(hasContentDescription("Play") and hasAnyDescendant(spinner), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `nothing spins or waits while nothing resolves`() {
        show(PreviewArtistState)

        compose.onNodeWithContentDescription("Play").assertIsEnabled()
        compose.onNodeWithContentDescription("Shuffle").assertIsEnabled()
        compose.onAllNodes(spinner, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a biography that fits three lines has no More`() {
        compose.setContent { SegueTheme { About(biography = "A short biography.") } }

        compose.onNodeWithText("A short biography.").assertIsDisplayed()
        compose.onNodeWithText("More").assertDoesNotExist()
    }

    @Test
    fun `a longer biography has More, which expands it and turns into Less`() {
        compose.setContent { SegueTheme { About(biography = "A long biography. ".repeat(80)) } }

        compose.onNodeWithText("More").performClick()

        compose.onNodeWithText("Less").performClick()
        compose.onNodeWithText("More").assertExists()
    }

    @Test
    fun `snackbars sit above the floating Play and Shuffle albums buttons`() {
        compose.mainClock.autoAdvance = false
        show(PreviewArtistState.copy(message = MediaActionFailure.FavoriteFailed))
        compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)

        val snackbar = compose.onNodeWithText("Couldn't update favorite", substring = true)
            .getUnclippedBoundsInRoot()
        val buttons = compose.onNodeWithContentDescription("Play albums").getUnclippedBoundsInRoot()
        assertThat(snackbar.bottom.value).isAtMost(buttons.top.value)
    }

    @Test
    fun `the app's snackbars rise above the floating Play and Shuffle albums buttons too`() {
        val shellLift = ShellSnackbarLift()

        show(PreviewArtistState, shellLift)

        // Queue, download and playback feedback shows in the shell's host, outside the page.
        assertThat(shellLift.bottom).isEqualTo(FloatingAlbumPlayButtonsInset)
    }

    @Test
    fun `without albums the snackbars keep their usual place`() {
        compose.mainClock.autoAdvance = false
        show(
            PreviewArtistState.copy(
                albums = persistentListOf(),
                appearsOn = persistentListOf(),
                message = MediaActionFailure.FavoriteFailed,
            ),
        )
        compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)

        val snackbar = compose.onNodeWithText("Couldn't update favorite", substring = true)
            .getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertThat(root.bottom.value - snackbar.bottom.value).isLessThan(SNACKBAR_USUAL_GAP_MAX_DP)
    }

    /** The page as the app shell shows it, which lifts its own snackbars by [shellLift]. */
    private fun show(state: ArtistUiState.Success, shellLift: ShellSnackbarLift = ShellSnackbarLift()) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                CompositionLocalProvider(LocalShellSnackbarLift provides shellLift) {
                    ArtistScreen(
                        state = state,
                        onBackClick = {},
                        onAlbumClick = {},
                        onArtistClick = {},
                        onPlay = {},
                        onHeroPlay = {},
                        onPlayTopSong = {},
                        onShuffleAlbums = {},
                        onInstantMix = {},
                        onToggleStar = {},
                        onRetry = {},
                        onMessageDismiss = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val SNACKBAR_SETTLE_MS = 1_000L

        /** The host's own gap and the snackbar's inner padding, but not the buttons' inset. */
        const val SNACKBAR_USUAL_GAP_MAX_DP = 40f
    }
}
