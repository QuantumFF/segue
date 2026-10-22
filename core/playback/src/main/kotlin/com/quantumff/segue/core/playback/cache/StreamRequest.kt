package com.quantumff.segue.core.playback.cache

import com.quantumff.segue.core.model.StreamingQuality
import java.util.concurrent.ConcurrentHashMap

/** What the player asked for when it opened a song: part of the stream format the UI shows. */
internal sealed interface StreamRequest {
    /** A stream from the server at [quality]: the original file, or a transcode. */
    data class Stream(val quality: StreamingQuality) : StreamRequest {
        /** The codec the server was asked to transcode to, or null for the original file. */
        val transcodeFormat: String? get() = quality.format?.takeUnless { it.equals(RAW, ignoreCase = true) }

        /** The bit rate the server was asked to stay under, or null for none. */
        val maxBitRateKbps: Int? get() = quality.maxBitRateKbps.takeIf { it > 0 && transcodeFormat != null }
    }

    /** The downloaded file, of [sizeBytes]. */
    data class Download(val sizeBytes: Long) : StreamRequest

    private companion object {
        const val RAW = "raw"
    }
}

/** The latest [StreamRequest] of each song, recorded on loader threads and read on the player's. */
internal class StreamRequests {
    private val bySong = ConcurrentHashMap<String, StreamRequest>()

    fun record(songId: String, request: StreamRequest) {
        bySong[songId] = request
    }

    operator fun get(songId: String): StreamRequest? = bySong[songId]
}
