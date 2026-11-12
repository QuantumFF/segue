package com.quantumff.segue.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.OfflineStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The offline chip's copy, what a tap does and how TalkBack announces it, per status (DESIGN.md §5.8). */
@RunWith(RobolectricTestRunner::class)
class OfflineStatusChipTest {
    @get:Rule
    val compose = createComposeRule()

    private var status by mutableStateOf(OfflineStatus.Online)
    private val taps = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            SegueTheme {
                OfflineStatusChip(
                    status = status,
                    onRetry = { taps += "retry" },
                    onOpenOfflineModeSetting = { taps += "settings" },
                )
            }
        }
    }

    @Test
    fun `online there is no chip`() {
        show()

        compose.onNodeWithText("Offline", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without a network a tap retries`() {
        status = OfflineStatus.ShowingDownloads
        show()

        compose.onNodeWithText("Offline · showing downloads").performClick()

        assertThat(taps).containsExactly("retry")
    }

    @Test
    fun `an unreachable server shows the cached library and a tap retries`() {
        status = OfflineStatus.ShowingCachedLibrary
        show()

        compose.onNodeWithText("Offline · showing cached library").performClick()

        assertThat(taps).containsExactly("retry")
    }

    @Test
    fun `in offline mode a tap opens the offline mode setting`() {
        status = OfflineStatus.ShowingDownloadsInOfflineMode
        show()

        compose.onNodeWithText("Offline mode · showing downloads").performClick()

        assertThat(taps).containsExactly("settings")
    }

    @Test
    fun `without a network the chip is a 48dp button that retries`() {
        status = OfflineStatus.ShowingDownloads
        show()

        compose.onNodeWithText("Offline · showing downloads").assertIsButtonLabelled("Retry")
    }

    @Test
    fun `with an unreachable server the chip is a 48dp button that retries`() {
        status = OfflineStatus.ShowingCachedLibrary
        show()

        compose.onNodeWithText("Offline · showing cached library").assertIsButtonLabelled("Retry")
    }

    @Test
    fun `in offline mode the chip is a 48dp button that opens settings`() {
        status = OfflineStatus.ShowingDownloadsInOfflineMode
        show()

        compose.onNodeWithText("Offline mode · showing downloads").assertIsButtonLabelled("Open settings")
    }

    private fun SemanticsNodeInteraction.assertIsButtonLabelled(clickLabel: String) {
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assert(
            SemanticsMatcher("click label is \"$clickLabel\"") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == clickLabel
            },
        )
        assertHeightIsAtLeast(SegueSizes.touchTarget)
    }
}
