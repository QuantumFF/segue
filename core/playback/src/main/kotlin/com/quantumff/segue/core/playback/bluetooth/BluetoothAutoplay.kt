package com.quantumff.segue.core.playback.bluetooth

import android.bluetooth.BluetoothProfile
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * "Play when Bluetooth connects" ([UserPreferences.playOnBluetoothConnect]): a Bluetooth audio
 * device connecting resumes the queue, unless the setting is off or something is already playing,
 * in another app or in Segue.
 */
internal class BluetoothAutoplay(
    private val preferences: UserPreferencesRepository,
    /** Whether any app is playing music right now. */
    private val isMusicActive: () -> Boolean,
    /** Plays the queue: the player's, or this device's saved queue when nothing is queued. */
    private val resume: suspend () -> Unit,
) {
    /** A Bluetooth audio device's connection moved to [state], a [BluetoothProfile] `STATE_` value. */
    suspend fun onConnectionState(state: Int) {
        if (state != BluetoothProfile.STATE_CONNECTED) return
        if (!preferences.preferences.first().playOnBluetoothConnect) return
        if (isMusicActive()) return
        resume()
    }
}
