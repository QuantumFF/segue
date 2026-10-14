package com.quantumff.segue.core.network.di

import com.quantumff.segue.core.network.auth.SaltGenerator
import com.quantumff.segue.core.network.auth.SecureRandomSaltGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Subsonic responses carry many fields we do not model; ignore them instead of failing.
     * Servers disagree on types (Navidrome sends numeric folder IDs, some send quoted numbers) and
     * send `null` for empty lists, so decode leniently and fall back to defaults.
     */
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /** The one OkHttp client. Per-server clients derive from it with `newBuilder()`. */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindingsModule {
    @Binds
    fun saltGenerator(impl: SecureRandomSaltGenerator): SaltGenerator
}
