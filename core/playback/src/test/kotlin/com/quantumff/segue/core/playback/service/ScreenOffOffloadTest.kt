package com.quantumff.segue.core.playback.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/** The offload switch the emulator cannot exercise (it has no compress-offload output). */
@RunWith(RobolectricTestRunner::class)
class ScreenOffOffloadTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exo = ExoPlayer.Builder(context).build()
    private val offload = ScreenOffOffload(context, exo)

    @After
    fun tearDown() {
        offload.stop()
        exo.release()
    }

    private fun preferences() = exo.trackSelectionParameters.audioOffloadPreferences

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun offloadsOnlyWhileTheScreenIsOff() {
        offload.start()
        assertThat(preferences()).isEqualTo(AudioOffloadPreferences.DEFAULT)

        broadcast(Intent.ACTION_SCREEN_OFF)
        assertThat(preferences().audioOffloadMode).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
        // Gapless album transitions: never offload on a DSP that drops gapless metadata.
        assertThat(preferences().isGaplessSupportRequired).isTrue()

        broadcast(Intent.ACTION_SCREEN_ON)
        assertThat(preferences()).isEqualTo(AudioOffloadPreferences.DEFAULT)
    }

    @Test
    fun neverOffloadsWhileAudioProcessingIsRequired() {
        offload.processingRequired = true
        offload.start()

        broadcast(Intent.ACTION_SCREEN_OFF)
        assertThat(preferences()).isEqualTo(AudioOffloadPreferences.DEFAULT)

        offload.processingRequired = false
        assertThat(preferences().audioOffloadMode).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)

        offload.processingRequired = true
        assertThat(preferences()).isEqualTo(AudioOffloadPreferences.DEFAULT)
    }

    @Test
    fun startsOffloadedWhenTheScreenIsAlreadyOff() {
        shadowOf(context.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        offload.start()
        assertThat(preferences().audioOffloadMode).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
    }
}
