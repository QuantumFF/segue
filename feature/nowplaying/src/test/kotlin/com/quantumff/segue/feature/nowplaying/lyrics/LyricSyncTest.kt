package com.quantumff.segue.feature.nowplaying.lyrics

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.LyricLine
import com.quantumff.segue.core.model.Lyrics
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LyricSyncTest {
    private val starts = persistentListOf(1_000L, 2_500L, 4_000L)

    @Test
    fun noLineIsActiveBeforeTheFirstLine() {
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 999, offsetMillis = 0)).isEqualTo(-1)
    }

    @Test
    fun aLineIsActiveFromItsStart() {
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 1_000, offsetMillis = 0)).isEqualTo(0)
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 2_499, offsetMillis = 0)).isEqualTo(0)
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 2_500, offsetMillis = 0)).isEqualTo(1)
    }

    @Test
    fun theLastLineStaysActiveToTheEnd() {
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 600_000, offsetMillis = 0)).isEqualTo(2)
    }

    @Test
    fun noLinesMeansNoActiveLine() {
        assertThat(LyricSync.activeLineIndex(emptyList(), positionMillis = 5_000, offsetMillis = 0)).isEqualTo(-1)
    }

    @Test
    fun aPositiveOffsetShowsLinesSooner() {
        // OpenSubsonic: a positive offset makes lyrics appear sooner.
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 500, offsetMillis = 500)).isEqualTo(0)
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 499, offsetMillis = 500)).isEqualTo(-1)
    }

    @Test
    fun aNegativeOffsetShowsLinesLater() {
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 1_499, offsetMillis = -500)).isEqualTo(-1)
        assertThat(LyricSync.activeLineIndex(starts, positionMillis = 1_500, offsetMillis = -500)).isEqualTo(0)
    }

    @Test
    fun linesSharingAStartActivateAsTheLastOfThem() {
        val shared = listOf(1_000L, 1_000L, 3_000L)
        assertThat(LyricSync.activeLineIndex(shared, positionMillis = 1_000, offsetMillis = 0)).isEqualTo(1)
    }

    @Test
    fun theNextChangeIsTheNextLinesShiftedStart() {
        assertThat(LyricSync.nextChangeAt(starts, positionMillis = 0, offsetMillis = 0)).isEqualTo(1_000)
        assertThat(LyricSync.nextChangeAt(starts, positionMillis = 1_000, offsetMillis = 0)).isEqualTo(2_500)
        assertThat(LyricSync.nextChangeAt(starts, positionMillis = 1_000, offsetMillis = 200)).isEqualTo(2_300)
        assertThat(LyricSync.nextChangeAt(starts, positionMillis = 4_000, offsetMillis = 0)).isNull()
    }

    @Test
    fun theNextChangeSkipsLinesSharingTheCurrentStart() {
        val shared = listOf(1_000L, 1_000L, 3_000L)
        assertThat(LyricSync.nextChangeAt(shared, positionMillis = 500, offsetMillis = 0)).isEqualTo(1_000)
        assertThat(LyricSync.nextChangeAt(shared, positionMillis = 1_000, offsetMillis = 0)).isEqualTo(3_000)
    }

    @Test
    fun syncedLyricsAreSortedByStartAndKeepTheirOffset() {
        val lyrics = lyrics(
            synced = true,
            offsetMillis = 250,
            LyricLine(2_000, "second"),
            LyricLine(500, "first"),
        )
        val content = LyricsContent.from(lyrics)
        assertThat(content).isInstanceOf(LyricsContent.Synced::class.java)
        content as LyricsContent.Synced
        assertThat(content.lines).containsExactly("first", "second").inOrder()
        assertThat(content.starts).containsExactly(500L, 2_000L).inOrder()
        assertThat(content.offsetMillis).isEqualTo(250)
    }

    @Test
    fun unsyncedLyricsKeepTheirOrder() {
        val content = LyricsContent.from(
            lyrics(synced = false, offsetMillis = 0, LyricLine(null, "a"), LyricLine(null, "b")),
        )
        assertThat(content).isEqualTo(LyricsContent.Unsynced(persistentListOf("a", "b")))
    }

    @Test
    fun syncedLyricsWithoutTimesFallBackToUnsynced() {
        val content = LyricsContent.from(lyrics(synced = true, offsetMillis = 0, LyricLine(null, "a")))
        assertThat(content).isEqualTo(LyricsContent.Unsynced(persistentListOf("a")))
    }

    @Test
    fun blankLyricsAreNone() {
        assertThat(LyricsContent.from(lyrics(synced = false, offsetMillis = 0, LyricLine(null, " ")))).isNull()
        assertThat(LyricsContent.from(null)).isNull()
    }

    @Test
    fun theActiveLineFollowsPlaybackBetweenSamples() = runTest {
        val samples = MutableSharedFlow<PlaybackSample>(replay = 1)
        samples.emit(PlaybackSample(positionMillis = 0, isPlaying = true))
        LyricSync.activeLines(samples, starts, offsetMillis = 0).test {
            assertThat(awaitItem()).isEqualTo(-1)
            assertThat(awaitItem()).isEqualTo(0)
            assertThat(testScheduler.currentTime).isEqualTo(1_000)
            assertThat(awaitItem()).isEqualTo(1)
            assertThat(testScheduler.currentTime).isEqualTo(2_500)
            assertThat(awaitItem()).isEqualTo(2)
            assertThat(testScheduler.currentTime).isEqualTo(4_000)
            expectNoEvents()
        }
    }

    @Test
    fun aNewSampleResynchronises() = runTest {
        val samples = MutableSharedFlow<PlaybackSample>(replay = 1)
        samples.emit(PlaybackSample(positionMillis = 0, isPlaying = true))
        LyricSync.activeLines(samples, starts, offsetMillis = 0).test {
            assertThat(awaitItem()).isEqualTo(-1)
            // A seek past the second line's start.
            samples.emit(PlaybackSample(positionMillis = 3_000, isPlaying = true))
            assertThat(awaitItem()).isEqualTo(1)
            assertThat(awaitItem()).isEqualTo(2)
            assertThat(testScheduler.currentTime).isEqualTo(1_000)
        }
    }

    @Test
    fun whilePausedNoTimerRuns() = runTest {
        LyricSync.activeLines(flowOf(PlaybackSample(1_200, isPlaying = false)), starts, offsetMillis = 0).test {
            assertThat(awaitItem()).isEqualTo(0)
            awaitComplete()
        }
    }

    @Test
    fun repeatedSamplesOnTheSameLineEmitOnce() = runTest {
        val samples = flowOf(PlaybackSample(1_100, isPlaying = false), PlaybackSample(1_600, isPlaying = false))
        LyricSync.activeLines(samples, starts, offsetMillis = 0).test {
            assertThat(awaitItem()).isEqualTo(0)
            awaitComplete()
        }
    }

    private fun lyrics(synced: Boolean, offsetMillis: Long, vararg lines: LyricLine) = Lyrics(
        language = "eng",
        synced = synced,
        offsetMillis = offsetMillis,
        displayArtist = null,
        displayTitle = null,
        lines = lines.toList().toImmutableList(),
    )
}
