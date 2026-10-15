package com.quantumff.segue.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongFileInfo
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class NowPlayingUiStateTest {
    @Test
    fun losslessQualityShowsBitDepthAndSampleRate() {
        assertThat(qualityLabel("flac", 1_012, 44_100, 16)).isEqualTo("FLAC · 16/44.1")
        assertThat(qualityLabel("flac", 2_304, 96_000, 24)).isEqualTo("FLAC · 24/96")
    }

    @Test
    fun lossyQualityShowsTheBitRate() {
        assertThat(qualityLabel("mp3", 320, 44_100, null)).isEqualTo("MP3 · 320")
        assertThat(qualityLabel("opus", 128, null, 0)).isEqualTo("OPUS · 128")
    }

    @Test
    fun qualityNeedsAFormat() {
        assertThat(qualityLabel(null, 320, null, null)).isNull()
        assertThat(qualityLabel("ogg", null, null, null)).isEqualTo("OGG")
    }

    @Test
    fun sampleRatesReadInKilohertz() {
        assertThat(formatKiloHertz(44_100)).isEqualTo("44.1")
        assertThat(formatKiloHertz(48_000)).isEqualTo("48")
        assertThat(formatKiloHertz(88_200)).isEqualTo("88.2")
    }

    @Test
    fun aMinuteTimerEndsThatManyMinutesFromNow() {
        assertThat(SleepTimerChoice.Minutes(30).toSleepTimer(nowMillis = 1_000))
            .isEqualTo(SleepTimer.AtTime(1_000 + 30 * 60_000L))
        assertThat(SleepTimerChoice.EndOfAlbum.toSleepTimer(nowMillis = 0)).isEqualTo(SleepTimer.EndOfAlbum)
    }

    @Test
    fun neighborsWrapOnlyWithRepeatAll() {
        val player = player(currentQueueIndex = 0, repeatMode = RepeatMode.Off)
        assertThat(player.toNowPlayingUiState(null, null).previous).isNull()
        assertThat(player.toNowPlayingUiState(null, null).next?.timelineIndex).isEqualTo(1)
        val wrapping = player.copy(repeatMode = RepeatMode.All).toNowPlayingUiState(null, null)
        assertThat(wrapping.previous?.timelineIndex).isEqualTo(2)
    }

    @Test
    fun libraryDetailsApplyOnlyToTheirOwnSong() {
        val player = player(currentQueueIndex = 0)
        val stale =
            Song(
                id = "other",
                title = "x",
                albumId = null,
                album = null,
                artist = null,
                artistId = null,
                suffix = "mp3",
            )
        assertThat(player.toNowPlayingUiState(stale, null).quality).isNull()

        val song = Song(
            id = "s0",
            title = "Song 0",
            albumId = null,
            album = null,
            artist = "Artist",
            artistId = "ar-1",
            suffix = "flac",
            bitRate = 900,
        )
        val state = player.toNowPlayingUiState(song, SongFileInfo("a/b.flac", 44_100, 16, 2))
        assertThat(state.quality).isEqualTo("FLAC · 16/44.1")
        assertThat(state.track?.artists?.map { it.id }).containsExactly("ar-1")
        assertThat(state.details?.path).isEqualTo("a/b.flac")
    }

    private fun player(currentQueueIndex: Int, repeatMode: RepeatMode = RepeatMode.Off): PlayerUiState {
        val queue = (0..2).map { index ->
            QueueItem(
                songId = "s$index",
                timelineIndex = index,
                title = "Song $index",
                artist = "Artist",
                album = "Album",
                albumId = "al",
                coverArtId = "c$index",
                durationMillis = 1_000,
                isStarred = false,
            )
        }.toImmutableList()
        return PlayerUiState(
            currentItem = queue[currentQueueIndex],
            currentQueueIndex = currentQueueIndex,
            queue = queue,
            repeatMode = repeatMode,
        )
    }
}
