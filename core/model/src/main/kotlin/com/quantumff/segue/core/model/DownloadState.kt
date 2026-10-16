package com.quantumff.segue.core.model

/** Where an offline download of a song stands. */
enum class DownloadState {
    Queued,
    Downloading,

    /** Held by the user; the partial file is kept so it resumes where it stopped. */
    Paused,
    Completed,
    Failed,
}
