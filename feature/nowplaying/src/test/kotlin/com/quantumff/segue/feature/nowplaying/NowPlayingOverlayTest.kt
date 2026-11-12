package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each opening of Now Playing starts as asked, even one that interrupts its slide down. */
@RunWith(RobolectricTestRunner::class)
class NowPlayingOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `reopening while it slides down opens on the panel asked for`() {
        var visible by mutableStateOf(true)
        var opening by mutableIntStateOf(1)
        var start by mutableStateOf(NowPlayingStart(panel = NowPlayingPanel.Lyrics))
        compose.setContent {
            SegueTheme {
                NowPlayingOverlay(visible = visible, opening = opening) {
                    // Like Now Playing: the start is read once, when the screen is first shown.
                    val panel = rememberSaveable { start.panel?.name ?: "none" }
                    Text("Showing $panel")
                }
            }
        }
        compose.onNodeWithText("Showing Lyrics").assertExists()

        compose.mainClock.autoAdvance = false
        visible = false
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Showing Lyrics").assertExists() // Still sliding down.

        start = NowPlayingStart(openMenu = true)
        opening = 2
        visible = true
        compose.mainClock.autoAdvance = true

        compose.onNodeWithText("Showing none").assertExists()
    }
}
