package com.quantumff.segue.core.playback.browse

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [block] in this scope and completes the returned future with its result. The future always
 * completes: with the exception if [block] fails, or cancelled if the scope is cancelled first, so
 * a Media3 caller is never left waiting.
 */
internal fun <T> CoroutineScope.launchFuture(block: suspend () -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val job = launch {
        try {
            result.set(block())
        } catch (e: CancellationException) {
            result.setException(e)
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Reported through the future, not the scope (which would crash the process).
            result.setException(e)
        }
    }
    // Cancelled before it started (the scope was already cancelled): nothing else completes it.
    job.invokeOnCompletion { cause -> if (cause != null) result.setException(cause) }
    return result
}
