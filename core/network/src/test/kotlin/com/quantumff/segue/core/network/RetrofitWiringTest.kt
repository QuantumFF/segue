package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.network.di.NetworkModule
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET

/** Proves Retrofit + kotlinx.serialization + MockWebServer are wired. Not the real Subsonic client. */
class RetrofitWiringTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `decodes a Subsonic ping envelope and ignores unknown keys`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true}}""")
                .build(),
        )
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkModule.json().asConverterFactory("application/json".toMediaType()))
            .build()
            .create<PingApi>()

        val response = api.ping().response

        assertThat(response.status).isEqualTo("ok")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/rest/ping")
    }

    private interface PingApi {
        @GET("rest/ping")
        suspend fun ping(): Envelope
    }

    @Serializable
    private data class Envelope(@SerialName("subsonic-response") val response: Body)

    @Serializable
    private data class Body(val status: String, val version: String)
}
