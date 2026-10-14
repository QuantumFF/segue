package com.quantumff.segue.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.toRemoteException
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.network.SubsonicException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Starts and observes library syncs for a server. */
internal interface SyncScheduler {
    fun requestSync(serverId: String, fullRefresh: Boolean)

    fun cancel(serverId: String)

    fun progress(serverId: String): Flow<SyncProgress>
}

/**
 * One unique WorkManager job per server, run only with a network connection and enough battery.
 * A request while a sync is pending or running is dropped (`KEEP`); nothing is scheduled
 * periodically, so syncs happen only on sign-in, app start, and explicit refresh.
 */
internal class WorkManagerSyncScheduler @Inject constructor(private val workManager: WorkManager) : SyncScheduler {
    override fun requestSync(serverId: String, fullRefresh: Boolean) {
        val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    LibrarySyncWorker.KEY_SERVER_ID to serverId,
                    LibrarySyncWorker.KEY_FULL to fullRefresh,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(workName(serverId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(serverId: String) {
        workManager.cancelUniqueWork(workName(serverId))
    }

    override fun progress(serverId: String): Flow<SyncProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(serverId)).map { it.lastOrNull().toSyncProgress() }

    private fun workName(serverId: String) = "library-sync-$serverId"

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}

@HiltWorker
internal class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: LibrarySynchronizer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        val full = inputData.getBoolean(KEY_FULL, false)
        return try {
            synchronizer.sync(serverId, full) { setProgress(it.toWorkData()) }
            Result.success()
        } catch (e: SubsonicException) {
            outcomeFor(e.toRemoteException())
        } catch (e: RemoteException) {
            outcomeFor(e)
        }
    }

    /** Retry only what may succeed later; a rejected password will not. */
    private fun outcomeFor(error: RemoteException): Result =
        if (error is RemoteException.Unreachable && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: error::class.simpleName)))
        }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_FULL = "full"
        const val KEY_ERROR = "error"
        const val KEY_PHASE = "phase"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        private const val MAX_ATTEMPTS = 5
    }
}

internal fun SyncProgress.Running.toWorkData(): Data = workDataOf(
    LibrarySyncWorker.KEY_PHASE to phase.name,
    LibrarySyncWorker.KEY_COMPLETED to completed,
    LibrarySyncWorker.KEY_TOTAL to total,
)

internal fun WorkInfo?.toSyncProgress(): SyncProgress = when (this?.state) {
    null, WorkInfo.State.CANCELLED -> SyncProgress.Idle

    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SyncProgress.Pending

    WorkInfo.State.RUNNING -> SyncProgress.Running(
        phase = progress.getString(LibrarySyncWorker.KEY_PHASE)
            ?.let { name -> SyncPhase.entries.firstOrNull { it.name == name } }
            ?: SyncPhase.Artists,
        completed = progress.getInt(LibrarySyncWorker.KEY_COMPLETED, 0),
        total = progress.getInt(LibrarySyncWorker.KEY_TOTAL, 0),
    )

    WorkInfo.State.SUCCEEDED -> SyncProgress.Succeeded

    WorkInfo.State.FAILED -> SyncProgress.Failed(outputData.getString(LibrarySyncWorker.KEY_ERROR))
}
