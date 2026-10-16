package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.model.StreamingQuality
import javax.inject.Inject

/**
 * A stream to play: the authenticated URL, and a cache key that stays the same across requests
 * (the URL does not: every request has a new salt).
 */
data class StreamLocation(val url: String, val cacheKey: String)

/** Builds authenticated media URLs for the active server, for the player. */
interface MediaUrlRepository {
    /** Throws RemoteException.NotSignedIn when signed out. */
    suspend fun stream(songId: String, quality: StreamingQuality): StreamLocation

    /** Throws RemoteException.NotSignedIn when signed out. */
    suspend fun coverArtUrl(coverArtId: String, size: Int?): String
}

internal class SubsonicMediaUrlRepository @Inject constructor(private val clients: SubsonicClientProvider) :
    MediaUrlRepository {
    override suspend fun stream(songId: String, quality: StreamingQuality): StreamLocation {
        val serverId = clients.requireActiveServerId()
        val bitRate = quality.maxBitRateKbps.takeIf { it > 0 }
        val url = clients.client(serverId).streamUrl(songId, maxBitRateKbps = bitRate, format = quality.format)
        // The server and quality are part of the key: a transcode is a different file.
        val key = "$serverId/$songId/${bitRate ?: 0}/${quality.format ?: "raw"}"
        return StreamLocation(url, key)
    }

    override suspend fun coverArtUrl(coverArtId: String, size: Int?): String {
        val serverId = clients.requireActiveServerId()
        return clients.client(serverId).coverArtUrl(coverArtId, size)
    }
}
