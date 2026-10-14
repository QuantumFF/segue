package com.quantumff.segue.core.playback.cache

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.MediaUrlRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.playback.media.SegueUris
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Turns `segue://` URIs into authenticated server URLs when a loader opens them. Songs stream at
 * the Wi-Fi or cellular quality from settings, chosen by the network at that moment, and get a
 * stable cache key (the URL changes with every salt). Runs on loader threads, so blocking on the
 * account store is fine.
 */
class SegueUriResolver @Inject constructor(
    private val mediaUrls: MediaUrlRepository,
    private val preferences: UserPreferencesRepository,
    private val network: NetworkMonitor,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SegueUris.SCHEME) return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("No media ID in $uri")
        return try {
            runBlocking {
                when (uri.authority) {
                    SegueUris.SONG -> {
                        val location = mediaUrls.stream(id, quality())
                        dataSpec.buildUpon().setUri(Uri.parse(location.url)).setKey(location.cacheKey).build()
                    }

                    SegueUris.COVER -> {
                        val size = uri.getQueryParameter(SegueUris.SIZE)?.toIntOrNull()
                        dataSpec.withUri(Uri.parse(mediaUrls.coverArtUrl(id, size)))
                    }

                    else -> throw IOException("Unknown media URI $uri")
                }
            }
        } catch (e: RemoteException) {
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
