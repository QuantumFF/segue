package com.quantumff.segue.core.network

import com.quantumff.segue.core.network.auth.SaltGenerator
import com.quantumff.segue.core.network.auth.SubsonicAuthInterceptor
import com.quantumff.segue.core.network.auth.SubsonicAuthParams
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/**
 * Creates a [SubsonicClient] per server account. All clients share the app's [OkHttpClient], so
 * they share its connection pool and thread pool.
 *
 * The client is a [Provider] because this factory is built during `Application.onCreate`, and
 * building an [OkHttpClient] initializes TLS (about 50 ms of main-thread work at cold start).
 */
class SubsonicClientFactory @Inject constructor(
    private val okHttpClient: Provider<OkHttpClient>,
    private val json: Json,
    private val saltGenerator: SaltGenerator,
) {
    fun create(config: SubsonicServerConfig): SubsonicClient {
        val baseUrl = (config.baseUrl.trimEnd('/') + "/").toHttpUrl()
        val authParams = SubsonicAuthParams(
            username = config.username,
            password = config.password,
            clientName = CLIENT_NAME,
            apiVersion = API_VERSION,
            saltGenerator = saltGenerator,
        )
        val client = okHttpClient.get().newBuilder().addInterceptor(SubsonicAuthInterceptor(authParams)).build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<SubsonicApi>()
        return RetrofitSubsonicClient(
            api = api,
            json = json,
            restUrl = baseUrl.newBuilder().addPathSegment("rest").build(),
            authParams = authParams,
        )
    }

    companion object {
        /** The `c` parameter. Navidrome lists players by this name. */
        const val CLIENT_NAME = "Segue"

        /** The Subsonic API version Segue speaks. 1.16.1 is the base for OpenSubsonic. */
        const val API_VERSION = "1.16.1"
    }
}
