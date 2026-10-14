package com.quantumff.segue.core.data

import com.quantumff.segue.core.network.SubsonicException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Why a call that needs the server failed. Repositories throw these instead of network types, so
 * features can show a message without depending on core:network.
 */
sealed class RemoteException(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    /** No active account, or its password can no longer be read. Sign in again. */
    class NotSignedIn : RemoteException("Not signed in")

    /** The server rejected the credentials. Sign in again. */
    class AuthFailed(cause: Throwable) : RemoteException(cause.message, cause)

    /** No connection, a timeout, or a proxy error. Retrying later may work. */
    class Unreachable(cause: Throwable) : RemoteException(cause.message, cause)

    class NotFound(cause: Throwable) : RemoteException(cause.message, cause)

    /** The server refused or failed the request. [serverMessage] is its explanation, if any. */
    class ServerError(val serverMessage: String?, cause: Throwable) : RemoteException(serverMessage, cause)
}

internal fun SubsonicException.toRemoteException(): RemoteException = when (this) {
    is SubsonicException.Api -> when {
        isAuthError -> RemoteException.AuthFailed(this)
        this is SubsonicException.NotFound -> RemoteException.NotFound(this)
        else -> RemoteException.ServerError(serverMessage, this)
    }

    is SubsonicException.Network, is SubsonicException.Http -> RemoteException.Unreachable(this)

    is SubsonicException.Malformed -> RemoteException.ServerError(message, this)
}

/** Runs a server call, translating [SubsonicException] into [RemoteException]. */
internal inline fun <T> remote(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: SubsonicException) {
    throw e.toRemoteException()
}
