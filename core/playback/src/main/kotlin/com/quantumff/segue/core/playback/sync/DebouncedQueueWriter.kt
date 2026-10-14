package com.quantumff.segue.core.playback.sync

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Writes the queue state at most once per burst of events: each [submit] restarts a
 * [debounceMillis] quiet period, and only the latest state is written. [flush] writes at once.
 * A state equal to the last one written is skipped, and a failed write (false or an exception)
 * leaves the state dirty so the next event retries it. Nothing runs between events, so there is
 * no periodic write.
 *
 * Call [submit], [flush], and [markWritten] from one thread (the player's).
 */
class DebouncedQueueWriter(
    private val scope: CoroutineScope,
    private val debounceMillis: Long,
    private val write: suspend (QueueState) -> Boolean,
) {
    private val mutex = Mutex()
    private var pending: Job? = null
    private var latest: QueueState? = null

    @Volatile
    private var lastWritten: QueueState? = null

    fun submit(state: QueueState) {
        latest = state
        pending?.cancel()
        pending = scope.launch {
            delay(debounceMillis)
            writeIfChanged(state)
        }
    }

    /** Writes [state], or the latest submitted state when null, now. */
    fun flush(state: QueueState?) {
        val target = state ?: latest ?: return
        latest = target
        pending?.cancel()
        pending = null
        scope.launch { writeIfChanged(target) }
    }

    /** Records [state] as already written, for a queue restored from or adopted into storage. */
    fun markWritten(state: QueueState) {
        lastWritten = state
    }

    // Once the quiet period is over, a newer event must not cancel a write that is in flight.
    private suspend fun writeIfChanged(state: QueueState) = withContext(NonCancellable) {
        mutex.withLock {
            if (state == lastWritten) return@withLock
            val written = try {
                write(state)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                false
            }
            if (written) lastWritten = state
        }
    }
}
