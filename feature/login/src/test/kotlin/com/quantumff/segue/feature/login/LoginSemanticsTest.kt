package com.quantumff.segue.feature.login

import android.view.View
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LoginSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fieldsDeclareAutofillContentTypes() {
        setLogin(LoginUiState())

        assertThat(contentHints("Server address")).contains("url")
        assertThat(contentHints("Username")).contains(View.AUTOFILL_HINT_USERNAME)
        assertThat(contentHints("Password")).contains(View.AUTOFILL_HINT_PASSWORD)
    }

    @Test
    fun errorIsAnnouncedPolitely() {
        setLogin(LoginUiState(error = LoginError.WrongCredentials))

        compose.onNode(hasText("Wrong username or password"), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun wrongCredentialsMoveFocusToPassword() {
        val state = mutableStateOf(LoginUiState(step = LoginStep.Checking))
        setLogin(state)

        state.value = LoginUiState(error = LoginError.WrongCredentials)

        compose.onNode(field("Password")).assertIsFocused()
    }

    @Test
    fun missingServerAddressMovesFocusToServerField() {
        val state = mutableStateOf(LoginUiState())
        setLogin(state)

        state.value = LoginUiState(error = LoginError.MissingUrl)

        compose.onNode(field("Server address")).assertIsFocused()
    }

    @Test
    fun formLabelsDontClipAtLargeFont() {
        setLogin(mutableStateOf(LoginUiState()), fontScale = 2f)

        listOf("Server address", "Username", "Password", "Connect").forEach { label ->
            assertWithMessage(label).that(clipped(label)).isFalse()
        }
    }

    @Test
    fun continueButtonGrowsAtLargeFont() {
        setLogin(mutableStateOf(LoginUiState(step = LoginStep.Syncing)), fontScale = 2f)

        assertWithMessage("Continue").that(clipped("Continue")).isFalse()
    }

    /** Whether [text] is cut off: taller than its space, or ellipsized. */
    private fun clipped(text: String): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and hasSetTextAction().not(), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        return layout.didOverflowHeight || layout.isLineEllipsized(layout.lineCount - 1)
    }

    private fun field(label: String) = hasText(label) and hasSetTextAction()

    /** The autofill hints a password manager reads from the field labelled [label]. */
    private fun contentHints(label: String): List<String> {
        val node = compose.onNode(field(label)).fetchSemanticsNode()
        val type = node.config.getOrNull(SemanticsProperties.ContentType) ?: return emptyList()
        // Compose keeps `ContentType.contentHints` internal; its JVM getter is public.
        val hints = Class.forName("androidx.compose.ui.autofill.ContentType_androidKt")
            .getMethod("getContentHints", ContentType::class.java)
            .invoke(null, type) as Array<*>
        return hints.map { it.toString() }
    }

    private fun setLogin(state: LoginUiState) = setLogin(mutableStateOf(state))

    private fun setLogin(state: State<LoginUiState>, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SegueTheme {
                    LoginScreen(
                        uiState = state.value,
                        serverUrl = TextFieldState(),
                        username = TextFieldState(),
                        password = TextFieldState(),
                        onConnectClick = {},
                        onContinueClick = {},
                        onBackClick = {},
                    )
                }
            }
        }
    }
}
