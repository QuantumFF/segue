package com.quantumff.segue.core.model

/**
 * What may be shown as playable while offline (no network, or offline mode on). What is queued
 * offline is narrowed by the playback service (QueueEditor in core:playback).
 */
object OfflinePlayback {
    /** Whether a track row is enabled: always online, only when downloaded offline (DESIGN.md §5.2). */
    fun isAvailable(songId: String, isOffline: Boolean, downloaded: Set<String>): Boolean =
        !isOffline || songId in downloaded
}
