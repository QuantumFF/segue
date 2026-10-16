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

    /**
     * No connection, a timeout, a TLS failure, or a non-Subsonic HTTP error such as a proxy's 502.
     * [httpCode] is set for HTTP errors; a 404 usually means the address is not a Subsonic server.
     * Retrying later may work.
     */
    class Unreachable(cause: Throwable, val httpCode: Int? = null) : RemoteException(cause.message, cause) {
        /** The TLS handshake or certificate check failed, for example a self-signed certificate. */
        val isTlsFailure: Boolean
            get() = generateSequence(cause) { it.cause }.any {
                it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException
            }

        /** The host name did not resolve. */
        val isUnknownHost: Boolean
            get() = generateSequence(cause) { it.cause }.any { it is java.net.UnknownHostException }
    }

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

    is SubsonicException.Network -> RemoteException.Unreachable(this)

    is SubsonicException.Http -> RemoteException.Unreachable(this, httpCode = httpCode)

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
