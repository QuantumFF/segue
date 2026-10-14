package com.quantumff.segue.core.playback.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.quantumff.segue.core.model.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** The on-disk LRU cache of streamed audio. One instance per process, as SimpleCache requires. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamCache

private const val BYTES_PER_MB = 1024L * 1024L

fun megabytes(mb: Int): Long = mb * BYTES_PER_MB

@Module
@InstallIn(SingletonComponent::class)
object StreamCacheModule {
    @Provides
    @Singleton
    fun evictor(): AdjustableLruCacheEvictor =
        AdjustableLruCacheEvictor(megabytes(UserPreferences.DEFAULT_STREAM_CACHE_MB))

    @Provides
    @Singleton
    @StreamCache
    fun cache(@ApplicationContext context: Context, evictor: AdjustableLruCacheEvictor): Cache =
        SimpleCache(File(context.cacheDir, "stream"), evictor, StandaloneDatabaseProvider(context))

    /**
     * Reads through the stream cache and writes what it downloads into it. A cache error falls back
     * to the network rather than failing playback.
     */
    @Provides
    @Singleton
    fun cacheDataSourceFactory(@StreamCache cache: Cache, okHttpClient: OkHttpClient): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
