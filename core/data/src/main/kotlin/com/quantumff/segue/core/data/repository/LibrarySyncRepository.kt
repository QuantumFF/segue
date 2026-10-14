package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.SyncScheduler
import com.quantumff.segue.core.model.SyncProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Mirrors the active server's library into Room in the background. */
interface LibrarySyncRepository {
    /** Progress of the active server's sync. */
    val progress: Flow<SyncProgress>

    /**
     * Schedules a sync once the device is online. It is incremental unless this is the first sync,
     * the last full sync is a week old, or [fullRefresh] is set. Ignored while one is pending.
     */
    suspend fun requestSync(fullRefresh: Boolean = false)
}

internal class WorkManagerLibrarySyncRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val scheduler: SyncScheduler,
) : LibrarySyncRepository {
    override val progress: Flow<SyncProgress> = clients.forActiveServer(SyncProgress.Idle, scheduler::progress)

    override suspend fun requestSync(fullRefresh: Boolean) {
        scheduler.requestSync(clients.requireActiveServerId(), fullRefresh)
    }
}
