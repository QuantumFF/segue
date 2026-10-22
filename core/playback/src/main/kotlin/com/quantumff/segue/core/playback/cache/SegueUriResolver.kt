package com.quantumff.segue.core.playback.cache

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.image.CoverArtUrlResolver
import com.quantumff.segue.core.data.repository.MediaUrlRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.playback.media.SegueUris
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Turns `segue://` URIs into what a loader opens. A downloaded song resolves to its local file
 * (works in airplane mode). Other songs become authenticated server URLs that stream at the Wi-Fi
 * or cellular quality from settings, chosen by the network at that moment, with a stable cache
 * key (the URL changes with every salt), so the stream cache is read before the network. Cover
 * art becomes a signed `getCoverArt` URL from [CoverArtUrlResolver], with the stable cache key the
 * app's image loader uses too. Runs on loader threads, so blocking on Room and the account store
 * is fine. Each song's [requests] entry records what was asked for, for the stream format.
 */
class SegueUriResolver @Inject constructor(
    private val downloads: DownloadRepository,
    private val mediaUrls: MediaUrlRepository,
    private val covers: CoverArtUrlResolver,
    private val preferences: UserPreferencesRepository,
    private val network: NetworkMonitor,
) : ResolvingDataSource.Resolver {
    /** What the latest resolve of each song asked for: its downloaded file, or a stream quality. */
    internal val requests = StreamRequests()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SegueUris.SCHEME) return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("No media ID in $uri")
        return try {
            runBlocking {
                when (uri.authority) {
                    SegueUris.SONG -> {
                        downloads.localFile(id)?.let { file ->
                            requests.record(id, StreamRequest.Download(file.length()))
                            return@runBlocking dataSpec.withUri(Uri.fromFile(file))
                        }
                        val quality = quality()
                        val location = mediaUrls.stream(id, quality)
                        requests.record(id, StreamRequest.Stream(quality))
                        dataSpec.buildUpon().setUri(Uri.parse(location.url)).setKey(location.cacheKey).build()
                    }

                    SegueUris.COVER -> {
                        val size = uri.getQueryParameter(SegueUris.SIZE)?.toIntOrNull()
                            ?: throw IOException("No size in $uri")
                        val location = covers.resolve(id, size) ?: throw IOException("Not signed in")
                        dataSpec.buildUpon().setUri(Uri.parse(location.url)).setKey(location.cacheKey).build()
                    }

                    else -> throw IOException("Unknown media URI $uri")
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Signed out, a server error, or storage trouble: a load error the player reports, not a crash.
            throw IOException(e)
        }
    }

    private suspend fun quality(): StreamingQuality {
        val prefs = preferences.preferences.first()
        return when (network.current()) {
            NetworkType.Unmetered -> prefs.wifiStreamingQuality
            NetworkType.Metered, NetworkType.None -> prefs.cellularStreamingQuality
        }
    }
}
