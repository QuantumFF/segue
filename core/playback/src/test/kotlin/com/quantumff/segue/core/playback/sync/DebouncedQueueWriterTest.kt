package com.quantumff.segue.core.playback.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DebouncedQueueWriterTest {
    private val written = mutableListOf<Pair<Long, QueueState>>()
    private var succeed = true

    private fun TestScope.writer() = DebouncedQueueWriter(backgroundScope, debounceMillis = 2_000) { state ->
        if (succeed) written += testScheduler.currentTime to state
        succeed
    }

    private fun state(position: Long, ids: List<String> = listOf("a", "b"), index: Int = 0) =
        QueueState(ids, index, position)

    @Test
    fun `a burst of events is coalesced into one write of the latest state`() = runTest {
        val writer = writer()
        writer.submit(state(1))
        advanceTimeBy(500)
        writer.submit(state(2))
        advanceTimeBy(1_500)
        writer.submit(state(3))
        advanceTimeBy(1_999)
        runCurrent()
        assertThat(written).isEmpty()

        advanceTimeBy(2)
        runCurrent()
        assertThat(written).containsExactly(4_000L to state(3))
    }

    @Test
    fun `events further apart than the debounce are written separately`() = runTest {
        val writer = writer()
        writer.submit(state(1))
        advanceTimeBy(3_000)
        writer.submit(state(2))
        advanceTimeBy(3_000)
        assertThat(written.map { it.second }).containsExactly(state(1), state(2)).inOrder()
    }

    @Test
    fun `writeNow writes at once, even an unchanged state, and drops the pending write`() = runTest {
        val writer = writer()
        writer.flush(state(1))
        runCurrent()
        writer.submit(state(2))
        val now = mutableListOf<QueueState>()
        writer.writeNow(state(1)) { now += it }
        assertThat(now).containsExactly(state(1))

        advanceTimeBy(10_000)
        assertThat(written.map { it.second }).containsExactly(state(1))
    }

    @Test
    fun `writeNow waits for a write in flight`() = runTest {
        val order = mutableListOf<String>()
        val writer = DebouncedQueueWriter(backgroundScope, debounceMillis = 2_000) {
            delay(1_000)
            order += "debounced"
            true
        }
        writer.flush(state(1))
        runCurrent()
        writer.writeNow(state(2)) { order += "now" }
        assertThat(order).containsExactly("debounced", "now").inOrder()
    }

    @Test
    fun `a failed writeNow throws and leaves the state to be written again`() = runTest {
        val writer = writer()
        val error = runCatching { writer.writeNow(state(1)) { error("offline") } }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalStateException::class.java)

        writer.flush(state(1))
        runCurrent()
        assertThat(written.map { it.second }).containsExactly(state(1))
    }

    @Test
    fun `flush writes at once and drops the pending debounced write`() = runTest {
        val writer = writer()
        writer.submit(state(1))
        advanceTimeBy(500)
        writer.flush(state(2))
        runCurrent()
        assertThat(written).containsExactly(500L to state(2))

        advanceTimeBy(10_000)
        assertThat(written).hasSize(1)
    }

    @Test
    fun `flush without a state writes the latest submitted one`() = runTest {
        val writer = writer()
        writer.submit(state(7))
        writer.flush(null)
        runCurrent()
        assertThat(written.map { it.second }).containsExactly(state(7))
    }

    @Test
    fun `a state equal to the last written one is not written again`() = runTest {
        val writer = writer()
        writer.submit(state(1))
        advanceTimeBy(3_000)
        writer.submit(state(1))
        advanceTimeBy(3_000)
        writer.flush(state(1))
        runCurrent()
        assertThat(written).hasSize(1)
    }

    @Test
    fun `a baseline state counts as already written`() = runTest {
        val writer = writer()
        writer.markWritten(state(5))
        writer.submit(state(5))
        advanceTimeBy(3_000)
        assertThat(written).isEmpty()

        writer.submit(state(6))
        advanceTimeBy(3_000)
        assertThat(written.map { it.second }).containsExactly(state(6))
    }

    @Test
    fun `a failed write is retried by the next event even with the same state`() = runTest {
        val writer = writer()
        succeed = false
        writer.submit(state(1))
        advanceTimeBy(3_000)
        assertThat(written).isEmpty()

        succeed = true
        writer.flush(state(1))
        runCurrent()
        assertThat(written.map { it.second }).containsExactly(state(1))
    }

    @Test
    fun `nothing is written while idle - no periodic writes`() = runTest {
        val writer = writer()
        writer.submit(state(1))
        advanceTimeBy(60 * 60 * 1_000)
        assertThat(written).hasSize(1)
    }
}
