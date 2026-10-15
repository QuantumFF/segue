package com.quantumff.segue.core.playback.browse

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LaunchFutureTest {
    private fun TestScope.scope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test
    fun `completes with the result`() = runTest {
        val future = scope().launchFuture { 42 }
        advanceUntilIdle()
        assertThat(future.get()).isEqualTo(42)
    }

    @Test
    fun `a failure completes the future and leaves the scope running`() = runTest {
        val scope = scope()
        val future = scope.launchFuture<Int> { error("boom") }
        advanceUntilIdle()

        val error = runCatching { future.get() }.exceptionOrNull()
        assertThat(error).isInstanceOf(ExecutionException::class.java)
        assertThat(error!!.cause).hasMessageThat().isEqualTo("boom")
        val next = scope.launchFuture { 1 }
        advanceUntilIdle()
        assertThat(next.get()).isEqualTo(1)
    }

    @Test
    fun `a cancelled scope still completes the future`() = runTest {
        val scope = scope()
        val running = scope.launchFuture<Int> { awaitCancellation() }
        advanceUntilIdle()
        scope.cancel()
        val neverStarted = scope.launchFuture { 1 }
        advanceUntilIdle()

        assertThat(running.isDone).isTrue()
        assertThat(neverStarted.isDone).isTrue()
    }
}
