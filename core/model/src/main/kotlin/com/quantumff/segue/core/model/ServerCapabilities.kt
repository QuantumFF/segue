package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * What a server can do, detected at login and on app start, then cached.
 *
 * [extensions] maps an OpenSubsonic extension name (from `getOpenSubsonicExtensions`) to the
 * versions the server supports.
 */
data class ServerCapabilities(
    val apiVersion: String,
    val openSubsonic: Boolean,
    val serverType: String?,
    val serverVersion: String?,
    val extensions: ImmutableMap<String, ImmutableList<Int>>,
) {
    /** Use `getPlayQueueByIndex`/`savePlayQueueByIndex`, which handle duplicate tracks. */
    val supportsIndexBasedQueue: Boolean get() = supports(INDEX_BASED_QUEUE)

    /** `getLyricsBySongId` is available. */
    val supportsSongLyrics: Boolean get() = supports(SONG_LYRICS)

    /** `stream` honors `timeOffset`, so seeking works while transcoding. */
    val supportsTranscodeOffset: Boolean get() = supports(TRANSCODE_OFFSET)

    /** Parameters may be sent as an HTTP form POST body. */
    val supportsFormPost: Boolean get() = supports(FORM_POST)

    /** `getTopSongs` accepts an artist ID. */
    val supportsTopSongsByArtistId: Boolean get() = supports(TOP_SONGS_BY_ARTIST_ID)

    fun supports(extension: String, version: Int? = null): Boolean {
        if (!openSubsonic) return false
        val versions = extensions[extension] ?: return false
        return version == null || version in versions
    }

    companion object {
        const val INDEX_BASED_QUEUE = "indexBasedQueue"
        const val SONG_LYRICS = "songLyrics"
        const val TRANSCODE_OFFSET = "transcodeOffset"
        const val FORM_POST = "formPost"
        const val TOP_SONGS_BY_ARTIST_ID = "topSongsByArtistId"

        /** Before detection has run. Assumes a plain Subsonic server. */
        val Unknown = ServerCapabilities(
            apiVersion = "1.16.1",
            openSubsonic = false,
            serverType = null,
            serverVersion = null,
            extensions = persistentMapOf(),
        )
    }
}
