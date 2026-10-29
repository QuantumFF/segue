package com.quantumff.segue.core.data.snackbar

/**
 * One poster's snackbar at a time, on the app's [Snackbars]: [post] takes down the slot's message
 * before, as if dismissed. For an action only the latest message can take, such as an Undo of the
 * last removal, which an earlier snackbar would otherwise offer to no effect. Other posters'
 * messages are left alone.
 *
 * Each message is [post]ed with what it is [about], so [takeDown] can pick it: the playlist a
 * removal is from, say. Call it from one thread, such as the main one.
 */
class SnackbarSlot<K>(private val snackbars: Snackbars) {
    private var showing: Showing<K>? = null

    private class Showing<K>(val about: K, val message: SnackbarMessage)

    /** Takes down the slot's message, then posts [text] with [action], about [about]. */
    fun post(text: SnackbarText, action: SnackbarAction, about: K): SnackbarMessage {
        takeDown()
        lateinit var message: SnackbarMessage
        // The host settling it empties the slot, so it does not keep a message that has gone.
        val settling = SnackbarAction(
            action.label,
            onDismiss = {
                forget(message)
                action.onDismiss()
            },
        ) {
            forget(message)
            action.onClick()
        }
        message = snackbars.post(text, settling)
        showing = Showing(about, message)
        return message
    }

    /** Takes the slot's message back, as if dismissed, if it is still up and [which] picks what it is about. */
    fun takeDown(which: (K) -> Boolean = { true }) {
        val shown = showing ?: return
        if (!which(shown.about)) return
        showing = null
        snackbars.takeBack(shown.message)
    }

    private fun forget(message: SnackbarMessage) {
        if (showing?.message === message) showing = null
    }
}

/** [SnackbarSlot.post] for a slot whose messages need not say what they are about. */
fun SnackbarSlot<Unit>.post(text: SnackbarText, action: SnackbarAction) = post(text, action, Unit)
