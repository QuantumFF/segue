package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Which track rows are enabled, with the downloads behind their badges. Online every row is;
 * offline only rows of fully downloaded songs (DESIGN.md §5.2). What is queued offline is narrowed
 * by the playback service (QueueEditor in core:playback).
 */
data class SongAvailability(
    val isOffline: Boolean = false,
    /** Downloads by song ID, for row badges. */
    val downloads: ImmutableMap<String, SongDownload> = persistentMapOf(),
) {
    fun isAvailable(song: Song): Boolean = !isOffline || downloads[song.id]?.state == DownloadState.Completed
}
