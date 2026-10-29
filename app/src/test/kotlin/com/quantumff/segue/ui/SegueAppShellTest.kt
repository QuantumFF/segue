package com.quantumff.segue.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.R
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.PlaybackError
import com.quantumff.segue.core.model.PlaybackErrorKind
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.feature.queue.QueueRemovals
import com.quantumff.segue.playback.ShellPlayerActions
import com.quantumff.segue.playback.ShellPlayerState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shell without Hilt: feature screens are still stateless placeholders. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SegueAppShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val snackbars = Snackbars()

    private fun setShell(layout: ShellLayout, player: ShellPlayerState = testPlayer) {
        compose.setContent {
            SegueTheme {
                val appState = rememberSegueAppState(shellLayout = layout)
                TestShell(appState, player, snackbars)
            }
        }
    }

    /** "Nothing to play", the text of a play intent that queued nothing. */
    private val nothingToPlay = SnackbarText.of(R.string.player_nothing_to_play)

    /** The snackbar showing [text], as a screen reader finds it: announced as a live region. */
    private fun announced(text: String): SemanticsNodeInteraction = compose.onNode(
        hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)) and hasText(text),
    )

    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and hasClickAction())

    private fun assertShown(text: String) {
        compose.onAllNodes(hasText(text)).onFirst().assertExists()
    }

    @Test
    fun `each tab keeps its own back stack, Home included`() {
        setShell(ShellLayout.Compact)

        compose.onNodeWithText("Open a sample album").performClick()
        assertShown("Album sample-album")

        tab("Library").performClick()
        compose.onNodeWithText("Open a sample artist").performClick()
        assertShown("Artist sample-artist")

        tab("Home").performClick()
        assertShown("Album sample-album")

        tab("Library").performClick()
        assertShown("Artist sample-artist")
    }

    @Test
    fun `reselecting a tab returns to its root`() {
        setShell(ShellLayout.Compact)

        tab("Library").performClick()
        compose.onNodeWithText("Open a sample artist").performClick()
        tab("Library").performClick()

        compose.onNodeWithText("Open a sample artist").assertExists()
    }

    @Test
    fun `back at another tab's root goes to Home`() {
        setShell(ShellLayout.Compact)

        tab("Playlists").performClick()
        compose.onNodeWithText("Open a sample playlist").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Open a sample album").assertExists()
    }

    @Test
    fun `phone layout docks the mini player above the bar`() {
        setShell(ShellLayout.Compact)

        compose.onNodeWithText(TEST_TRACK).assertExists()
        tab("Search").assertExists()
    }

    @Test
    fun `expanded layout shows the sidebar and the player bar`() {
        setShell(ShellLayout.Expanded)

        compose.onNodeWithText("PLAYLISTS").assertExists()
        compose.onNodeWithText(TEST_TRACK).assertExists()
        tab("Library").performClick()
        compose.onNodeWithText("Open a sample artist").assertExists()
    }

    @Test
    fun `now playing hides the navigation chrome`() {
        setShell(ShellLayout.Compact)

        compose.onNodeWithText(TEST_TRACK).performClick()

        compose.onNodeWithText("Now playing").assertExists()
        tab("Search").assertDoesNotExist()
        compose.onNodeWithText(TEST_TRACK).assertDoesNotExist()
    }

    @Test
    fun `a posted message is announced above the mini player`() {
        setShell(ShellLayout.Compact)

        snackbars.post(nothingToPlay)

        announced("Nothing to play").assertExists()
    }

    @Test
    fun `a posted message is announced over Now Playing`() {
        setShell(ShellLayout.Compact)
        compose.onNodeWithText(TEST_TRACK).performClick()

        snackbars.post(nothingToPlay)

        compose.onNodeWithText("Now playing").assertExists()
        announced("Nothing to play").assertExists()
    }

    @Test
    fun `a playback error is announced over Now Playing, Now Playing's one host`() {
        val error = PlaybackError("s1", "Nude", PlaybackErrorKind.Network)
        setShell(ShellLayout.Compact, player = testPlayer.copy(playbackError = error))
        compose.onNodeWithText(TEST_TRACK).performClick()

        compose.onNodeWithText("Now playing").assertExists()
        compose.onAllNodes(
            hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)) and hasText("Retry"),
        )
            .assertCountEquals(1)
    }

    @Test
    fun `a message counts as shown once it has timed out`() {
        setShell(ShellLayout.Compact)
        compose.mainClock.autoAdvance = false

        snackbars.post(nothingToPlay)
        compose.mainClock.advanceTimeBy(SHORT_SNACKBAR_MILLIS / 2)
        assertThat(snackbars.current.value).isNotNull()

        compose.mainClock.advanceTimeBy(SHORT_SNACKBAR_MILLIS)
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun `tapping a message's action runs it`() {
        setShell(ShellLayout.Compact)
        var undone = false

        snackbars.post(nothingToPlay, SnackbarAction.undo { undone = true })
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()

        assertThat(undone).isTrue()
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun `a message its poster takes back goes down at once`() {
        setShell(ShellLayout.Compact)
        val message = snackbars.post(nothingToPlay, SnackbarAction.undo {})
        announced("Nothing to play").assertExists()

        snackbars.onDismiss(message)

        announced("Nothing to play").assertDoesNotExist()
    }

    @Test
    fun `a queue removal's Undo outlives a panel change and collapsing Now Playing`() {
        var undone = false
        val removals = QueueRemovals(snackbars)
        compose.setContent {
            SegueTheme {
                val appState = rememberSegueAppState(shellLayout = ShellLayout.Compact)
                TestShell(appState, snackbars = snackbars, nowPlaying = {
                    PanelledNowPlaying(appState, onRemove = { removals.removed("Nude") { undone = true } })
                })
            }
        }
        compose.onNodeWithText(TEST_TRACK).performClick()

        compose.onNodeWithText("Remove Nude").performClick()
        compose.onNodeWithText("Lyrics panel").performClick()
        compose.onNodeWithText("Remove Nude").assertDoesNotExist()
        announced("Removed Nude").assertExists()

        compose.onNodeWithText("Collapse").performClick()
        compose.onNodeWithText(TEST_TRACK).assertExists()
        announced("Removed Nude").assertExists()

        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertThat(undone).isTrue()
    }

    @Test
    fun `an empty queue hides the mini player but keeps the bar`() {
        setShell(ShellLayout.Compact, player = ShellPlayerState())

        tab("Search").assertExists()
        compose.onNodeWithText(TEST_TRACK).assertDoesNotExist()
    }

    @Test
    fun `the expanded sidebar lists playlists and opens them`() {
        var opened: String? = null
        compose.setContent {
            SegueTheme {
                val appState = rememberSegueAppState(shellLayout = ShellLayout.Expanded)
                SegueApp(
                    appState = appState,
                    player = testPlayer,
                    positionMillis = remember { MutableStateFlow(0L) },
                    playerActions = ShellPlayerActions.None,
                    snackbars = snackbars,
                    sidebarPlaylists = persistentListOf(SidebarPlaylist("p1", "Road trip")),
                    onSidebarPlaylistClick = { opened = it },
                ) { TestNavHost(appState, it) }
            }
        }

        compose.onNodeWithText("Road trip").performClick()

        assertThat(opened).isEqualTo("p1")
    }

    @Test
    fun `an empty queue hides the expanded player bar`() {
        setShell(ShellLayout.Expanded, player = ShellPlayerState())

        compose.onNodeWithText("PLAYLISTS").assertExists()
        compose.onNodeWithText(TEST_TRACK).assertDoesNotExist()
    }

    private val offer = RemoteQueueOffer("Feishin", trackCount = 12, currentTitle = "Nude")

    @Test
    fun `a remote queue offer docks a continue bar above the mini player on any screen`() {
        setShell(ShellLayout.Compact, player = testPlayer.copy(remoteOffer = offer))

        assertShown("Continue from Feishin")
        compose.onNodeWithText(TEST_TRACK).assertExists()
        compose.onNodeWithText("Open a sample album").performClick()
        assertShown("Continue from Feishin")
    }

    @Test
    fun `with an empty queue the continue bar replaces the mini player`() {
        setShell(ShellLayout.Medium, player = ShellPlayerState(remoteOffer = offer))

        assertShown("Continue from Feishin")
        compose.onNodeWithText(TEST_TRACK).assertDoesNotExist()
    }

    @Test
    fun `the continue bar shows above the expanded player bar and hides under Now Playing`() {
        setShell(ShellLayout.Expanded, player = testPlayer.copy(remoteOffer = offer))
        assertShown("Continue from Feishin")

        compose.onNodeWithText(TEST_TRACK).performClick()

        compose.onNodeWithText("Now playing").assertExists()
        compose.onAllNodes(hasText("Continue from Feishin")).assertCountEquals(0)
    }
}

/**
 * A stand-in Now Playing with a Queue and a Lyrics panel, composing only the one shown, as the
 * phone layout does. The Queue panel's one row removes "Nude" through [onRemove].
 */
@Composable
private fun PanelledNowPlaying(appState: SegueAppState, onRemove: () -> Unit) {
    if (!appState.isNowPlayingOpen) return
    var lyrics by remember { mutableStateOf(false) }
    Column {
        Text("Now playing")
        TextButton(onClick = { lyrics = true }) { Text("Lyrics panel") }
        if (lyrics) Text("Lyrics") else TextButton(onClick = onRemove) { Text("Remove Nude") }
        TextButton(onClick = appState::closeNowPlaying) { Text("Collapse") }
    }
}

/** Material's short snackbar duration, without accessibility services asking for longer. */
private const val SHORT_SNACKBAR_MILLIS = 4_000L
