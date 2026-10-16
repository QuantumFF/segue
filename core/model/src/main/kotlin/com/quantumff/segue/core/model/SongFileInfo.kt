package com.quantumff.segue.core.model

/**
 * File details of a song that the synced library does not keep: from `getSong`, for the Now
 * Playing quality pill and details sheet. Missing values are null.
 */
data class SongFileInfo(
    /** Path on the server, relative to its music folder. Servers may hide it. */
    val path: String?,
    /** Hz. */
    val samplingRate: Int?,
    /** Bits per sample; 0 or null for lossy formats. */
    val bitDepth: Int?,
    val channelCount: Int?,
)
