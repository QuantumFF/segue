package com.quantumff.segue.core.model

import kotlinx.collections.immutable.toImmutableList

/**
 * What is on the device: fully downloaded songs, albums with at least one of them, and artists
 * credited on those songs or albums.
 */
data class DownloadedLibrary(
    val songIds: Set<String> = emptySet(),
    val albumIds: Set<String> = emptySet(),
    val artistIds: Set<String> = emptySet(),
)

/**
 * Narrows browse lists to downloaded music while offline, as Library does with its "Downloaded"
 * filter (DESIGN.md §1: offline is first-class). Online, everything passes.
 */
object OfflineLibrary {
    /** [albums] in their order; offline only those with a downloaded song. */
    fun albums(albums: List<Album>, isOffline: Boolean, downloaded: DownloadedLibrary): List<Album> =
        if (isOffline) albums.filter { it.id in downloaded.albumIds } else albums

    /** Search results; offline only downloaded songs, albums and artists. */
    fun search(result: SearchResult, isOffline: Boolean, downloaded: DownloadedLibrary): SearchResult {
        if (!isOffline) return result
        return result.copy(
            artists = result.artists.filter { it.id in downloaded.artistIds }.toImmutableList(),
            albums = result.albums.filter { it.id in downloaded.albumIds }.toImmutableList(),
            songs = result.songs.filter { it.id in downloaded.songIds }.toImmutableList(),
        )
    }

    /** "Shuffle albums" candidates: with [offline] (what is downloaded), only downloaded albums. */
    fun shuffleAlbumIds(albumIds: List<String>, offline: DownloadedLibrary?): List<String> =
        if (offline == null) albumIds else albumIds.filter { it in offline.albumIds }

    /** "Shuffle albums" songs: with [offline], only the downloaded ones, since nothing else can play. */
    fun shuffleSongs(songs: List<Song>, offline: DownloadedLibrary?): List<Song> =
        if (offline == null) songs else songs.filter { it.id in offline.songIds }
}
