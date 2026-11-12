package com.quantumff.segue.feature.widget

import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.playback.sync.QueueRestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What a tap on the widget's surface (outside its buttons) does. */
internal enum class SurfaceTap {
    /** Opens the app on Now Playing. */
    OpenNowPlaying,

    /** The playback service restores the saved queue on this device, paused at its last position. */
    ResumeSavedQueue,

    /** Opens the app. */
    OpenApp,
}

/** The tap for a widget showing a track ([hasTrack]) or the empty state, given whether [SavedQueueProbe.canResume]. */
internal fun surfaceTap(hasTrack: Boolean, canResume: Boolean): SurfaceTap = when {
    hasTrack -> SurfaceTap.OpenNowPlaying
    canResume -> SurfaceTap.ResumeSavedQueue
    else -> SurfaceTap.OpenApp
}

/**
 * Whether the playback service would restore a saved queue on start: one is saved on this device
 * and some of its songs are still in the library (as `SavedQueueLoader` restores it). Room only.
 */
internal class SavedQueueProbe(
    private val snapshots: QueueSnapshotRepository,
    private val library: LibraryRepository,
) {
    suspend fun canResume(): Boolean = canResumeUpdates().first()

    /** [canResume], again each time the saved queue changes (Clear queue empties it after the widget). */
    fun canResumeUpdates(): Flow<Boolean> = snapshots.snapshot
        .map { snapshot ->
            snapshot ?: return@map false
            val available = library.songsByIds(snapshot.songIds).mapTo(HashSet()) { it.id }
            QueueRestore.plan(snapshot, available) != null
        }
        .distinctUntilChanged()
}
