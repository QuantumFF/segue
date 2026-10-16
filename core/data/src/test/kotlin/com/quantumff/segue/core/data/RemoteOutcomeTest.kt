package com.quantumff.segue.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteOutcomeTest {
    private val cause = IOException("boom")

    @Test
    fun `a value is done`() {
        val outcome = remoteOutcome { 42 }

        assertThat(outcome).isEqualTo(RemoteOutcome.Done(42))
        assertThat(outcome.getOrNull()).isEqualTo(42)
        assertThat(outcome.exceptionOrNull()).isNull()
        assertThat(outcome.isFailure).isFalse()
    }

    @Test
    fun `not found is its own outcome, apart from the other failures`() {
        val error = RemoteException.NotFound(cause)
        val outcome = remoteOutcome<Int> { throw error }

        assertThat(outcome).isEqualTo(RemoteOutcome.NotFound(error))
        assertThat(outcome).isNotInstanceOf(RemoteOutcome.Failed::class.java)
        assertThat(outcome.isFailure).isTrue()
        assertThat(outcome.getOrNull()).isNull()
        assertThat(outcome.exceptionOrNull()).isSameInstanceAs(error)
    }

    @Test
    fun `an unreachable server is a failed unreachable outcome`() {
        val error = RemoteException.Unreachable(cause, httpCode = 502)
        val outcome = remoteOutcome<Int> { throw error }

        assertThat(outcome).isEqualTo(RemoteOutcome.Unreachable(error))
        assertThat(outcome).isInstanceOf(RemoteOutcome.Failed::class.java)
        assertThat(outcome.exceptionOrNull()).isSameInstanceAs(error)
    }

    @Test
    fun `every other remote failure is refused`() {
        val errors = listOf(
            RemoteException.NotSignedIn(),
            RemoteException.AuthFailed(cause),
            RemoteException.ServerError("nope", cause),
        )

        for (error in errors) {
            val outcome = remoteOutcome<Int> { throw error }
            assertThat(outcome).isEqualTo(RemoteOutcome.Refused(error))
            assertThat(outcome).isInstanceOf(RemoteOutcome.Failed::class.java)
        }
    }

    @Test
    fun `other exceptions and cancellation propagate`() {
        assertThrows(IllegalStateException::class.java) { remoteOutcome<Int> { throw IllegalStateException() } }
        assertThrows(CancellationException::class.java) { remoteOutcome<Int> { throw CancellationException() } }
    }
}
