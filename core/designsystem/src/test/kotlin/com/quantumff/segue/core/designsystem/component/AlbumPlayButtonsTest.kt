package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Play and Shuffle albums chevrons are 48dp touch targets. */
@RunWith(RobolectricTestRunner::class)
class AlbumPlayButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each queue options chevron is at least 48dp`() {
        compose.setContent { SegueTheme { AlbumPlayButtons(onPlay = { _, _ -> }) } }

        listOf("Play albums: queue options", "Shuffle albums: queue options").forEach { chevron ->
            compose.onNodeWithContentDescription(chevron)
                .assertWidthIsAtLeast(SegueSizes.touchTarget)
                .assertHeightIsAtLeast(SegueSizes.touchTarget)
        }
    }
}
