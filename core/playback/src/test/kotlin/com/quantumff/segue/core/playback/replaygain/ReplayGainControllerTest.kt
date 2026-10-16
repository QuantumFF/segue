package com.quantumff.segue.core.playback.replaygain

import android.content.Context
import android.content.Intent
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.ScreenOffOffload
import com.quantumff.segue.core.testing.data.TestData
import kotlin.math.log10
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** Player volume, the processor and the offload switch follow the settings and the current track. */
@RunWith(RobolectricTestRunner::class)
class ReplayGainControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exo = ExoPlayer.Builder(context).build()
    private val processor = ReplayGainProcessor()
    private val offload = ScreenOffOffload(context, exo)
    private val controller = ReplayGainController(exo, processor, offload)
    private val track = ReplayGainSettings(ReplayGainMode.Track, ReplayGainTarget.ReplayGain2, boost = false)

    init {
        offload.start()
        exo.addListener(controller)
        exo.setMediaItems(
            listOf(
                TestData.song("quiet").copy(replayGain = ReplayGain(trackGain = 4f)).toMediaItem(),
                TestData.song("loud").copy(replayGain = ReplayGain(trackGain = -6.0206f)).toMediaItem(),
            ),
        )
    }

    @After
    fun tearDown() {
        offload.stop()
        exo.release()
    }

    private fun db(linear: Float) = (20 * log10(linear)).toDouble()

    private fun offloadMode() = exo.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode

    private fun screenOff() {
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF).setPackage(context.packageName))
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `without boost the current track's gain is player volume`() {
        controller.update(track)
        assertThat(exo.volume).isEqualTo(1f) // +4 dB cannot be reached by volume.

        exo.seekToNextMediaItem()
        ShadowLooper.idleMainLooper()
        assertThat(db(exo.volume)).isWithin(0.01).of(-6.0206)
    }

    @Test
    fun `the target and mode apply immediately`() {
        controller.update(track)
        controller.update(track.copy(target = ReplayGainTarget.Ebu))
        assertThat(db(exo.volume)).isWithin(0.01).of(-1.0) // +4 - 5

        controller.update(track.copy(mode = ReplayGainMode.Off))
        assertThat(exo.volume).isEqualTo(1f)
    }

    @Test
    fun `with boost the processor takes over and volume stays at unity`() {
        controller.update(track.copy(boost = true))
        exo.seekToNextMediaItem()
        ShadowLooper.idleMainLooper()
        assertThat(exo.volume).isEqualTo(1f)
        assertThat(processor.settings).isEqualTo(track.copy(boost = true))
    }

    @Test
    fun `boost keeps audio offload off while the screen is off, and releasing it restores offload`() {
        screenOff()
        controller.update(track)
        assertThat(offloadMode()).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)

        controller.update(track.copy(boost = true))
        assertThat(offloadMode()).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)

        controller.update(track)
        assertThat(offloadMode()).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
    }
}
