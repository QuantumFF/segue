package com.quantumff.segue.core.data.snackbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnackbarSlotTest {
    private val snackbars = Snackbars()
    private val slot = SnackbarSlot<String>(snackbars)
    private val calls = mutableListOf<String>()

    private fun post(about: String) = slot.post(
        SnackbarText.of(TEXT, about),
        SnackbarAction(SnackbarText.of(LABEL), onDismiss = { calls += "dismiss $about" }) { calls += "undo $about" },
        about,
    )

    @Test
    fun `posting takes down the slot's message before, as if dismissed`() {
        post("first")
        post("second")

        assertThat(calls).containsExactly("dismiss first")
        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.of(TEXT, "second"))
    }

    @Test
    fun `other posters' messages are left alone`() {
        snackbars.post(SnackbarText.of(TEXT, "other"))
        post("first")
        post("second")

        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.of(TEXT, "other"))
    }

    @Test
    fun `take down picks the message by what it is about`() {
        post("first")

        slot.takeDown { it == "elsewhere" }
        assertThat(calls).isEmpty()

        slot.takeDown { it == "first" }
        assertThat(calls).containsExactly("dismiss first")
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun `a message the user already acted on is not taken down again`() {
        val first = post("first")
        snackbars.onAction(first)

        slot.takeDown()
        post("second")

        assertThat(calls).containsExactly("undo first")
    }

    private companion object {
        const val TEXT = 1
        const val LABEL = 2
    }
}
