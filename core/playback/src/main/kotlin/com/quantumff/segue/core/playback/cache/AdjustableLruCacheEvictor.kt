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
 * The cache calls every method below with its own lock held.
 */
class AdjustableLruCacheEvictor(maxBytes: Long) : CacheEvictor {
    @Volatile
    var maxBytes: Long = maxBytes

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
        while (currentBytes + requiredBytes > maxBytes && spans.isNotEmpty()) {
            cache.removeSpan(spans.first())
        }
    }
}
