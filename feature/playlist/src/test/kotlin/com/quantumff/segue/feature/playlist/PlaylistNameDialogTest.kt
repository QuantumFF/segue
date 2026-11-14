package com.quantumff.segue.feature.playlist

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistNameDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val confirmed = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            SegueTheme {
                PlaylistNameDialog(
                    title = "New playlist",
                    confirmLabel = "Create",
                    initialName = "",
                    onConfirm = { confirmed += it },
                    onDismissRequest = {},
                )
            }
        }
    }

    @Test
    fun `the name field is focused when the dialog opens`() {
        show()

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `done on the keyboard confirms the name`() {
        show()

        compose.onNode(hasSetTextAction()).performTextInput("  Road trip ")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertThat(confirmed).containsExactly("Road trip")
    }

    @Test
    fun `done does nothing while the name is blank`() {
        show()

        compose.onNode(hasSetTextAction()).performTextInput("   ")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertThat(confirmed).isEmpty()
    }
}
