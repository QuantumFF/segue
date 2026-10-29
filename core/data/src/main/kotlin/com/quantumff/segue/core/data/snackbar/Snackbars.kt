package com.quantumff.segue.core.data.snackbar

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.quantumff.segue.core.data.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's snackbars (DESIGN.md §5.7): confirmations and Undo that any screen, ViewModel or service
 * posts, shown by the shell's one host above the mini player, or over Now Playing while it is open,
 * whichever screen is showing by then. Posters own no snackbar host.
 *
 * Post a confirmation with text alone; it shows for the short duration:
 * ```
 * snackbars.post(SnackbarText.of(R.string.switched_to, server.name))
 * ```
 * Post one with an action, which shows for the long duration. [SnackbarAction.onClick] runs if the
 * user taps it, [SnackbarAction.onDismiss] if the snackbar is dismissed or times out instead, so a
 * write that Undo can cancel waits for [SnackbarAction.onDismiss]:
 * ```
 * snackbars.post(
 *     SnackbarText.of(R.string.removed, song.title),
 *     SnackbarAction.undo(onDismiss = { commitRemoval() }) { restore() },
 * )
 * ```
 * The callbacks can run after the poster's screen has gone, so they should not depend on its
 * ViewModel scope.
 *
 * Messages show one at a time, in the order posted. A message stays [current] until the host
 * reports it through [onAction] or [onDismiss]: a host that goes away while showing it, as when the
 * activity is recreated, reports nothing, and the next host shows it again.
 */
@Singleton
class Snackbars @Inject constructor() {
    private val pending = ArrayDeque<SnackbarMessage>()
    private val head = MutableStateFlow<SnackbarMessage?>(null)

    /** The message to show now, or null when none is waiting. */
    val current: StateFlow<SnackbarMessage?> = head.asStateFlow()

    /** Queues [text], with [action] when there is one; safe to call from any thread. */
    fun post(text: SnackbarText, action: SnackbarAction? = null) = synchronized(pending) {
        pending.addLast(SnackbarMessage(text, action))
        head.value = pending.first()
    }

    /** For the host: the user tapped [message]'s action. */
    fun onAction(message: SnackbarMessage) {
        if (remove(message)) message.action?.onClick?.invoke()
    }

    /** For the host: [message] was dismissed or timed out without its action. */
    fun onDismiss(message: SnackbarMessage) {
        if (remove(message)) message.action?.onDismiss?.invoke()
    }

    /** Whether [message] was still waiting: each message is acted on or dismissed once. */
    private fun remove(message: SnackbarMessage): Boolean = synchronized(pending) {
        pending.remove(message).also { head.value = pending.firstOrNull() }
    }
}

/** A posted snackbar: its [text], and an optional [action] that makes it show for longer. */
class SnackbarMessage internal constructor(val text: SnackbarText, val action: SnackbarAction?) {
    /** Long with an action, so there is time to take it; short otherwise. */
    val duration: Duration get() = if (action != null) Duration.Long else Duration.Short

    enum class Duration { Short, Long }
}

/**
 * A snackbar's action button, [label], and what it does. [onClick] runs if it is tapped;
 * [onDismiss] runs if the snackbar is dismissed or times out instead.
 */
class SnackbarAction(val label: SnackbarText, val onDismiss: () -> Unit = {}, val onClick: () -> Unit) {
    companion object {
        /** "Undo": [onUndo] if tapped, [onDismiss] otherwise. */
        fun undo(onDismiss: () -> Unit = {}, onUndo: () -> Unit) =
            SnackbarAction(SnackbarText.of(R.string.core_data_undo), onDismiss, onUndo)
    }
}

/**
 * Snackbar text as a string or plurals resource and its format arguments, resolved when shown, so
 * posters need no resources and tests compare it by value.
 */
@ConsistentCopyVisibility
data class SnackbarText private constructor(val id: Int, val count: Int?, val args: List<Any>) {
    fun resolve(resources: Resources): String {
        val formatArgs = args.toTypedArray()
        return if (count == null) {
            resources.getString(id, *formatArgs)
        } else {
            resources.getQuantityString(id, count, *formatArgs)
        }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any) = SnackbarText(id, count = null, args.toList())

        /** The plural of [id] for [count]; pass [count] in [args] too when the text shows it. */
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any) = SnackbarText(id, count, args.toList())
    }
}
