package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueTheme
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

        compose.onAllNodes(clickLabel("Open Now Playing")).assertCountEquals(1)
        compose.onNode(clickLabel("Open Now Playing")).performClick()

        assertThat(opens).isEqualTo(1)
    }
}

/** Matches a node whose click action has [label]. */
internal fun clickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}
