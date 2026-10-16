package com.quantumff.segue.core.network

import com.quantumff.segue.core.network.model.NetworkError
import java.io.IOException

/** Every failure a [SubsonicClient] call can throw, besides cancellation. */
sealed class SubsonicException(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    /** The server answered with `status="failed"`. See the Subsonic error code table. */
    sealed class Api(val code: Int, val serverMessage: String?) :
        SubsonicException("Subsonic error $code: $serverMessage") {
        /** Credentials are wrong or not accepted. Ask the user to sign in again; retrying will not help. */
        open val isAuthError: Boolean get() = false
    }

    class Generic(message: String?) : Api(0, message)

    class MissingParameter(message: String?) : Api(10, message)

    class ClientTooOld(message: String?) : Api(20, message)

    class ServerTooOld(message: String?) : Api(30, message)

    class WrongCredentials(message: String?) : Api(40, message) {
        override val isAuthError get() = true
    }

    /** Token auth is unavailable, for example for LDAP users. */
    class TokenAuthNotSupported(message: String?) : Api(41, message) {
        override val isAuthError get() = true
    }

    class AuthMechanismNotSupported(message: String?) : Api(42, message) {
        override val isAuthError get() = true
    }

    class ConflictingAuthMechanisms(message: String?) : Api(43, message) {
        override val isAuthError get() = true
    }

    class InvalidApiKey(message: String?) : Api(44, message) {
        override val isAuthError get() = true
    }

    class NotAuthorized(message: String?) : Api(50, message)

    class TrialExpired(message: String?) : Api(60, message)

    class NotFound(message: String?) : Api(70, message)

    class Unknown(code: Int, message: String?) : Api(code, message)

    /** A non-2xx response without a Subsonic error body, for example from a reverse proxy. */
    class Http(val httpCode: Int) : SubsonicException("HTTP $httpCode")

    class Network(cause: IOException) : SubsonicException(cause.message, cause)

    /** The body is not a Subsonic JSON response, for example a captive portal page. */
    class Malformed(cause: Throwable) : SubsonicException(cause.message, cause)

    companion object {
        fun from(error: NetworkError?): Api {
            val message = error?.message
            return when (error?.code ?: 0) {
                0 -> Generic(message)
                10 -> MissingParameter(message)
                20 -> ClientTooOld(message)
                30 -> ServerTooOld(message)
                40 -> WrongCredentials(message)
                41 -> TokenAuthNotSupported(message)
                42 -> AuthMechanismNotSupported(message)
                43 -> ConflictingAuthMechanisms(message)
                44 -> InvalidApiKey(message)
                50 -> NotAuthorized(message)
                60 -> TrialExpired(message)
                70 -> NotFound(message)
                else -> Unknown(error!!.code, message)
            }
        }
    }
}
