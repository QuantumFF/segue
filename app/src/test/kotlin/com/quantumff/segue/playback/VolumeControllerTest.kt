package com.quantumff.segue.playback

import android.app.Application
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VolumeControllerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val controller = VolumeController(context)

    @Before
    fun tenSteps() {
        // Robolectric's music stream: 0 to 15 by default; ten steps keeps the numbers round.
        shadowOf(audio).setStreamMaxVolume(10)
    }

    @Test
    fun `reads the media volume as a fraction with the device's steps`() = runTest {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)

        assertThat(controller.volume.first()).isEqualTo(MediaVolume(fraction = 0.7f, steps = 9))
    }

    @Test
    fun `setting a fraction sets the nearest media volume step`() = runTest {
        controller.setVolume(0.42f)

        assertThat(audio.getStreamVolume(AudioManager.STREAM_MUSIC)).isEqualTo(4)
        assertThat(controller.volume.first().fraction).isWithin(0.001f).of(0.4f)
    }
}
