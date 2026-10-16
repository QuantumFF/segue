package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IdleStopTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private var timeouts = 0
    private var timeoutMillis = TIMEOUT

    @After
    fun tearDown() = exo.release()

    private fun TestScope.start() {
        exo.addListener(IdleStop(exo, backgroundScope, { timeoutMillis }) { timeouts++ })
        exo.setMediaItems(listOf(TestData.song("a").toMediaItem()))
        exo.prepare()
    }

    private fun play() {
        exo.play()
        assertThat(exo.playWhenReady).isTrue()
    }

    @Test
    fun `paused for the timeout stops the service once`() = runTest {
        start()
        play()
        exo.pause()

        advanceTimeBy(TIMEOUT - 1)
        assertThat(timeouts).isEqualTo(0)
        advanceTimeBy(2)
        assertThat(timeouts).isEqualTo(1)
        advanceTimeBy(TIMEOUT * 3)
        assertThat(timeouts).isEqualTo(1)
    }

    @Test
    fun `playing again before the timeout disarms it`() = runTest {
        start()
        play()
        exo.pause()
        advanceTimeBy(TIMEOUT / 2)

        play()
        advanceTimeBy(TIMEOUT * 3)

        assertThat(timeouts).isEqualTo(0)
    }

    @Test
    fun `more idle events do not restart the timer`() = runTest {
        start()
        play()
        exo.pause()
        advanceTimeBy(TIMEOUT / 2)

        exo.stop() // Idle again, by another event.
        advanceTimeBy(TIMEOUT / 2 + 1)

        assertThat(timeouts).isEqualTo(1)
    }

    @Test
    fun `the timeout is read when the timer is armed`() = runTest {
        start()
        play()
        timeoutMillis = TIMEOUT * 2
        exo.pause()

        advanceTimeBy(TIMEOUT + 1)
        assertThat(timeouts).isEqualTo(0)
        advanceTimeBy(TIMEOUT)
        assertThat(timeouts).isEqualTo(1)
    }

    @Test
    fun `while playing nothing stops`() = runTest {
        start()
        play()

        advanceTimeBy(TIMEOUT * 3)
        runCurrent()

        assertThat(timeouts).isEqualTo(0)
    }

    private companion object {
        const val TIMEOUT = 60_000L
    }
}
