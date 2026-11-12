package com.quantumff.segue.feature.widget

import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.playback.sync.QueueRestore
import kotlinx.coroutines.flow.first

/** What a tap on the widget's surface (outside its buttons) does. */
internal enum class SurfaceTap {
    /** Opens the app on Now Playing. */
    OpenNowPlaying,

    /** Starts the playback service, which restores the saved queue on this device, paused at its last position. */
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
    suspend fun canResume(): Boolean {
        val snapshot = snapshots.snapshot.first() ?: return false
        val available = library.songsByIds(snapshot.songIds).mapTo(HashSet()) { it.id }
        return QueueRestore.plan(snapshot, available) != null
    }
}
