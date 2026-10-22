package com.quantumff.segue.core.playback.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.PlaybackError
import com.quantumff.segue.core.model.PlaybackErrorKind
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.testing.data.TestData
import java.io.IOException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackErrorReporterTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private var changes = 0
    private val reporter = PlaybackErrorReporter(exo) { changes++ }.also { exo.addListener(it) }

    @After
    fun tearDown() = exo.release()

    private fun queue(vararg ids: String) = exo.setMediaItems(ids.map { TestData.song(it).toMediaItem() })

    private fun failure(errorCode: Int, cause: Throwable? = null) = PlaybackException("failed", cause, errorCode)

    private fun httpStatus(code: Int) = failure(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        HttpDataSource.InvalidResponseCodeException(
            code,
            null,
            IOException(),
            emptyMap(),
            DataSpec(Uri.parse("https://music.example/rest/stream")),
            ByteArray(0),
        ),
    )

    private fun kindOf(error: PlaybackException): PlaybackErrorKind? {
        reporter.onPlayerError(error)
        return reporter.error?.kind
    }

    @Test
    fun `a failure reports the current song and why`() {
        queue("a", "b")

        reporter.onPlayerError(failure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        val song = TestData.song("a")
        assertThat(reporter.error).isEqualTo(PlaybackError("a", song.title, PlaybackErrorKind.Network))
        assertThat(changes).isEqualTo(1)
    }

    @Test
    fun `errors are classified into network, unsupported, not found and other`() {
        queue("a")
        val expected = mapOf(
            failure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) to PlaybackErrorKind.Network,
            failure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) to PlaybackErrorKind.Network,
            failure(PlaybackException.ERROR_CODE_TIMEOUT) to PlaybackErrorKind.Network,
            // What the URI resolver throws when it cannot reach the server for a stream URL.
            failure(PlaybackException.ERROR_CODE_IO_UNSPECIFIED) to PlaybackErrorKind.Network,
            httpStatus(503) to PlaybackErrorKind.Network,
            httpStatus(404) to PlaybackErrorKind.NotFound,
            httpStatus(410) to PlaybackErrorKind.NotFound,
            failure(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) to PlaybackErrorKind.NotFound,
            failure(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) to PlaybackErrorKind.Unsupported,
            failure(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES) to
                PlaybackErrorKind.Unsupported,
            failure(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) to PlaybackErrorKind.Unsupported,
            failure(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) to PlaybackErrorKind.Unsupported,
            failure(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) to PlaybackErrorKind.Unsupported,
            failure(PlaybackException.ERROR_CODE_DECODING_FAILED) to PlaybackErrorKind.Unsupported,
            httpStatus(401) to PlaybackErrorKind.Other,
            failure(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED) to PlaybackErrorKind.Other,
            failure(PlaybackException.ERROR_CODE_UNSPECIFIED) to PlaybackErrorKind.Other,
        )
        for ((error, kind) in expected) {
            assertThat(kindOf(error)).isEqualTo(kind)
        }
    }

    @Test
    fun `moving to another item clears the error`() {
        queue("a", "b")
        reporter.onPlayerError(failure(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))

        exo.seekToNextMediaItem()

        assertThat(reporter.error).isNull()
        assertThat(changes).isEqualTo(2)
    }

    @Test
    fun `a retry that gets ready to play clears the error`() {
        queue("a")
        reporter.onPlayerError(failure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        reporter.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertThat(reporter.error).isNotNull()

        reporter.onPlaybackStateChanged(Player.STATE_READY)
        assertThat(reporter.error).isNull()
    }

    @Test
    fun `playing again clears the error`() {
        queue("a")
        reporter.onPlayerError(failure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        reporter.onIsPlayingChanged(true)

        assertThat(reporter.error).isNull()
    }

    @Test
    fun `nothing changes without an error to clear`() {
        queue("a", "b")
        exo.seekToNextMediaItem()
        reporter.onPlaybackStateChanged(Player.STATE_READY)

        assertThat(changes).isEqualTo(0)
    }
}
