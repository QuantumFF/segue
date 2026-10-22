package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BufferingRingTest {
    @get:Rule
    val compose = createComposeRule()

    private var isBuffering by mutableStateOf(false)

    private val ring = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun `the ring shows once buffering has lasted 300ms`() {
        show()

        buffering(true)
        compose.mainClock.advanceTimeBy(250)
        compose.onAllNodes(ring).assertCountEquals(0)

        compose.mainClock.advanceTimeBy(100)
        compose.onAllNodes(ring).assertCountEquals(1)
    }

    @Test
    fun `a short rebuffer never shows the ring`() {
        show()

        buffering(true)
        compose.mainClock.advanceTimeBy(200)
        buffering(false)
        compose.mainClock.advanceTimeBy(500)

        compose.onAllNodes(ring).assertCountEquals(0)
    }

    @Test
    fun `the ring goes as soon as buffering ends`() {
        show()
        buffering(true)
        compose.mainClock.advanceTimeBy(400)
        compose.onAllNodes(ring).assertCountEquals(1)

        buffering(false)
        compose.mainClock.advanceTimeByFrame()

        compose.onAllNodes(ring).assertCountEquals(0)
    }

    /** Changes the player's buffering state and lets the UI see it before the clock moves on. */
    private fun buffering(value: Boolean) {
        isBuffering = value
        compose.waitForIdle()
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SegueTheme {
                BufferingRing(isBuffering = isBuffering, diameter = 40.dp) {
                    Box(Modifier.size(48.dp))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }
}
