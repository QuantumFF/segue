package com.quantumff.segue.feature.login

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val servers = FakeServerRepository()

    private fun viewModel(canGoBack: Boolean = false) =
        LoginViewModel(SavedStateHandle(mapOf(LoginViewModel.KEY_CAN_GO_BACK to canGoBack)), servers)

    @Test
    fun blankAddressIsRejectedWithoutCallingTheServer() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.connect(" ", "demo", "demo")
            assertThat(awaitItem().error).isEqualTo(LoginError.MissingUrl)
        }
        assertThat(servers.signIns).isEmpty()
    }

    @Test
    fun connectingAgainWithTheAddressStillBlankIsANewFailure() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.connect(" ", "demo", "demo")
            val first = awaitItem()
            viewModel.connect(" ", "demo", "demo")
            val second = awaitItem()
            assertThat(second.error).isEqualTo(LoginError.MissingUrl)
            assertThat(second).isNotEqualTo(first)
        }
    }

    @Test
    fun blankUsernameIsRejected() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.connect("demo.navidrome.org", "", "demo")
            val state = awaitItem()
            assertThat(state.error).isEqualTo(LoginError.MissingUsername)
            assertThat(state.error?.field).isEqualTo(LoginField.Username)
        }
    }

    @Test
    fun successLeavesForHomeWithoutASyncStep() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().step).isEqualTo(LoginStep.Form)
            viewModel.connect("https://demo.navidrome.org", "demo", "demo")

            val steps = mutableListOf<LoginStep>()
            do steps += awaitItem().step while (steps.last() != LoginStep.Finished)
            // Checking, then straight out: the first sync shows on Home instead.
            assertThat(steps).containsExactly(LoginStep.Checking, LoginStep.Finished).inOrder()
        }
        assertThat(servers.signIns.single()).isEqualTo(Triple("https://demo.navidrome.org", "demo", "demo"))
    }

    @Test
    fun wrongPasswordShowsUnderThePasswordField() = assertErrorFor(
        RemoteException.AuthFailed(IOException("Wrong username or password")),
        LoginError.WrongCredentials,
    ) { assertThat(it.error?.field).isEqualTo(LoginField.Password) }

    @Test
    fun unknownHostReadsAsServerNotFound() = assertErrorFor(
        RemoteException.Unreachable(IOException(UnknownHostException("nope.example"))),
        LoginError.ServerNotFound,
    )

    @Test
    fun certificateFailureReadsAsTls() = assertErrorFor(
        RemoteException.Unreachable(IOException(SSLHandshakeException("PKIX path building failed"))),
        LoginError.Tls,
    )

    @Test
    fun http404ReadsAsNotASubsonicServer() = assertErrorFor(
        RemoteException.Unreachable(IOException("HTTP 404"), httpCode = 404),
        LoginError.NotSubsonic,
    ) { assertThat(it.errorDetail).isEqualTo("HTTP 404") }

    @Test
    fun timeoutReadsAsUnreachable() = assertErrorFor(
        RemoteException.Unreachable(IOException("timeout")),
        LoginError.Unreachable,
    )

    @Test
    fun malformedAddressReadsAsInvalidUrl() = assertErrorFor(
        IllegalArgumentException("Not a valid server address"),
        LoginError.InvalidUrl,
    )

    @Test
    fun editingAFieldClearsTheError() = runTest {
        servers.signInError = RemoteException.Unreachable(IOException("timeout"))
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.connect("demo.navidrome.org", "demo", "demo")
            var state = awaitItem()
            while (state.error == null) state = awaitItem()

            viewModel.clearError()
            assertThat(awaitItem().error).isNull()
        }
    }

    @Test
    fun canGoBackComesFromTheRoute() = runTest {
        viewModel(canGoBack = true).uiState.test {
            assertThat(awaitItem().canGoBack).isTrue()
        }
    }

    private fun assertErrorFor(error: Throwable, expected: LoginError, extra: (LoginUiState) -> Unit = {}) = runTest {
        servers.signInError = error
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(1)
            viewModel.connect("demo.navidrome.org", "demo", "demo")
            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertThat(state.error).isEqualTo(expected)
            assertThat(state.step).isEqualTo(LoginStep.Form)
            extra(state)
        }
    }
}
