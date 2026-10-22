package com.quantumff.segue.core.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.StreamFormat
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.playback.cache.StreamRequest
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.testing.data.TestData
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerUiStateProjectionTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val projection = PlayerUiStateProjection()

    @After
    fun tearDown() = exo.release()

    private fun items(vararg ids: String) = ids.map { TestData.song(it).toMediaItem() }

    private fun project(extras: Bundle = Bundle.EMPTY) = projection.project(exo, extras)

    @Test
    fun `an empty player is the empty state`() {
        assertThat(project()).isEqualTo(PlayerUiState())
    }

    @Test
    fun `the queue in play order, the current item and its position`() {
        exo.setMediaItems(items("a", "b", "c"), 1, 5_000)

        val state = project()

        assertThat(state.queue.map { it.songId }).containsExactly("a", "b", "c").inOrder()
        assertThat(state.queue.map { it.timelineIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(state.currentQueueIndex).isEqualTo(1)
        assertThat(state.currentItem?.songId).isEqualTo("b")
        assertThat(state.positionMillis).isEqualTo(5_000)
        assertThat(state.isPlaying).isFalse()
    }

    @Test
    fun `before the player knows the duration, the item's own stands in`() {
        exo.setMediaItems(items("a"))

        assertThat(project().durationMillis).isEqualTo(200_000)
    }

    @Test
    fun `the queue is rebuilt only when the timeline changes`() {
        exo.setMediaItems(items("a", "b"))
        val first = project().queue

        exo.seekTo(1, 3_000)
        assertThat(project().queue).isSameInstanceAs(first)

        exo.addMediaItems(items("c"))
        val grown = project().queue
        assertThat(grown).isNotSameInstanceAs(first)
        assertThat(grown.map { it.songId }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `buffering after play`() {
        exo.setMediaItems(items("a"))
        exo.prepare()
        exo.play()

        assertThat(project().isBuffering).isTrue()
    }

    @Test
    fun `repeat modes map both ways`() {
        for (mode in RepeatMode.entries) {
            exo.repeatMode = mode.playerRepeatMode
            assertThat(project().repeatMode).isEqualTo(mode)
        }
    }

    @Test
    fun `the sleep timer comes from the session extras`() {
        assertThat(project(SegueSessionCommands.sleepArgs(SleepTimer.EndOfAlbum)).sleepTimer)
            .isEqualTo(SleepTimer.EndOfAlbum)
        assertThat(project(SegueSessionCommands.sleepArgs(SleepTimer.AtTime(9_000))).sleepTimer)
            .isEqualTo(SleepTimer.AtTime(9_000))
        assertThat(project().sleepTimer).isNull()
    }

    @Test
    fun `no stream format before the player knows its tracks`() {
        exo.setMediaItems(items("a"))

        assertThat(project().streamFormat).isNull()
    }

    @Test
    fun `the stream format is the selected audio track's codec and bit rate`() {
        exo.setMediaItems(items("a"))
        val mp3 = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MPEG).setAverageBitrate(320_000)
            .setSampleRate(44_100).build()

        assertThat(projection.project(playing(mp3), Bundle.EMPTY).streamFormat)
            .isEqualTo(StreamFormat(codec = "MP3", bitRateKbps = 320, sampleRateHz = 44_100))
    }

    @Test
    fun `a lossless stream carries its bit depth and sample rate`() {
        exo.setMediaItems(items("a"))
        val flac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC).setSampleRate(96_000)
            .setPcmEncoding(C.ENCODING_PCM_24BIT).setAverageBitrate(4_608_000).build()

        val format = projection.project(playing(flac), Bundle.EMPTY).streamFormat

        assertThat(format?.codec).isEqualTo("FLAC")
        assertThat(format?.bitDepth).isEqualTo(24)
        assertThat(format?.sampleRateHz).isEqualTo(96_000)
    }

    @Test
    fun `a transcode names the requested codec and bit rate until the player decodes it`() {
        exo.setMediaItems(items("a"))
        val extras = requested("a", StreamRequest.Stream(StreamingQuality(maxBitRateKbps = 128, format = "opus")))

        assertThat(project(extras).streamFormat).isEqualTo(StreamFormat(codec = "OPUS", bitRateKbps = 128))
    }

    @Test
    fun `the requested bit rate fills in what the decoded codec does not declare`() {
        exo.setMediaItems(items("a"))
        val opus = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_OPUS).setSampleRate(48_000).build()
        val extras = requested("a", StreamRequest.Stream(StreamingQuality(maxBitRateKbps = 128, format = "opus")))

        assertThat(projection.project(playing(opus), extras).streamFormat)
            .isEqualTo(StreamFormat(codec = "OPUS", bitRateKbps = 128, sampleRateHz = 48_000))
    }

    @Test
    fun `a request the server did not honour leaves the bit rate unknown`() {
        exo.setMediaItems(items("a"))
        val opus = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_OPUS).build()
        val extras = requested("a", StreamRequest.Stream(StreamingQuality(maxBitRateKbps = 320, format = "mp3")))

        assertThat(projection.project(playing(opus), extras).streamFormat).isEqualTo(StreamFormat(codec = "OPUS"))
    }

    @Test
    fun `a request for another song is ignored`() {
        exo.setMediaItems(items("a", "b"))
        val extras = requested("b", StreamRequest.Stream(StreamingQuality(maxBitRateKbps = 128, format = "opus")))

        assertThat(project(extras).streamFormat).isNull()
    }

    @Test
    fun `an original-quality request names nothing by itself`() {
        exo.setMediaItems(items("a"))

        assertThat(project(requested("a", StreamRequest.Stream(StreamingQuality.Original))).streamFormat).isNull()
        assertThat(project(requested("a", StreamRequest.Stream(StreamingQuality(0, "raw")))).streamFormat).isNull()
    }

    @Test
    fun `a downloaded file's bit rate comes from its size and duration when its codec does not declare one`() {
        exo.setMediaItems(items("a")) // 200 seconds
        val opus = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_OPUS).build()
        val extras = requested("a", StreamRequest.Download(sizeBytes = 3_200_000))

        assertThat(projection.project(playing(opus), extras).streamFormat)
            .isEqualTo(StreamFormat(codec = "OPUS", bitRateKbps = 128))
    }

    private fun requested(songId: String, request: StreamRequest) =
        SegueSessionCommands.streamRequestArgs(songId, request)

    /** [exo] with [format] as its selected audio track, as once the current song has loaded. */
    private fun playing(format: Format): Player = object : ForwardingPlayer(exo) {
        override fun getCurrentTracks() = Tracks(
            ImmutableList.of(
                Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            ),
        )
    }
}
