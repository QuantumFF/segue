package com.quantumff.segue.core.network.auth

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Adds `u`, `t`, `s`, `v`, `c`, and `f` to every API request. Parameters already set are kept. */
internal class SubsonicAuthInterceptor(private val authParams: SubsonicAuthParams) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = authParams.applyTo(request.url.newBuilder(), existing = request.url)
            .setIfAbsent(request.url, "f", "json")
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}

/** Builds the auth query parameters, with a fresh salt each time. */
internal class SubsonicAuthParams(
    private val username: String,
    private val password: String,
    private val clientName: String,
    private val apiVersion: String,
    private val saltGenerator: SaltGenerator,
) {
    fun applyTo(builder: HttpUrl.Builder, existing: HttpUrl? = null): HttpUrl.Builder {
        val salt = saltGenerator.next()
        return builder
            .setIfAbsent(existing, "u", username)
            .setIfAbsent(existing, "t", SubsonicToken.create(password, salt))
            .setIfAbsent(existing, "s", salt)
            .setIfAbsent(existing, "v", apiVersion)
            .setIfAbsent(existing, "c", clientName)
    }
}

private fun HttpUrl.Builder.setIfAbsent(existing: HttpUrl?, name: String, value: String): HttpUrl.Builder =
    if (existing?.queryParameter(name) != null) this else setQueryParameter(name, value)
