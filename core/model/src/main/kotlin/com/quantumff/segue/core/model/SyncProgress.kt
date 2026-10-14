package com.quantumff.segue.core.model

/** Library sync status, for a progress indicator. */
sealed interface SyncProgress {
    data object Idle : SyncProgress

    /** Enqueued but waiting, for example for a network connection. */
    data object Pending : SyncProgress

    /** [total] is 0 when the size of the phase is not known yet. */
    data class Running(val phase: SyncPhase, val completed: Int, val total: Int) : SyncProgress

    data object Succeeded : SyncProgress

    data class Failed(val message: String?) : SyncProgress
}

enum class SyncPhase {
    Artists,
    Genres,
    Albums,
    Songs,
    Playlists,
}
