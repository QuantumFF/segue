package com.quantumff.segue.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.quantumff.segue.core.data.image.CoverArtUrlResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/** Coil configuration (DESIGN.md §6): memory cache 20% of the heap, disk cache 512 MB, shared OkHttp client. */
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        resolver: CoverArtUrlResolver,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(CoverArtKeyer())
            add(CoverArtFetcher.Factory(resolver))
            // The app's single OkHttp client: one connection pool for API calls and images.
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(DISK_CACHE_DIR).toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(false)
        .build()

    private const val MEMORY_CACHE_PERCENT = 0.2
    private const val DISK_CACHE_DIR = "image_cache"
    private const val DISK_CACHE_BYTES = 512L * 1024 * 1024
}
