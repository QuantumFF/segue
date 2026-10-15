package com.quantumff.segue.core.data.download

import android.util.Log
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.DownloadDao
import com.quantumff.segue.core.datastore.AccountsDataSource
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-wide download wiring. Call [start] once from `Application.onCreate`.
 *
 * - Pins follow the library: whenever the songs the active server's pins cover change (a library
 *   sync rewrote a pinned playlist, an artist got a new album, an album was refreshed), the
 *   download rows are reconciled. Room notifies on writes, so this is event-driven, not polled.
 * - Queued downloads left from the last run get their worker scheduled again.
 * - Files of servers that were signed out are deleted.
 */
@Singleton
class DownloadInitializer @Inject internal constructor(
    private val clients: SubsonicClientProvider,
    private val dao: DownloadDao,
    private val repository: OfflineDownloadRepository,
    private val scheduler: DownloadScheduler,
    private val preferences: DownloadPreferencesDataSource,
    private val accounts: AccountsDataSource,
    private val files: DownloadFiles,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            clients.activeServerId
                .flatMapLatest { serverId ->
                    if (serverId == null) {
                        emptyFlow()
                    } else {
                        dao.observePinMembership(serverId)
                            .map { serverId to DownloadPlanner.desiredSongs(it) }
                            .distinctUntilChanged()
                            // A sync writes in batches; reconcile once it settles.
                            .debounce(RECONCILE_DEBOUNCE_MILLIS)
                    }
                }
                .collect { (serverId, _) -> repository.reconcile(serverId) }
        }
        scope.launch {
            if (dao.queuedCount() + dao.downloadingCount() > 0) {
                scheduler.schedule(preferences.preferences.first(), restart = false)
            }
            val signedIn = accounts.accounts.first().mapTo(HashSet()) { it.id }
            if (signedIn.isNotEmpty()) {
                try {
                    files.deleteServersExcept(signedIn)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not clean up downloads of removed servers", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DownloadInitializer"
        const val RECONCILE_DEBOUNCE_MILLIS = 1_000L
    }
}
