package com.quantumff.segue.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TopBarsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the settings button is announced as Settings, not the server`() {
        compose.setContent {
            SegueTheme { AccountAction(serverName = SERVER, onClick = {}) }
        }

        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithContentDescription(SERVER).assertDoesNotExist()
    }

    @Test
    fun `long-pressing the settings button shows the server in a tooltip`() {
        compose.setContent {
            SegueTheme { AccountAction(serverName = SERVER, onClick = {}) }
        }

        compose.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText(SERVER).assertExists()
    }

    @Test
    fun `the detail bar's title is out of the tree while the hero shows`() {
        compose.setContent {
            SegueTheme { DetailTopBar(title = TITLE, collapsed = false, onBackClick = {}) }
        }

        compose.onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun `the collapsed detail bar's title is a heading`() {
        var collapsed by mutableStateOf(false)
        compose.setContent {
            SegueTheme { DetailTopBar(title = TITLE, collapsed = collapsed, onBackClick = {}) }
        }

        collapsed = true
        compose.waitForIdle()

        compose.onNodeWithText(TITLE).assert(isHeading())
    }

    private companion object {
        const val TITLE = "In Rainbows"
        const val SERVER = "demo.navidrome.org"
    }
}
