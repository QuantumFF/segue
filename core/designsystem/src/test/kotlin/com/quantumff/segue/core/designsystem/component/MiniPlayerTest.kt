package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.testing.compose.hasClickLabel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiniPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    private var opens = 0

    @Test
    fun `the mini player's action is Open Now Playing`() {
        compose.setContent {
            SegueTheme {
                MiniPlayer(
                    state = PreviewData.miniPlayer,
                    progress = { 0f },
                    onClick = { opens++ },
                    onPlayPauseClick = {},
                    onNextClick = {},
                )
            }
        }

        compose.onAllNodes(hasClickLabel("Open Now Playing")).assertCountEquals(1)
        compose.onNode(hasClickLabel("Open Now Playing")).performClick()

        assertThat(opens).isEqualTo(1)
    }
}
