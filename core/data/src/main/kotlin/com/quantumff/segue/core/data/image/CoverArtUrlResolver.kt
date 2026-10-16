package com.quantumff.segue.core.data.image

import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** A signed `getCoverArt` URL and a stable key to cache the image under. */
data class CoverArtLocation(
    /** Carries a fresh auth salt, so never use it as a cache key. */
    val url: String,
    /**
     * `v2:<serverId>:<coverArtId>:<size>`: stable across requests and unique across servers. The
     * version skips entries written before `getCoverArt` error documents were treated as failures
     * (some of those are a cached error, not an image); the caches' size limits evict them.
     */
    val cacheKey: String,
)

/** Builds cover art URLs for the active server, so the image loader never handles credentials. */
interface CoverArtUrlResolver {
    /** Null when signed out or the account's password can no longer be read. */
    suspend fun resolve(coverArtId: String, size: Int): CoverArtLocation?
}

internal class ActiveServerCoverArtUrlResolver @Inject constructor(private val clients: SubsonicClientProvider) :
    CoverArtUrlResolver {
    override suspend fun resolve(coverArtId: String, size: Int): CoverArtLocation? {
        val serverId = clients.activeServerId.first() ?: return null
        val client = try {
            clients.client(serverId)
        } catch (_: RemoteException.NotSignedIn) {
            return null
        }
        return CoverArtLocation(
            url = client.coverArtUrl(coverArtId, size),
            cacheKey = "$CACHE_KEY_VERSION:$serverId:$coverArtId:$size",
        )
    }
}

private const val CACHE_KEY_VERSION = "v2"
