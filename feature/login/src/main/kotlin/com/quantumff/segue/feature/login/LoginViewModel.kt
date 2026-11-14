package com.quantumff.segue.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the login flow is. */
enum class LoginStep {
    /** Entering the server address and credentials. */
    Form,

    /** `ping` and extension detection are running. */
    Checking,

    /** Signed in: leave the login flow. The first library sync shows its progress on Home. */
    Finished,
}

/** Why connecting failed, and which field the message belongs under (DESIGN.md §4.11). */
enum class LoginError(val field: LoginField) {
    MissingUrl(LoginField.Url),
    MissingUsername(LoginField.Username),
    InvalidUrl(LoginField.Url),
    ServerNotFound(LoginField.Url),
    Unreachable(LoginField.Url),
    Tls(LoginField.Url),
    NotSubsonic(LoginField.Url),
    ServerError(LoginField.Url),
    WrongCredentials(LoginField.Password),
}

enum class LoginField {
    Url,
    Username,
    Password,
}

data class LoginUiState(
    val step: LoginStep = LoginStep.Form,
    val error: LoginError? = null,
    /** The HTTP status for [LoginError.NotSubsonic] and [LoginError.ServerError], or the server's message. */
    val errorDetail: String? = null,
    /** Opened from Settings to add another server, so there is somewhere to go back to. */
    val canGoBack: Boolean = false,
    /** Counts failed attempts, so a failure repeating the last [error] is still a new one to show. */
    val failedAttempts: Int = 0,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
) : ViewModel() {
    private val canGoBack: Boolean = savedStateHandle.get<Boolean>(KEY_CAN_GO_BACK) ?: false
    private val form = MutableStateFlow(LoginUiState(canGoBack = canGoBack))

    val uiState: StateFlow<LoginUiState> = form.asStateFlow()

    fun connect(serverUrl: String, username: String, password: String) {
        if (form.value.step != LoginStep.Form) return
        val error = when {
            serverUrl.isBlank() -> LoginError.MissingUrl
            username.isBlank() -> LoginError.MissingUsername
            else -> null
        }
        if (error != null) {
            form.update { it.copy(error = error, errorDetail = null, failedAttempts = it.failedAttempts + 1) }
            return
        }
        form.update { it.copy(step = LoginStep.Checking, error = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                serverRepository.signIn(serverUrl, username, password)
                form.update { it.copy(step = LoginStep.Finished) }
            } catch (e: IllegalArgumentException) {
                fail(LoginError.InvalidUrl, detail = null)
            } catch (e: RemoteException) {
                fail(e.toLoginError(), detail = e.detail())
            }
        }
    }

    /** The user edited a field: the old message no longer applies. */
    fun clearError() {
        if (form.value.error != null) form.update { it.copy(error = null, errorDetail = null) }
    }

    private fun fail(error: LoginError, detail: String?) {
        form.update {
            it.copy(step = LoginStep.Form, error = error, errorDetail = detail, failedAttempts = it.failedAttempts + 1)
        }
    }

    companion object {
        /** Matches the `canGoBack` property of the login route. */
        const val KEY_CAN_GO_BACK = "canGoBack"
    }
}

private const val HTTP_NOT_FOUND = 404

internal fun RemoteException.toLoginError(): LoginError = when (this) {
    is RemoteException.AuthFailed -> LoginError.WrongCredentials

    is RemoteException.Unreachable -> when {
        isTlsFailure -> LoginError.Tls
        isUnknownHost -> LoginError.ServerNotFound
        httpCode == HTTP_NOT_FOUND -> LoginError.NotSubsonic
        httpCode != null -> LoginError.ServerError
        else -> LoginError.Unreachable
    }

    // A 200 answer that is not a Subsonic response, such as a web page at the address.
    is RemoteException.ServerError -> if (serverMessage == null) LoginError.NotSubsonic else LoginError.ServerError

    is RemoteException.NotFound -> LoginError.NotSubsonic

    is RemoteException.NotSignedIn -> LoginError.WrongCredentials
}

private fun RemoteException.detail(): String? = when (this) {
    is RemoteException.Unreachable -> httpCode?.let { "HTTP $it" }
    is RemoteException.ServerError -> serverMessage
    else -> null
}
