package com.quantumff.segue.core.model

/** Where an offline download of a song stands. */
enum class DownloadState {
    Queued,
    Downloading,
    Completed,
    Failed,
}
