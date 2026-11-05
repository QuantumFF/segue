package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the download sheet on an album, artist or playlist page can do to its pin. Retry and Resume
 * put the pin's songs back in the queue, Pause holds them, and Cancel and Remove unpin it with Undo.
 */
enum class PinAction {
    Retry,
    Resume,
    Pause,
    Cancel,
    Remove,
}

/** The download sheet's actions for a pin in this status, in menu order. */
val DownloadStatus.pinActions: ImmutableList<PinAction>
    get() = when (this) {
        DownloadStatus.Failed -> persistentListOf(PinAction.Retry, PinAction.Remove)
        DownloadStatus.Paused -> persistentListOf(PinAction.Resume, PinAction.Remove)
        DownloadStatus.Queued, is DownloadStatus.Downloading -> persistentListOf(PinAction.Pause, PinAction.Cancel)
        DownloadStatus.Downloaded -> persistentListOf(PinAction.Remove)
    }
