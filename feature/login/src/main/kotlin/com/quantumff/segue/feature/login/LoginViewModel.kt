package com.quantumff.segue.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.model.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the login flow is. */
enum class LoginStep {
    /** Entering the server address and credentials. */
    Form,

    /** `ping` and extension detection are running. */
    Checking,

    /** Signed in; the first library sync is running. */
    Syncing,

    /** Done: leave the login flow. */
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
    val syncProgress: SyncProgress = SyncProgress.Pending,
    /** Opened from Settings to add another server, so there is somewhere to go back to. */
    val canGoBack: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val syncRepository: LibrarySyncRepository,
) : ViewModel() {
    private val canGoBack: Boolean = savedStateHandle.get<Boolean>(KEY_CAN_GO_BACK) ?: false
    private val form = MutableStateFlow(LoginUiState(canGoBack = canGoBack))

    private val syncProgress = form.flatMapLatest { state ->
        if (state.step == LoginStep.Syncing) syncRepository.progress else flowOf(SyncProgress.Pending)
    }

    val uiState: StateFlow<LoginUiState> = combine(form, syncProgress) { state, progress ->
        when {
            state.step != LoginStep.Syncing -> state

            // A finished first sync leaves the progress screen by itself.
            progress == SyncProgress.Succeeded -> state.copy(step = LoginStep.Finished, syncProgress = progress)

            // WorkManager reports nothing until the job is registered: show it as pending.
            progress == SyncProgress.Idle -> state.copy(syncProgress = SyncProgress.Pending)

            else -> state.copy(syncProgress = progress)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), form.value)

    fun connect(serverUrl: String, username: String, password: String) {
        if (form.value.step != LoginStep.Form) return
        val error = when {
            serverUrl.isBlank() -> LoginError.MissingUrl
            username.isBlank() -> LoginError.MissingUsername
            else -> null
        }
        if (error != null) {
            form.update { it.copy(error = error, errorDetail = null) }
            return
        }
        form.update { it.copy(step = LoginStep.Checking, error = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                serverRepository.signIn(serverUrl, username, password)
                form.update { it.copy(step = LoginStep.Syncing) }
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

    /** Leave the sync progress screen and browse while the sync continues in the background. */
    fun continueToLibrary() {
        form.update { it.copy(step = LoginStep.Finished) }
    }

    private fun fail(error: LoginError, detail: String?) {
        form.update { it.copy(step = LoginStep.Form, error = error, errorDetail = detail) }
    }

    companion object {
        /** Matches the `canGoBack` property of the login route. */
        const val KEY_CAN_GO_BACK = "canGoBack"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
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
