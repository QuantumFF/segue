package com.quantumff.segue.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.component.SectionHeader
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens for a section header's extra icon (the Explore refresh), dark and light: idle,
 * running (the inline spinner in its place) and disabled (Offline). Same commands as
 * [ComponentScreenshotTest]. The spinner turns forever, so the clock is held still.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SectionHeaderScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun extraIconStates() {
        var dark by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SegueTheme(systemDark = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        SectionHeader(title = "Explore", onSeeAllClick = {}, extraIcon = SegueIcons.Refresh)
                        SectionHeader(
                            title = "Explore",
                            onSeeAllClick = {},
                            extraIcon = SegueIcons.Refresh,
                            extraIconLoading = true,
                        )
                        SectionHeader(
                            title = "Explore",
                            onSeeAllClick = {},
                            extraIcon = SegueIcons.Refresh,
                            extraIconEnabled = false,
                        )
                    }
                }
            }
        }
        listOf(true, false).forEach { isDark ->
            dark = isDark
            compose.waitForIdle()
            // A fixed point in the spinner's turn, where its arc shows.
            compose.mainClock.advanceTimeBy(SPINNER_FRAME_MILLIS)
            val theme = if (isDark) "dark" else "light"
            compose.onRoot().captureRoboImage("src/test/screenshots/section_header_actions_$theme.png")
        }
    }

    private companion object {
        const val SPINNER_FRAME_MILLIS = 400L
    }
}
