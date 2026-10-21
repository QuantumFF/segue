package com.quantumff.segue.core.network

import com.quantumff.segue.core.network.auth.SaltGenerator
import com.quantumff.segue.core.network.di.NetworkModule
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.rules.ExternalResource

/** A MockWebServer plus a [SubsonicClient] pointed at it, with a predictable salt sequence. */
class SubsonicServerRule : ExternalResource() {
    val server = MockWebServer()
    private var saltCounter = 0
    val saltGenerator = SaltGenerator { "salt${saltCounter++}" }

    lateinit var client: SubsonicClient
        private set

    override fun before() {
        server.start()
        val config =
            SubsonicServerConfig(baseUrl = server.url("/music").toString(), username = "admin", password = "sesame")
        client = SubsonicClientFactory({ OkHttpClient() }, NetworkModule.json(), saltGenerator).create(config)
    }

    override fun after() = server.close()

    fun enqueueFixture(name: String) = enqueueBody(fixture(name))

    fun enqueueOk() = enqueueBody("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""")

    fun enqueueBody(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    fun takeRequest(): RecordedRequest = server.takeRequest()

    companion object {
        fun fixture(name: String): String =
            checkNotNull(SubsonicServerRule::class.java.classLoader?.getResource("fixtures/$name")) {
                "No fixture $name"
            }
                .readText()
    }
}

/** Every value of a query parameter, in order. */
fun RecordedRequest.params(name: String): List<String?> = url.queryParameterValues(name)

fun RecordedRequest.param(name: String): String? = url.queryParameter(name)

/** Every value of a field of the request's form body, in order. */
fun RecordedRequest.formParams(name: String): List<String?> =
    "http://form/?${body?.utf8().orEmpty()}".toHttpUrl().queryParameterValues(name)

fun RecordedRequest.formParam(name: String): String? = formParams(name).firstOrNull()
