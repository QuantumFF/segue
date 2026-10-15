package com.quantumff.segue.core.data.download

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.StreamingQuality
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HttpSongDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val db = inMemoryDatabase()
    private val client = FakeSubsonicClient()
    private val row = DownloadEntity(SERVER_ID, "s1", DownloadState.Downloading, updatedAt = 0)
    private lateinit var files: DownloadFiles

    @Before
    fun setUp() {
        server.start()
        client.streamBaseUrl = server.url("/stream/").toString()
        files = DownloadFiles(tmp.newFolder("downloads"))
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun TestScope.downloader() = HttpSongDownloader(
        clients = FakeClientProvider(client),
        songDao = db.songDao(),
        okHttpClient = OkHttpClient(),
        files = files,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun response(code: Int, body: String, vararg headers: Pair<String, String>) = MockResponse.Builder()
        .code(code)
        .headers(Headers.headersOf(*headers.flatMap { listOf(it.first, it.second) }.toTypedArray()))
        .body(body)
        .build()

    @Test
    fun `an original download is saved under the song's suffix with format raw`() = runTest {
        db.songDao().upsertAll(listOf(SongEntity(SERVER_ID, "s1", "t", "t", suffix = "flac")))
        server.enqueue(response(200, "abcdef", "Content-Type" to "audio/flac"))

        val outcome = downloader().download(row, StreamingQuality.Original, { _, _ -> }, { true })

        val completed = outcome as DownloadOutcome.Completed
        assertThat(completed.file.name).isEqualTo("s1.flac")
        assertThat(completed.file.readText()).isEqualTo("abcdef")
        assertThat(client.streamRequests.single().third).isEqualTo("raw")
    }

    @Test
    fun `a partial file resumes with a range request`() = runTest {
        val target = files.fileFor(SERVER_ID, "s1", "audio")
        target.parentFile?.mkdirs()
        files.partFileFor(target).writeText("abc")
        server.enqueue(response(206, "def", "Content-Type" to "audio/mpeg"))

        val outcome = downloader().download(row, StreamingQuality.Original, { _, _ -> }, { true })

        assertThat(server.takeRequest().headers["Range"]).isEqualTo("bytes=3-")
        assertThat((outcome as DownloadOutcome.Completed).file.readText()).isEqualTo("abcdef")
    }

    @Test
    fun `a server that ignores the range restarts the file`() = runTest {
        val target = files.fileFor(SERVER_ID, "s1", "128.opus")
        target.parentFile?.mkdirs()
        files.partFileFor(target).writeText("stale")
        server.enqueue(response(200, "fresh", "Content-Type" to "audio/ogg"))

        val outcome = downloader().download(row, StreamingQuality(128, "opus"), { _, _ -> }, { true })

        assertThat((outcome as DownloadOutcome.Completed).file.readText()).isEqualTo("fresh")
        assertThat(client.streamRequests.single()).isEqualTo(Triple("s1", 128, "opus"))
    }

    @Test
    fun `a subsonic error body fails and a server error retries`() = runTest {
        server.enqueue(response(200, """{"subsonic-response":{"status":"failed"}}""", "Content-Type" to "application/json"))
        server.enqueue(response(503, "busy"))

        val refused = downloader().download(row, StreamingQuality.Original, { _, _ -> }, { true })
        val busy = downloader().download(row, StreamingQuality.Original, { _, _ -> }, { true })

        assertThat(refused).isInstanceOf(DownloadOutcome.Failed::class.java)
        assertThat(busy).isInstanceOf(DownloadOutcome.Retry::class.java)
    }

    @Test
    fun `a download no longer wanted stops and keeps its partial file`() = runTest {
        server.enqueue(response(200, "x".repeat(2 * 1024 * 1024), "Content-Type" to "audio/mpeg"))

        val outcome = downloader().download(row, StreamingQuality.Original, { _, _ -> }, { false })

        assertThat(outcome).isEqualTo(DownloadOutcome.Stopped)
        assertThat(files.partFileFor(files.fileFor(SERVER_ID, "s1", "audio")).length()).isGreaterThan(0L)
    }
}
