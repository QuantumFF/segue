package com.quantumff.segue.feature.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `./gradlew :feature:login:recordRoborazziDebug`, check with `verifyRoborazziDebug`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LoginScreenshotTest {
    @Test
    fun loginError() = capture("login_wrong_password") {
        Login(LoginUiState(error = LoginError.WrongCredentials), url = "demo.navidrome.org", user = "demo")
    }

    @Composable
    private fun Login(state: LoginUiState, url: String = "", user: String = "") {
        LoginScreen(
            uiState = state,
            serverUrl = TextFieldState(url),
            username = TextFieldState(user),
            password = TextFieldState("secret"),
            onConnectClick = {},
            onBackClick = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$theme.png") {
                SegueTheme(systemDark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }
}
