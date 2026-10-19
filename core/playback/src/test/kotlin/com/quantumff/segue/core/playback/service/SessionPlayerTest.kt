package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionPlayerTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val player = SeguePlayer(exo)
    private var outsideApp = true
    private var checks = 0
    private var serverAnswer = CompletableDeferred<Unit>()

    @After
    fun tearDown() = exo.release()

    private fun TestScope.sessionPlayer(): SessionPlayer {
        exo.setMediaItems(listOf(TestData.song("local").toMediaItem()))
        exo.prepare()
        return SessionPlayer(player, backgroundScope, { outsideApp }) {
            checks++
            serverAnswer.await()
            exo.setMediaItems(listOf(TestData.song("remote").toMediaItem()))
        }
    }

    @Test
    fun `in the app play is immediate and nothing is checked`() = runTest {
        outsideApp = false
        val session = sessionPlayer()

        session.play()

        assertThat(exo.playWhenReady).isTrue()
        assertThat(checks).isEqualTo(0)
    }

    @Test
    fun `outside the app play waits for the check and then plays what it queued`() = runTest {
        val session = sessionPlayer()

        session.play()
        runCurrent()
        assertThat(exo.playWhenReady).isFalse()

        serverAnswer.complete(Unit)
        runCurrent()
        assertThat(exo.playWhenReady).isTrue()
        assertThat(exo.currentMediaItem?.mediaId).isEqualTo("remote")
    }

    @Test
    fun `a pause during the check wins, and the check still finishes`() = runTest {
        val session = sessionPlayer()

        session.play()
        runCurrent()
        session.pause()
        serverAnswer.complete(Unit)
        runCurrent()

        assertThat(exo.playWhenReady).isFalse()
        assertThat(exo.currentMediaItem?.mediaId).isEqualTo("remote")
    }

    @Test
    fun `play pressed again during the check checks once`() = runTest {
        val session = sessionPlayer()

        session.play()
        runCurrent()
        session.pause()
        session.playWhenReady = true
        serverAnswer.complete(Unit)
        runCurrent()

        assertThat(checks).isEqualTo(1)
        assertThat(exo.playWhenReady).isTrue()
    }

    @Test
    fun `nothing is checked while already playing`() = runTest {
        val session = sessionPlayer()
        exo.play()

        session.play()

        assertThat(checks).isEqualTo(0)
    }
}
