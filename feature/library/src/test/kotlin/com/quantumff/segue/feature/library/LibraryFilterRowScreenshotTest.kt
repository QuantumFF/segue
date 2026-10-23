package com.quantumff.segue.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Albums filter row on a 360dp phone, dark and light: at rest, fading before the count, and
 * scrolled to its last chip, clear of the count. Guards against chips running under the count.
 * Record with `./gradlew :feature:library:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LibraryFilterRowScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private var dark by mutableStateOf(true)

    @Test
    fun albumsFilterRow() {
        show()
        capture("library_filter_row_albums")
    }

    @Test
    fun albumsFilterRowScrolledToTheEnd() {
        show()
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
        capture("library_filter_row_albums_end")
    }

    private fun capture(name: String) {
        listOf(true, false).forEach {
            dark = it
            val theme = if (it) "dark" else "light"
            compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$theme.png")
        }
    }

    private fun show() {
        compose.setContent {
            SegueTheme(systemDark = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FilterRow(state = LibraryUiState(albumCount = 1234), onEvent = {}, onPickerClick = {})
                }
            }
        }
    }
}
