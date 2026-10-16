package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.network.di.NetworkModule
import java.io.IOException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Navidrome answers `getCoverArt` with HTTP 200 and a Subsonic error document when it is busy
 * ("context deadline exceeded"), and with 429 when it throttles. An image loader that trusts the
 * 2xx caches that document as the image and shows the placeholder for that track from then on.
 */
class CoverArtResponseInterceptorTest {
    private val server = MockWebServer()
    private val sleeps = mutableListOf<Long>()
    private val client = OkHttpClient.Builder()
        .addInterceptor(CoverArtResponseInterceptor(sleep = { sleeps += it }))
        .build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun `an image passes through untouched`() {
        server.enqueue(image())

        val response = get("/rest/getCoverArt.view?id=mf-1&size=128")

        assertThat(response.code).isEqualTo(200)
        assertThat(response.bytes).isEqualTo(JPEG)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `a busy server's error document is retried until the image comes`() {
        server.enqueue(xmlError(code = 0, message = "Internal Server Error: context deadline exceeded"))
        server.enqueue(image())

        val response = get("/rest/getCoverArt.view?id=mf-1&size=128")

        assertThat(response.code).isEqualTo(200)
        assertThat(response.bytes).isEqualTo(JPEG)
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(sleeps.sum()).isEqualTo(500L)
    }

    @Test
    fun `an error document is never handed on as a successful image`() {
        repeat(CoverArtResponseInterceptor.MAX_ATTEMPTS) { server.enqueue(xmlError(code = 0, message = "busy")) }

        val response = get("/rest/getCoverArt.view?id=mf-1&size=128")

        assertThat(response.isSuccessful).isFalse()
        assertThat(response.bytes).isEmpty()
        assertThat(server.requestCount).isEqualTo(CoverArtResponseInterceptor.MAX_ATTEMPTS)
    }

    @Test
    fun `a JSON error document fails too`() {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"subsonic-response":{"status":"failed","error":{"code":70,"message":"not found"}}}""")
                .build(),
        )

        val response = get("/rest/getCoverArt?id=missing&f=json")

        assertThat(response.isSuccessful).isFalse()
    }

    @Test
    fun `a permanent error such as not found is not retried`() {
        server.enqueue(xmlError(code = 70, message = "Artwork not found"))

        val response = get("/rest/getCoverArt.view?id=missing")

        assertThat(response.isSuccessful).isFalse()
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `throttling is retried after the server's Retry-After, capped`() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "1").body("Too Many Requests").build())
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "3600").build())
        server.enqueue(image())

        val response = get("/rest/getCoverArt.view?id=mf-1&size=128")

        assertThat(response.code).isEqualTo(200)
        assertThat(sleeps.sum()).isEqualTo(1_000L + CoverArtResponseInterceptor.MAX_BACKOFF_MILLIS)
    }

    @Test
    fun `a request cancelled while it waits is not sent again`() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "2").build())
        server.enqueue(image())
        lateinit var call: Call
        val cancelling = OkHttpClient.Builder()
            .addInterceptor(CoverArtResponseInterceptor(sleep = { call.cancel() }))
            .build()
        call = cancelling.newCall(Request.Builder().url(server.url("/rest/getCoverArt.view?id=mf-1")).build())

        assertThrows(IOException::class.java) { call.execute() }
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `the app's one HTTP client, which every cover art consumer uses, carries the interceptor`() {
        assertThat(NetworkModule.okHttpClient().interceptors.filterIsInstance<CoverArtResponseInterceptor>())
            .hasSize(1)
    }

    @Test
    fun `throttling that does not end leaves the failure status`() {
        repeat(CoverArtResponseInterceptor.MAX_ATTEMPTS) { server.enqueue(MockResponse.Builder().code(429).build()) }

        val response = get("/rest/getCoverArt.view?id=mf-1")

        assertThat(response.code).isEqualTo(429)
        assertThat(server.requestCount).isEqualTo(CoverArtResponseInterceptor.MAX_ATTEMPTS)
    }

    @Test
    fun `other endpoints are left alone`() {
        server.enqueue(xmlError(code = 0, message = "busy"))

        val response = get("/rest/getAlbum.view?id=a1")

        assertThat(response.code).isEqualTo(200)
        assertThat(server.requestCount).isEqualTo(1)
    }

    private class Result(val code: Int, val isSuccessful: Boolean, val bytes: ByteArray)

    private fun get(path: String): Result =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { response ->
            Result(response.code, response.isSuccessful, response.body.bytes())
        }

    private fun image() = MockResponse.Builder()
        .addHeader("Content-Type", "image/jpeg")
        .body(Buffer().write(JPEG))
        .build()

    private fun xmlError(code: Int, message: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/xml")
        .body(
            """<subsonic-response xmlns="http://subsonic.org/restapi" status="failed" version="1.16.1">""" +
                """<error code="$code" message="$message"></error></subsonic-response>""",
        )
        .build()

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    }
}
