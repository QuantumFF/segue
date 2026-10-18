package com.quantumff.segue.core.playback.browse

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ExecutionException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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

/** Suspends until this future completes; a failure throws its [ExecutionException]. Cancelling cancels it. */
internal suspend fun <T> ListenableFuture<T>.await(): T {
    if (isDone) return get()
    return suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: ExecutionException) {
                    continuation.resumeWithException(e)
                }
            },
            MoreExecutors.directExecutor(),
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
}
