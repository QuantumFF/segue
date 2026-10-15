package com.quantumff.segue.core.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quantumff.segue.core.data.R
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.DownloadDao
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.DownloadState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bytes of running downloads by `serverId/songId`. In memory only: Room keeps the states. */
@Singleton
internal class DownloadProgressTracker @Inject constructor() {
    private val state = MutableStateFlow<Map<String, LiveProgress>>(emptyMap())
    val progress: StateFlow<Map<String, LiveProgress>> = state.asStateFlow()

    fun update(row: DownloadEntity, bytes: Long, total: Long?) =
        state.update { it + (key(row.serverId, row.songId) to LiveProgress(bytes, total)) }

    fun remove(row: DownloadEntity) = state.update { it - key(row.serverId, row.songId) }

    companion object {
        fun key(serverId: String, songId: String) = "$serverId/$songId"
    }
}

/** Starts the download worker. */
internal fun interface DownloadScheduler {
    /** [restart] replaces a running worker, so changed constraints take effect at once. */
    fun schedule(preferences: DownloadPreferences, restart: Boolean)
}

/**
 * One unique worker drains the queue. Wi-Fi only maps to an unmetered network, charging only to
 * `requiresCharging`. A request while it runs is appended: the running worker picks up new rows
 * anyway, and the appended one ends at once if nothing is left.
 */
internal class WorkManagerDownloadScheduler @Inject constructor(private val workManager: WorkManager) :
    DownloadScheduler {
    override fun schedule(preferences: DownloadPreferences, restart: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraintsFor(preferences))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "song-downloads"
        private const val BACKOFF_SECONDS = 30L

        fun constraintsFor(preferences: DownloadPreferences): Constraints = Constraints.Builder()
            .setRequiredNetworkType(if (preferences.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(preferences.chargingOnly)
            .setRequiresStorageNotLow(true)
            .build()
    }
}

/**
 * Downloads queued songs, [CONCURRENCY] at a time, in a foreground service with a progress
 * notification. Each song resumes from its partial file. Network trouble puts the song back in
 * the queue and retries the worker later (with backoff, once the constraints hold again).
 */
@HiltWorker
internal class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val downloader: SongDownloader,
    private val files: DownloadFiles,
    private val progress: DownloadProgressTracker,
    private val preferences: DownloadPreferencesDataSource,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    private val notifications = DownloadNotifications(context)
    private val finished = AtomicInteger(0)

    override suspend fun doWork(): Result {
        dao.requeueInterrupted()
        if (dao.queuedCount() == 0) return Result.success()
        try {
            setForeground(foregroundInfo())
        } catch (e: IllegalStateException) {
            // Started from the background where a foreground service is not allowed: run as plain work.
            Log.w(TAG, "Downloading without a foreground service", e)
        }
        val quality = preferences.preferences.first().quality
        val retry = AtomicBoolean(false)
        coroutineScope {
            repeat(CONCURRENCY) {
                launch {
                    while (!retry.get()) {
                        val row = dao.claimNext(clock.nowMillis()) ?: break
                        if (!downloadOne(row, quality)) retry.set(true)
                        finished.incrementAndGet()
                        notifications.update(foregroundNotification())
                    }
                }
            }
        }
        return if (retry.get()) Result.retry() else Result.success()
    }

    /** False when the network failed and the worker should retry later. */
    private suspend fun downloadOne(row: DownloadEntity, quality: com.quantumff.segue.core.model.StreamingQuality): Boolean {
        var bytes = row.bytesDownloaded
        var total = row.totalBytes
        try {
            val outcome = downloader.download(
                row = row,
                quality = quality,
                onProgress = { written, size ->
                    bytes = written
                    total = size
                    progress.update(row, written, size)
                },
                stillWanted = { dao.isDownloading(row.serverId, row.songId) },
            )
            return when (outcome) {
                is DownloadOutcome.Completed -> {
                    val saved = dao.complete(row.serverId, row.songId, outcome.file.path, outcome.bytes, clock.nowMillis())
                    // Unpinned (or paused) during the last bytes: the row no longer wants this file.
                    if (!saved && dao.get(row.serverId, row.songId) == null) files.delete(outcome.file.path)
                    true
                }

                DownloadOutcome.Stopped -> {
                    if (dao.get(row.serverId, row.songId) == null) files.deleteSong(row.serverId, row.songId)
                    true
                }

                is DownloadOutcome.Retry -> {
                    val giveUp = runAttemptCount >= MAX_ATTEMPTS
                    dao.finishAttempt(
                        row.serverId,
                        row.songId,
                        if (giveUp) DownloadState.Failed else DownloadState.Queued,
                        outcome.bytes,
                        outcome.total,
                        outcome.reason,
                        clock.nowMillis(),
                    )
                    giveUp
                }

                is DownloadOutcome.Failed -> {
                    dao.finishAttempt(
                        row.serverId,
                        row.songId,
                        DownloadState.Failed,
                        0,
                        null,
                        outcome.reason,
                        clock.nowMillis(),
                    )
                    files.deleteSong(row.serverId, row.songId)
                    true
                }
            }
        } catch (e: CancellationException) {
            // Stopped by WorkManager (constraints lost, cancelled): back to the queue, keeping the bytes.
            withContext(NonCancellable) {
                dao.finishAttempt(row.serverId, row.songId, DownloadState.Queued, bytes, total, null, clock.nowMillis())
            }
            throw e
        } finally {
            progress.remove(row)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    private suspend fun foregroundInfo(): ForegroundInfo = ForegroundInfo(
        DownloadNotifications.NOTIFICATION_ID,
        foregroundNotification(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    private suspend fun foregroundNotification(): Notification {
        val done = finished.get()
        val left = dao.queuedCount() + dao.downloadingCount()
        return notifications.progress(done = done, total = done + left)
    }

    private companion object {
        const val TAG = "DownloadWorker"
        const val CONCURRENCY = 3
        const val MAX_ATTEMPTS = 10
    }
}

/** The "Downloading songs" notification, on its own low-importance channel. */
internal class DownloadNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.core_data_downloads_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun progress(done: Int, total: Int): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(context.getString(R.string.core_data_downloading_title))
        .setContentText(context.getString(R.string.core_data_downloading_progress, done, total))
        .setProgress(total, done, total == 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()

    fun update(notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 0x5e9e
    }
}
