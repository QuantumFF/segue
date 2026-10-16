package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList

/** One lyrics track for a song, from OpenSubsonic `getLyricsBySongId`. */
data class Lyrics(
    /** ISO 639 code. `xxx` and `und` mean unspecified. */
    val language: String,
    val synced: Boolean,
    /** Shift every line's start by this many milliseconds. */
    val offsetMillis: Long,
    val displayArtist: String?,
    val displayTitle: String?,
    val lines: ImmutableList<LyricLine>,
)

/** [startMillis] is null for unsynced lyrics. */
data class LyricLine(val startMillis: Long?, val text: String)
