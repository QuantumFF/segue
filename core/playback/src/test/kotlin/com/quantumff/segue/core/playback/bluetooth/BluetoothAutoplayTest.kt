package com.quantumff.segue.core.playback.bluetooth

import android.bluetooth.BluetoothProfile
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BluetoothAutoplayTest {
    private val preferences = FakeUserPreferencesRepository()
    private var musicActive = false
    private var resumes = 0
    private val autoplay = BluetoothAutoplay(preferences, { musicActive }) { resumes++ }

    private fun turnOn() = preferences.preferences.value.let {
        preferences.preferences.value = it.copy(playOnBluetoothConnect = true)
    }

    @Test
    fun `a connection resumes the queue when the setting is on`() = runTest {
        turnOn()

        autoplay.onConnectionState(BluetoothProfile.STATE_CONNECTED)

        assertThat(resumes).isEqualTo(1)
    }

    @Test
    fun `nothing plays while the setting is off`() = runTest {
        autoplay.onConnectionState(BluetoothProfile.STATE_CONNECTED)

        assertThat(resumes).isEqualTo(0)
    }

    @Test
    fun `music already playing is left alone`() = runTest {
        turnOn()
        musicActive = true

        autoplay.onConnectionState(BluetoothProfile.STATE_CONNECTED)

        assertThat(resumes).isEqualTo(0)
    }

    @Test
    fun `only a completed connection plays`() = runTest {
        turnOn()

        listOf(
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING,
            BluetoothProfile.STATE_DISCONNECTED,
        ).forEach { autoplay.onConnectionState(it) }

        assertThat(resumes).isEqualTo(0)
    }
}
