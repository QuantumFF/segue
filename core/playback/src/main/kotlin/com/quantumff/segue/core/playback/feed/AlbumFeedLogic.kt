package com.quantumff.segue.core.playback.feed

import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.Song
import kotlin.math.min

/** Window sizes and the edit check of an album feed (docs/adr/0003). */
object AlbumFeedPolicy {
    /** Tracks the first window queues: enough for a long session, small enough to queue at once. */
    const val WINDOW_TRACKS = 200

    /** Tracks each later window appends. */
    const val EXTENSION_TRACKS = 100

    /** The feed appends a window once fewer than this many tracks are left after the current one. */
    const val LOW_WATER_TRACKS = 30

    fun shouldExtend(remaining: Int): Boolean = remaining < LOW_WATER_TRACKS

    /**
     * Identifies a queue by its song IDs in order (FNV-1a over the IDs and their lengths). The
     * feed records it after each of its own changes; a different value means someone else edited
     * the queue. Cheap enough to compute on every queue event.
     */
    fun fingerprint(songIds: List<String>): Long {
        var hash = Fnv1a.mix(Fnv1a.OFFSET, songIds.size)
        for (id in songIds) hash = Fnv1a.mix(Fnv1a.mix(hash, id.length), id)
        return hash
    }
}

/** 64-bit FNV-1a: a fast, stable hash of strings and ints, for fingerprints and shuffle keys. */
private object Fnv1a {
    const val OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME = 0x100000001b3L

    fun mix(hash: Long, value: Int): Long = (hash xor value.toLong()) * PRIME

    fun mix(hash: Long, text: String): Long = text.fold(hash) { acc, char -> mix(acc, char.code) }
}

/** The order an album play queues albums in, or songs in a tracks shuffle. */
object AlbumFeedOrder {
    /**
     * [ids] (duplicates dropped) as [order] has them: unchanged, or shuffled by [seed]. The shuffle
     * sorts by a hash of the seed and each ID, so the same seed always gives the same order,
     * whatever order the IDs come in, and albums or songs added to the library later slot in
     * without reshuffling the rest. A persisted feed can therefore rebuild its order instead of
     * storing it.
     */
    fun order(ids: List<String>, order: AlbumOrder, seed: Long): List<String> = when (order) {
        AlbumOrder.InOrder -> ids.distinct()

        // Each key is hashed once: a tracks shuffle sorts every song in the library.
        AlbumOrder.Shuffled, AlbumOrder.ShuffledTracks -> ids.distinct()
            .map { it to shuffleKey(seed, it) }
            .sortedWith(compareBy<Pair<String, Long>> { it.second }.thenBy { it.first })
            .map { it.first }
    }

    private fun shuffleKey(seed: Long, id: String): Long = splitMix(seed xor Fnv1a.mix(Fnv1a.OFFSET, id))

    /** SplitMix64's finalizer: spreads nearby inputs over the whole range. */
    private fun splitMix(value: Long): Long {
        var z = value + GOLDEN_GAMMA
        z = (z xor (z ushr SHIFT_1)) * MIX_1
        z = (z xor (z ushr SHIFT_2)) * MIX_2
        return z xor (z ushr SHIFT_3)
    }

    private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9e3779b97f4a7c15
    private const val MIX_1 = -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
    private const val MIX_2 = -0x6b2fb644ecceee15L // 0x94d049bb133111eb
    private const val SHIFT_1 = 30
    private const val SHIFT_2 = 27
    private const val SHIFT_3 = 31
}

/**
 * Cuts an album order into windows of whole albums. [songsOf] reads the playable songs of some
 * albums (any order); it is called for a few albums at a time, so a window never reads more of
 * the library than it queues.
 */
class AlbumFeedWindows(private val songsOf: suspend (albumIds: List<String>) -> List<Song>) {
    /**
     * Songs to queue, album after album, each in disc and track order. [cursor] is the next unused
     * position in the album order; [exhausted] is true once every album is used up.
     */
    data class Window(val songs: List<Song>, val cursor: Int, val exhausted: Boolean)

    /**
     * Whole albums of [order] from [cursor] until the window holds at least [minTracks] tracks or
     * the order runs out. Albums in [skip] (already queued) and albums with nothing to play are
     * passed over, but still used up.
     */
    suspend fun next(order: List<String>, cursor: Int, minTracks: Int, skip: Set<String> = emptySet()): Window {
        val songs = ArrayList<Song>()
        var next = cursor.coerceIn(0, order.size)
        while (next < order.size && songs.size < minTracks) {
            val batch = order.subList(next, min(next + BATCH_ALBUMS, order.size)).toList()
            val wanted = batch.filterNot { it in skip }
            val loaded = if (wanted.isEmpty()) emptyMap() else songsOf(wanted).groupBy { it.albumId }
            for (albumId in batch) {
                next++
                if (albumId !in skip) songs += loaded[albumId].orEmpty().sortedWith(DISC_AND_TRACK)
                if (songs.size >= minTracks) break
            }
        }
        return Window(songs, next, exhausted = next >= order.size)
    }

    companion object {
        /** Albums read per query: a typical album has about ten tracks. */
        const val BATCH_ALBUMS = 8

        /** Each album is queued whole, in disc and track order. */
        private val DISC_AND_TRACK = compareBy<Song>({ it.discNumber ?: 0 }, { it.track ?: 0 })
    }
}
