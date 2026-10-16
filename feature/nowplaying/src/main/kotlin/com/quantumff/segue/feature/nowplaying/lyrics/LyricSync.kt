package com.quantumff.segue.feature.nowplaying.lyrics

import androidx.compose.runtime.Immutable
import com.quantumff.segue.core.model.Lyrics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** Where playback was when the sample was taken. */
data class PlaybackSample(val positionMillis: Long, val isPlaying: Boolean)

/** Lyrics as Now Playing shows them: synced lines with start times, or plain lines. */
@Immutable
sealed interface LyricsContent {
    /** [starts] are line start times in milliseconds, ascending, parallel to [lines]. */
    data class Synced(val lines: ImmutableList<String>, val starts: ImmutableList<Long>, val offsetMillis: Long) :
        LyricsContent

    data class Unsynced(val lines: ImmutableList<String>) : LyricsContent

    companion object {
        /**
         * Synced when every line has a start time (sorted by it, stable for lines sharing one);
         * otherwise unsynced. Null when there are no lyrics or every line is blank.
         */
        fun from(lyrics: Lyrics?): LyricsContent? {
            if (lyrics == null || lyrics.lines.all { it.text.isBlank() }) return null
            val synced = lyrics.synced && lyrics.lines.all { it.startMillis != null }
            if (!synced) return Unsynced(lyrics.lines.map { it.text }.toImmutableList())
            val sorted = lyrics.lines.sortedBy { it.startMillis }
            return Synced(
                lines = sorted.map { it.text }.toImmutableList(),
                starts = sorted.map { requireNotNull(it.startMillis) }.toImmutableList(),
                offsetMillis = lyrics.offsetMillis,
            )
        }
    }
}

/**
 * Line timing for synced lyrics. A line is active from its start until the next line's start; the
 * last line stays active to the end. OpenSubsonic offsets are in milliseconds and a positive offset
 * makes lyrics appear sooner, so a line starts at `start - offset` on the playback clock.
 */
object LyricSync {
    /**
     * Index into [starts] (ascending) of the line active at [positionMillis], or -1 before the
     * first line. Lines sharing a start activate together as the last of them.
     */
    fun activeLineIndex(starts: List<Long>, positionMillis: Long, offsetMillis: Long): Int {
        val lyricTime = positionMillis + offsetMillis
        // Binary search for the last start <= lyricTime.
        var low = 0
        var high = starts.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= lyricTime) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    /** The playback position at which the active line next changes, or null after the last line starts. */
    fun nextChangeAt(starts: List<Long>, positionMillis: Long, offsetMillis: Long): Long? {
        val next = activeLineIndex(starts, positionMillis, offsetMillis) + 1
        return starts.getOrNull(next)?.let { it - offsetMillis }
    }

    /**
     * The active line, from [samples] of the playback position. Between samples a timer fires at
     * the next line's start while playing (the "next-line timer" of DESIGN.md §6), so lines change
     * on time however coarse the samples are; while paused nothing runs. Each sample resyncs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun activeLines(samples: Flow<PlaybackSample>, starts: List<Long>, offsetMillis: Long): Flow<Int> =
        samples.transformLatest { sample ->
            var position = sample.positionMillis
            emit(activeLineIndex(starts, position, offsetMillis))
            if (!sample.isPlaying) return@transformLatest
            while (true) {
                val next = nextChangeAt(starts, position, offsetMillis) ?: break
                delay(next - position)
                position = next
                emit(activeLineIndex(starts, position, offsetMillis))
            }
        }.distinctUntilChanged()
}
