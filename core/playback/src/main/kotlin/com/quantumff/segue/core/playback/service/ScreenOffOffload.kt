package com.quantumff.segue.core.playback.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences

/**
 * Audio offload only while the screen is off, following the Media3 offload guide: offload hands
 * decoding to the DSP so the CPU can sleep, but makes position updates coarse and seeking slower,
 * which only matters while someone is looking. Gapless support is required so album transitions
 * stay seamless; devices without gapless offload just keep decoding on the CPU.
 *
 * Offload skips audio processors. Plain ReplayGain is player volume, which offload honors; boosted
 * ReplayGain needs its processor, so [processingRequired] keeps offload off for as long as it is
 * set, whatever the screen does. It flips once per setting change, never per track, so gapless
 * transitions do not cross between offload and PCM.
 */
class ScreenOffOffload(private val context: Context, private val player: Player) {
    private var screenOff = false

    /** True while an audio processor must run (ReplayGain boost): no offload then. */
    var processingRequired = false
        set(value) {
            field = value
            update()
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_SCREEN_ON -> screenOff = false
                else -> return
            }
            update()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenOff = !context.getSystemService(PowerManager::class.java).isInteractive
        update()
    }

    fun stop() {
        context.unregisterReceiver(receiver)
    }

    private fun update() {
        val enabled = screenOff && !processingRequired
        val preferences = if (enabled) OFFLOAD else AudioOffloadPreferences.DEFAULT
        val parameters = player.trackSelectionParameters
        if (parameters.audioOffloadPreferences == preferences) return
        Log.d(
            TAG,
            "Audio offload ${if (enabled) "on" else "off"} (screenOff=$screenOff, processing=$processingRequired)",
        )
        player.trackSelectionParameters = parameters.buildUpon().setAudioOffloadPreferences(preferences).build()
    }

    private companion object {
        const val TAG = "SegueOffload"

        val OFFLOAD: AudioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .setIsGaplessSupportRequired(true)
            .build()
    }
}
