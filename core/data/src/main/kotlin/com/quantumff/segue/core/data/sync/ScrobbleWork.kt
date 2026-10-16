package com.quantumff.segue.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.ScrobbleFlusher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Sends pending scrobbles when there is a network. */
internal fun interface ScrobbleScheduler {
    fun requestFlush(serverId: String)
}

/**
 * One unique job per server, run only with a network. A request while one is pending or running is
 * appended, so a scrobble stored while a flush is sending its last batch still goes out; an
 * appended run with nothing pending finishes at once. Nothing is periodic.
 */
internal class WorkManagerScrobbleScheduler @Inject constructor(private val workManager: WorkManager) :
    ScrobbleScheduler {
    override fun requestFlush(serverId: String) {
        val request = OneTimeWorkRequestBuilder<ScrobbleFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(ScrobbleFlushWorker.KEY_SERVER_ID to serverId))
            .build()
        workManager.enqueueUniqueWork("scrobble-flush-$serverId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 60L
    }
}

@HiltWorker
internal class ScrobbleFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flusher: ScrobbleFlusher,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        return try {
            flusher.flush(serverId)
            Result.success()
        } catch (_: RemoteException.Unreachable) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } catch (_: RemoteException) {
            // Signed out or rejected: the rows stay and go out with the next play's flush.
            Result.failure()
        }
    }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        private const val MAX_ATTEMPTS = 8
    }
}
