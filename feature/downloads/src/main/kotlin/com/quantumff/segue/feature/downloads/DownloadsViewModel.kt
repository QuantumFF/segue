package com.quantumff.segue.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.data.download.unpinWithUndo
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.StorageLimit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Downloads screen (DESIGN.md §4.9). */
data class DownloadsUiState(
    /** True until Room's first answer, so the empty state does not flash. */
    val isLoading: Boolean = true,
    /** Pins still downloading, queued or paused. */
    val active: ImmutableList<PinnedDownload> = persistentListOf(),
    /** Pins whose remaining songs all failed. */
    val failed: ImmutableList<PinnedDownload> = persistentListOf(),
    /** Pins with every song on the device. */
    val downloaded: ImmutableList<PinnedDownload> = persistentListOf(),
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val storageLimit: StorageLimit = StorageLimit.None,
    /** The storage limit is full: queued downloads wait until space is freed or the limit raised. */
    val limitReached: Boolean = false,
    /** The manual "Offline mode" switch. */
    val forceOffline: Boolean = false,
    /** Offline right now: the switch, or no network. */
    val isOffline: Boolean = false,
) {
    val isEmpty: Boolean get() = active.isEmpty() && failed.isEmpty() && downloaded.isEmpty()

    /**
     * Share of the space downloads could grow into that they already use, for the storage bar:
     * the storage limit when one is set, else the free space on the volume.
     */
    val storageFraction: Float
        get() {
            val capacity = storageLimit.bytes?.coerceAtMost(usedBytes + freeBytes) ?: (usedBytes + freeBytes)
            return if (capacity <= 0) 0f else (usedBytes.toFloat() / capacity).coerceIn(0f, 1f)
        }

    companion object {
        /** Sorts pins into the three sections. */
        fun from(overview: DownloadOverview, forceOffline: Boolean, isOffline: Boolean): DownloadsUiState {
            val byStatus = overview.pins.groupBy { pin ->
                when (pin.status) {
                    DownloadStatus.Downloaded -> Section.Downloaded
                    DownloadStatus.Failed -> Section.Failed
                    else -> Section.Active
                }
            }
            return DownloadsUiState(
                isLoading = false,
                active = byStatus[Section.Active].orEmpty().toImmutableList(),
                failed = byStatus[Section.Failed].orEmpty().toImmutableList(),
                downloaded = byStatus[Section.Downloaded].orEmpty().sortedBy { it.name.lowercase() }.toImmutableList(),
                usedBytes = overview.usedBytes,
                freeBytes = overview.freeBytes,
                storageLimit = overview.storageLimit,
                limitReached = overview.limitReached,
                forceOffline = forceOffline,
                isOffline = isOffline,
            )
        }
    }

    private enum class Section { Active, Failed, Downloaded }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    offlineMode: OfflineModeRepository,
    private val snackbars: Snackbars,
) : ViewModel() {
    val uiState: StateFlow<DownloadsUiState> = combine(
        downloads.overview,
        downloads.preferences,
        offlineMode.isOffline,
    ) { overview, preferences, offline ->
        DownloadsUiState.from(overview, preferences.forceOffline, offline)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadsUiState())

    fun pause(pin: PinnedDownload) = launch { downloads.pause(pin.kind, pin.id) }

    /** Resumes a paused pin, or retries a failed one. */
    fun resume(pin: PinnedDownload) = launch { downloads.resume(pin.kind, pin.id) }

    /**
     * Cancels an active download or removes a finished one: unpins it at once, with Undo; its files
     * are deleted once the snackbar has gone.
     */
    fun remove(pin: PinnedDownload) = launch { downloads.unpinWithUndo(pin.kind, pin.id, pin.name, snackbars) }

    fun removeAll() = launch { downloads.removeAll() }

    fun setForceOffline(enabled: Boolean) = launch { downloads.setForceOffline(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
