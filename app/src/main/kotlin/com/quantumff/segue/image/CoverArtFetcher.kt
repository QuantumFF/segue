package com.quantumff.segue.image

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.toUri
import com.quantumff.segue.core.data.image.CoverArtUrlResolver
import com.quantumff.segue.core.designsystem.art.CoverArtRequest

/**
 * Loads a [CoverArtRequest]: resolves the signed `getCoverArt?id=…&size=…` URL for the active
 * server, then hands it to the OkHttp network fetcher. The URL carries a fresh auth salt on every
 * call, so the disk cache key is the stable `<serverId>:<coverArtId>:<size>` instead.
 *
 * Signed out (no URL): the fetch fails and the placeholder stays (DESIGN.md §5.8).
 */
internal class CoverArtFetcher(
    private val request: CoverArtRequest,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val resolver: CoverArtUrlResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val location = resolver.resolve(request.coverArtId, request.size)
            ?: error("No active server for cover art ${request.coverArtId}")
        val networkOptions = options.copy(diskCacheKey = location.cacheKey)
        val uri = location.url.toUri()
        val (fetcher, _) = checkNotNull(imageLoader.components.newFetcher(uri, networkOptions, imageLoader)) {
            "No network fetcher registered"
        }
        return fetcher.fetch()
    }

    class Factory(private val resolver: CoverArtUrlResolver) : Fetcher.Factory<CoverArtRequest> {
        override fun create(data: CoverArtRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverArtFetcher(data, options, imageLoader, resolver)
    }
}

/**
 * Memory cache key for cover art. The active server is not part of it: the memory cache is
 * cleared when the active server changes (see SegueApplication).
 */
internal class CoverArtKeyer : Keyer<CoverArtRequest> {
    override fun key(data: CoverArtRequest, options: Options): String = "cover:${data.coverArtId}:${data.size}"
}
