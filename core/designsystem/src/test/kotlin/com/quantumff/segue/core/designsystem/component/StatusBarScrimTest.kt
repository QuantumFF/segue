package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The status-bar scrim of a top-level screen shows only once content scrolls under it (DESIGN.md §3). */
@RunWith(RobolectricTestRunner::class)
class StatusBarScrimTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var listState: LazyListState

    private fun show() {
        compose.setContent {
            SegueTheme {
                listState = rememberLazyListState()
                Box {
                    LazyColumn(state = listState) {
                        items(50) { Text("Row $it", Modifier.height(64.dp)) }
                    }
                    StatusBarScrim(listState, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    private fun scrollTo(index: Int) {
        compose.runOnIdle { runBlocking { listState.scrollToItem(index) } }
        compose.waitForIdle()
    }

    @Test
    fun `at the top of the list there is no scrim`() {
        show()

        compose.onNodeWithTag(STATUS_BAR_SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun `once content scrolls under the status bar the scrim shows`() {
        show()

        scrollTo(10)

        compose.onNodeWithTag(STATUS_BAR_SCRIM_TAG).assertExists()
    }

    @Test
    fun `scrolling back to the top hides the scrim`() {
        show()
        scrollTo(10)

        scrollTo(0)

        compose.onNodeWithTag(STATUS_BAR_SCRIM_TAG).assertDoesNotExist()
    }
}
