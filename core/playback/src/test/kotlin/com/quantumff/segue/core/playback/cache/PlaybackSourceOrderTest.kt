package com.quantumff.segue.core.playback.cache

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.MediaUrlRepository
import com.quantumff.segue.core.data.repository.StreamLocation
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.playback.media.SegueUris
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The player reads a song from its downloaded file first, then the stream cache, then the
 * network. The "network" here is a data source that can be switched off, as in airplane mode.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackSourceOrderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val downloads = FakeDownloadRepository()
    private val network = SwitchableNetwork()
    private lateinit var cache: SimpleCache
    private lateinit var streamFactory: CacheDataSource.Factory

    private val mediaUrls = object : MediaUrlRepository {
        override suspend fun stream(songId: String, quality: StreamingQuality) =
            StreamLocation(url = "https://music.example/stream/$songId?t=salt", cacheKey = "srv/$songId")

        override suspend fun coverArtUrl(coverArtId: String, size: Int?) = "https://music.example/cover/$coverArtId"
    }

    @Before
    fun setUp() {
        cache = SimpleCache(tmp.newFolder("stream"), NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        streamFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory { network }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @After
    fun tearDown() = cache.release()

    private val resolver get() = SegueUriResolver(downloads, mediaUrls, FakeUserPreferencesRepository(), NetworkMonitor(context))

    private fun read(songId: String): String {
        val source = playbackDataSourceFactory(context, streamFactory, resolver).createDataSource()
        return try {
            source.open(DataSpec(SegueUris.song(songId)))
            String(DataSourceUtil.readToEnd(source))
        } finally {
            source.close()
        }
    }

    @Test
    fun `a downloaded song plays from its file, even with no network`() {
        downloads.files["s1"] = File(tmp.root, "s1.mp3").apply { writeText("from-file") }
        network.online = false

        assertThat(read("s1")).isEqualTo("from-file")
    }

    @Test
    fun `a downloaded file is not copied into the stream cache`() {
        downloads.files["s1"] = File(tmp.root, "s1.mp3").apply { writeText("from-file") }

        read("s1")

        assertThat(cache.keys).isEmpty()
    }

    @Test
    fun `a song in the stream cache plays from it with no network`() {
        network.body = "from-cache"
        val spec = resolver.resolveDataSpec(DataSpec(SegueUris.song("s2")))
        CacheWriter(streamFactory.createDataSourceForDownloading(), spec, null, null).cache()
        network.online = false

        assertThat(read("s2")).isEqualTo("from-cache")
    }

    @Test
    fun `any other song streams from the network, and fails without one`() {
        network.body = "from-network"

        assertThat(read("s3")).isEqualTo("from-network")

        network.online = false
        assertThrows(IOException::class.java) { read("s4") }
    }

    /** A fake upstream that serves [body] for any request while [online]. */
    private class SwitchableNetwork : DataSource {
        var online = true
        var body = ""
        private var source: DataSource? = null

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            if (!online) throw IOException("Airplane mode")
            val file = File.createTempFile("net", ".bin").apply {
                writeText(body)
                deleteOnExit()
            }
            val delegate = DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()).createDataSource()
            source = delegate
            return delegate.open(dataSpec.buildUpon().setUri(android.net.Uri.fromFile(file)).build())
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(source).read(buffer, offset, length)

        override fun getUri() = source?.uri

        override fun close() {
            source?.close()
            source = null
        }
    }
}
