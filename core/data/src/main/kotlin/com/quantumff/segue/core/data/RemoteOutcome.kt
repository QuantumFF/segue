package com.quantumff.segue.core.data

/**
 * How a server call ended, for screens that turn a failure into UI state instead of catching
 * [RemoteException] themselves. Run the call with [remoteOutcome].
 *
 * Every screen shows the "Offline · showing cached library" chip (DESIGN.md) for the same outcome,
 * [showsOfflineChip]. Other failures keep the cached rows without the chip, or show the screen's
 * error state when nothing is cached.
 */
sealed interface RemoteOutcome<out T> {
    /** The call failed, for whatever reason. */
    val isFailure: Boolean get() = this !is Done

    /**
     * The server could not be reached, so the screen is showing what it has cached. A server that
     * answered with an error (signed out, refused, broken) is not "offline".
     */
    val showsOfflineChip: Boolean get() = this is Unreachable

    data class Done<out T>(val value: T) : RemoteOutcome<T>

    /** The server does not have the item ([RemoteException.NotFound]). */
    data class NotFound(val cause: RemoteException.NotFound) : RemoteOutcome<Nothing>

    /** The call failed but the item may still exist; cached data stays valid. */
    sealed interface Failed : RemoteOutcome<Nothing> {
        val cause: RemoteException
    }

    /** The server could not be reached ([RemoteException.Unreachable]). Retrying later may work. */
    data class Unreachable(override val cause: RemoteException.Unreachable) : Failed

    /** The session or the server refused the call: not signed in, credentials rejected, or a server error. */
    data class Refused(override val cause: RemoteException) : Failed
}

/** Runs a server call, turning the [RemoteException] it throws into a [RemoteOutcome]. Other exceptions propagate. */
inline fun <T> remoteOutcome(block: () -> T): RemoteOutcome<T> = try {
    RemoteOutcome.Done(block())
} catch (e: RemoteException.NotFound) {
    RemoteOutcome.NotFound(e)
} catch (e: RemoteException.Unreachable) {
    RemoteOutcome.Unreachable(e)
} catch (e: RemoteException) {
    RemoteOutcome.Refused(e)
}

/** The call's value, or null when it failed. */
fun <T> RemoteOutcome<T>.getOrNull(): T? = (this as? RemoteOutcome.Done)?.value

/** Why the call failed, or null when it succeeded. */
fun RemoteOutcome<*>.exceptionOrNull(): RemoteException? = when (this) {
    is RemoteOutcome.Done -> null
    is RemoteOutcome.NotFound -> cause
    is RemoteOutcome.Failed -> cause
}
