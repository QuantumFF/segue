package com.quantumff.segue.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RemoteQueueOffer
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

    private fun setShell(layout: ShellLayout, player: ShellPlayerState = testPlayer) {
        compose.setContent {
            SegueTheme {
                val appState = rememberSegueAppState(shellLayout = layout)
                TestShell(appState, player)
            }
        }
    }

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
