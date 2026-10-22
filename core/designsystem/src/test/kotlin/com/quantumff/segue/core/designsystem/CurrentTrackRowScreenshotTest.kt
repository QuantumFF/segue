package com.quantumff.segue.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowLeading
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the current song's track row (DESIGN.md §5.2), playing then paused, with a number and
 * with art. Like [ComponentScreenshotTest], but on a test clock: a playing row's equalizer animates
 * forever, so the clock stops partway through its cycle for the capture.
 * Record with `./gradlew :core:designsystem:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class CurrentTrackRowScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun currentTrackRowsDark() = capture(dark = true)

    @Test
    fun currentTrackRowsLight() = capture(dark = false)

    private fun capture(dark: Boolean) {
        val current = PreviewData.tracks.first { it.isCurrent }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SegueTheme(systemDark = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        listOf(TrackRowLeading.Number, TrackRowLeading.Art).forEach { leading ->
                            listOf(true, false).forEach { playing ->
                                TrackRow(
                                    state = current.copy(isPlaying = playing),
                                    onClick = {},
                                    leading = leading,
                                    onMoreClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(EQUALIZER_CAPTURE_MS)
        val theme = if (dark) "dark" else "light"
        compose.onRoot().captureRoboImage("src/test/screenshots/current_track_rows_$theme.png")
    }

    private companion object {
        const val EQUALIZER_CAPTURE_MS = 200L
    }
}
