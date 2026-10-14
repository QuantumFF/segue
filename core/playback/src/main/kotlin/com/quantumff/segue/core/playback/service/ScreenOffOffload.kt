package com.quantumff.segue.core.playback.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences

/**
 * Audio offload only while the screen is off, following the Media3 offload guide: offload hands
 * decoding to the DSP so the CPU can sleep, but makes position updates coarse and seeking slower,
 * which only matters while someone is looking. Gapless support is required so album transitions
 * stay seamless; devices without gapless offload just keep decoding on the CPU.
 *
 * ReplayGain is applied as player volume, which offload honors (see ReplayGainVolume).
 */
class ScreenOffOffload(private val context: Context, private val player: Player) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> setOffload(true)
                Intent.ACTION_SCREEN_ON -> setOffload(false)
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setOffload(!context.getSystemService(PowerManager::class.java).isInteractive)
    }

    fun stop() {
        context.unregisterReceiver(receiver)
    }

    private fun setOffload(enabled: Boolean) {
        val preferences = if (enabled) OFFLOAD else AudioOffloadPreferences.DEFAULT
        val parameters = player.trackSelectionParameters
        if (parameters.audioOffloadPreferences == preferences) return
        player.trackSelectionParameters = parameters.buildUpon().setAudioOffloadPreferences(preferences).build()
    }

    private companion object {
        val OFFLOAD: AudioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .setIsGaplessSupportRequired(true)
            .build()
    }
}
