package com.quantumff.segue.core.data.snackbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnackbarsTest {
    private val snackbars = Snackbars()

    @Test
    fun `a posted message shows, for the short duration without an action`() {
        snackbars.post(SnackbarText.of(TEXT, "Road trip"))

        val shown = snackbars.current.value
        assertThat(shown?.text).isEqualTo(SnackbarText.of(TEXT, "Road trip"))
        assertThat(shown?.duration).isEqualTo(SnackbarMessage.Duration.Short)
    }

    @Test
    fun `a message with an action shows for the long duration`() {
        snackbars.post(SnackbarText.of(TEXT), SnackbarAction(SnackbarText.of(LABEL)) {})

        assertThat(snackbars.current.value?.duration).isEqualTo(SnackbarMessage.Duration.Long)
    }

    @Test
    fun `messages show one at a time in the order they were posted`() {
        snackbars.post(SnackbarText.of(TEXT, "first"))
        snackbars.post(SnackbarText.of(TEXT, "second"))

        assertThat(snackbars.shownTexts()).containsExactly("first", "second").inOrder()
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun `a message is still current until it has been dismissed`() {
        snackbars.post(SnackbarText.of(TEXT, "first"))
        snackbars.post(SnackbarText.of(TEXT, "second"))
        val first = snackbars.current.value!!

        // A host that goes away mid-show reports nothing, so the next host shows it again.
        assertThat(snackbars.current.value).isSameInstanceAs(first)

        snackbars.onDismiss(first)
        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.of(TEXT, "second"))
    }

    @Test
    fun `a poster takes back its message, shown or waiting, as if dismissed`() {
        val calls = mutableListOf<String>()
        val first = snackbars.post(
            SnackbarText.of(TEXT, "first"),
            SnackbarAction(SnackbarText.of(LABEL), onDismiss = {
                calls += "dismiss"
            }) {},
        )
        val second = snackbars.post(SnackbarText.of(TEXT, "second"))
        snackbars.post(SnackbarText.of(TEXT, "third"))

        snackbars.takeBack(second)
        snackbars.takeBack(first)

        assertThat(calls).containsExactly("dismiss")
        assertThat(snackbars.shownTexts()).containsExactly("third")
    }

    @Test
    fun `tapping the action runs it, not its dismissal, and moves on`() {
        val calls = mutableListOf<String>()
        snackbars.post(
            SnackbarText.of(TEXT),
            SnackbarAction(SnackbarText.of(LABEL), onDismiss = {
                calls += "dismiss"
            }) {
                calls +=
                    "undo"
            },
        )
        snackbars.post(SnackbarText.of(TEXT, "next"))

        snackbars.onAction(snackbars.current.value!!)

        assertThat(calls).containsExactly("undo")
        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.of(TEXT, "next"))
    }

    @Test
    fun `dismissal or timeout runs the action's dismissal, not the action`() {
        val calls = mutableListOf<String>()
        snackbars.post(
            SnackbarText.of(TEXT),
            SnackbarAction(SnackbarText.of(LABEL), onDismiss = {
                calls += "dismiss"
            }) {
                calls +=
                    "undo"
            },
        )

        snackbars.onDismiss(snackbars.current.value!!)

        assertThat(calls).containsExactly("dismiss")
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun `a message is acted on or dismissed once`() {
        val calls = mutableListOf<String>()
        snackbars.post(
            SnackbarText.of(TEXT),
            SnackbarAction(SnackbarText.of(LABEL), onDismiss = {
                calls += "dismiss"
            }) {
                calls +=
                    "undo"
            },
        )
        val message = snackbars.current.value!!

        snackbars.onAction(message)
        snackbars.onDismiss(message)

        assertThat(calls).containsExactly("undo")
    }

    @Test
    fun `a message posted by an action waits behind those already posted`() {
        snackbars.post(
            SnackbarText.of(TEXT, "first"),
            SnackbarAction(SnackbarText.of(LABEL)) {
                snackbars.post(SnackbarText.of(TEXT, "from the action"))
            },
        )
        snackbars.post(SnackbarText.of(TEXT, "second"))

        snackbars.onAction(snackbars.current.value!!)

        assertThat(snackbars.shownTexts()).containsExactly("second", "from the action").inOrder()
    }

    /** Dismisses each message in turn, as a host does once it times out, and returns their first arguments. */
    private fun Snackbars.shownTexts(): List<Any> = generateSequence { current.value }
        .take(MAX_MESSAGES)
        .map { message -> message.text.args.first().also { onDismiss(message) } }
        .toList()

    private companion object {
        const val TEXT = 1
        const val LABEL = 2
        const val MAX_MESSAGES = 10
    }
}
