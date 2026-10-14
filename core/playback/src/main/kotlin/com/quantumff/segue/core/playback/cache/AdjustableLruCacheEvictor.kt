package com.quantumff.segue.core.playback.cache

import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Evicts least recently used spans once the cache is over [maxBytes]. Like Media3's
 * `LeastRecentlyUsedCacheEvictor`, except the limit can change at runtime when the user changes
 * the cache size setting; a smaller limit takes effect at the next write.
 *
 * The limit starts unknown (nothing is evicted) until the setting has been read. SimpleCache
 * replays every stored span at startup, and evicting against a default there would delete audio
 * that a user with a larger setting meant to keep.
 *
 * The cache calls every callback below with its own lock held.
 */
class AdjustableLruCacheEvictor : CacheEvictor {
    @Volatile
    var maxBytes: Long = UNKNOWN

    private val spans =
        TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.then { a, b -> a.compareTo(b) })
    private var currentBytes = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans += span
        currentBytes += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans -= span
        currentBytes -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, requiredBytes: Long) {
        val limit = maxBytes
        if (limit == UNKNOWN) return
        while (currentBytes + requiredBytes > limit && spans.isNotEmpty()) {
            cache.removeSpan(spans.first())
        }
    }

    private companion object {
        const val UNKNOWN = -1L
    }
}
