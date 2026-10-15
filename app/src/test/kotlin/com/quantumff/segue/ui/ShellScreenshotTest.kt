package com.quantumff.segue.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shell goldens per layout. Record with `./gradlew :app:recordRoborazziDebug`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ShellScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, layout: ShellLayout, dark: Boolean = true) {
        compose.setContent {
            SegueTheme(systemDark = dark) {
                val appState = rememberSegueAppState(shellLayout = layout)
                SegueApp(appState = appState) { TestNavHost(appState, it) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun compact() = capture("shell_compact", ShellLayout.Compact)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun compactLight() = capture("shell_compact_light", ShellLayout.Compact, dark = false)

    @Test
    @Config(qualifiers = "w700dp-h480dp-mdpi")
    fun medium() = capture("shell_medium", ShellLayout.Medium)

    @Test
    @Config(qualifiers = "w1000dp-h640dp-mdpi")
    fun expanded() = capture("shell_expanded", ShellLayout.Expanded)
}
