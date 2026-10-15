package com.quantumff.segue.core.playback.cache

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource

/**
 * Where the player reads a song from, in order:
 * 1. the downloaded file ([SegueUriResolver] resolves a downloaded song to `file://`, which
 *    [DefaultDataSource] reads directly, never through the stream cache);
 * 2. the stream cache, under the song's stable cache key;
 * 3. the network, through [streamDataSourceFactory] (a cache data source that also fills the cache).
 */
fun playbackDataSourceFactory(
    context: Context,
    streamDataSourceFactory: DataSource.Factory,
    resolver: ResolvingDataSource.Resolver,
): DataSource.Factory = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, streamDataSourceFactory), resolver)
