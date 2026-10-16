package com.quantumff.segue.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceholderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `renders its title inside the theme`() {
        compose.setContent {
            SegueTheme { PlaceholderScreen(title = "Home") }
        }
        compose.onNodeWithText("Home").assertExists()
    }
}
