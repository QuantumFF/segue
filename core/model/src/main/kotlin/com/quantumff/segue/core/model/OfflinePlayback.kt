package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/** A play request narrowed to what can play: the songs and where to start among them. */
data class PlayableSelection(val songs: List<Song>, val startIndex: Int)

/** What may play and be shown while offline (no network, or offline mode on). */
object OfflinePlayback {
    /**
     * [songs] with the ones not downloaded left out when [isOffline]. The start moves to the
     * requested song if it is downloaded, else to the next downloaded song after it, else to the
     * first one. Null when nothing is playable.
     */
    fun playable(songs: List<Song>, startIndex: Int, isOffline: Boolean, downloaded: Set<String>): PlayableSelection? {
        if (songs.isEmpty()) return null
        if (!isOffline) return PlayableSelection(songs, startIndex.coerceIn(songs.indices))
        val kept = songs.withIndex().filter { it.value.id in downloaded }
        if (kept.isEmpty()) return null
        val start = kept.indexOfFirst { it.index >= startIndex }.takeIf { it >= 0 } ?: 0
        return PlayableSelection(kept.map { it.value }, start)
    }

    /** Songs to queue (play next, add to queue): the downloaded ones when offline. */
    fun queueable(songs: List<Song>, isOffline: Boolean, downloaded: Set<String>): List<Song> =
        if (isOffline) songs.filter { it.id in downloaded } else songs
}

/**
 * Which track rows are enabled, with the downloads behind their badges. Online every row is;
 * offline only rows of fully downloaded songs (DESIGN.md §5.2).
 */
data class SongAvailability(
    val isOffline: Boolean = false,
    /** Downloads by song ID, for row badges. */
    val downloads: ImmutableMap<String, SongDownload> = persistentMapOf(),
) {
    fun isAvailable(song: Song): Boolean = !isOffline || downloads[song.id]?.state == DownloadState.Completed
}
