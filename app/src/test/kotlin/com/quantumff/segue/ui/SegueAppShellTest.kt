package com.quantumff.segue.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quantumff.segue.core.designsystem.theme.SegueTheme
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

    private fun setShell(layout: ShellLayout) {
        compose.setContent {
            SegueTheme { SegueApp(appState = rememberSegueAppState(shellLayout = layout)) }
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

        compose.onNodeWithText("Nothing playing").assertExists()
        tab("Search").assertExists()
    }

    @Test
    fun `expanded layout shows the sidebar and the player bar`() {
        setShell(ShellLayout.Expanded)

        compose.onNodeWithText("PLAYLISTS").assertExists()
        compose.onNodeWithText("Nothing playing").assertExists()
        tab("Library").performClick()
        compose.onNodeWithText("Open a sample artist").assertExists()
    }

    @Test
    fun `now playing hides the navigation chrome`() {
        setShell(ShellLayout.Compact)

        compose.onNodeWithText("Nothing playing").performClick()

        compose.onNodeWithText("PLAYING FROM ALBUM").assertExists()
        tab("Search").assertDoesNotExist()
        compose.onNodeWithText("Pick an album to start").assertDoesNotExist()
    }
}
