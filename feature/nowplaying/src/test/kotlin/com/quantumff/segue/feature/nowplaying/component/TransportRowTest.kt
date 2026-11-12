package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RepeatMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransportRowTest {
    @get:Rule
    val compose = createComposeRule()

    private var repeatMode by mutableStateOf(RepeatMode.Off)

    @Test
    fun `repeat is named Repeat and announces its mode`() {
        compose.setContent {
            SegueTheme {
                TransportRow(
                    isPlaying = false,
                    isBuffering = false,
                    repeatMode = repeatMode,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {},
                    onRepeatClick = {},
                )
            }
        }

        listOf(RepeatMode.Off to "Off", RepeatMode.All to "All", RepeatMode.One to "One").forEach { (mode, state) ->
            repeatMode = mode
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Repeat"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
        }
    }
}
