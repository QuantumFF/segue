package com.quantumff.segue.feature.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The queue header's Upload and Download while a transfer runs (DESIGN.md §4.8, §5.8). */
@RunWith(RobolectricTestRunner::class)
class QueueHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(QueueUiState(trackCount = 3, isLoading = false))

    private val spinning = hasAnyDescendant(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))

    @Test
    fun `uploading spins Upload only and disables both`() {
        state = state.copy(transfer = QueueTransfer.Upload)
        show()

        upload().assert(spinning).assertIsNotEnabled()
        download().assert(!spinning).assertIsNotEnabled()
    }

    @Test
    fun `downloading spins Download only and disables both`() {
        state = state.copy(transfer = QueueTransfer.Download)
        show()

        download().assert(spinning).assertIsNotEnabled()
        upload().assert(!spinning).assertIsNotEnabled()
    }

    @Test
    fun `with no transfer neither spins and both are enabled`() {
        show()

        upload().assert(!spinning).assertIsEnabled()
        download().assert(!spinning).assertIsEnabled()
    }

    private fun upload() = compose.onNodeWithContentDescription("Upload queue")

    private fun download() = compose.onNodeWithContentDescription("Download queue")

    private fun show() = compose.setContent {
        SegueTheme {
            QueueHeader(state, QueueHeaderActions(), showJumpToCurrent = false, onJumpToCurrent = {})
        }
    }
}
