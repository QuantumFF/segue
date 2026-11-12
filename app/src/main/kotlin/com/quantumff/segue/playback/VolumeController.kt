package com.quantumff.segue.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * The device's media volume (`STREAM_MUSIC`), for the player bar's slider: [volume] follows it,
 * volume keys included, and [setVolume] moves it to the nearest step without the system volume panel.
 */
@Singleton
class VolumeController @Inject constructor(@ApplicationContext private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** Our own changes, read back at once rather than waiting for the system's broadcast. */
    private val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    /** Listens to the system only while collected. */
    val volume: Flow<MediaVolume> = merge(systemChanges(), localChanges)
        .onStart { emit(Unit) }
        .map { read() }
        .distinctUntilChanged()

    fun setVolume(fraction: Float) {
        val min = audio.getStreamMinVolume(STREAM)
        val max = audio.getStreamMaxVolume(STREAM)
        val index = min + (fraction.coerceIn(0f, 1f) * (max - min)).roundToInt()
        audio.setStreamVolume(STREAM, index, 0)
        localChanges.tryEmit(Unit)
    }

    private fun read(): MediaVolume {
        val min = audio.getStreamMinVolume(STREAM)
        val max = audio.getStreamMaxVolume(STREAM)
        if (max <= min) return MediaVolume.Full
        val fraction = (audio.getStreamVolume(STREAM) - min).toFloat() / (max - min)
        return MediaVolume(fraction = fraction.coerceIn(0f, 1f), steps = (max - min - 1).coerceAtLeast(0))
    }

    /**
     * The system's volume-changed broadcast. It is not in the public SDK, but there is no other
     * callback for one stream's volume.
     */
    private fun systemChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_STREAM_TYPE, STREAM) == STREAM) trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}

/**
 * The device's media volume as a slider shows it: [fraction] of the way from the lowest to the
 * highest step, and [steps] the number of stops between the two ends (`Slider`'s `steps`).
 */
@Immutable
data class MediaVolume(val fraction: Float, val steps: Int) {
    companion object {
        val Full = MediaVolume(fraction = 1f, steps = 0)
    }
}

/** The player bar's volume slider: the media volume, and what moving the slider does. */
@Immutable
class ShellVolume(val level: StateFlow<MediaVolume>, val onChange: (Float) -> Unit) {
    companion object {
        /** Full and fixed: for previews and tests that do not touch the volume. */
        val Fixed = ShellVolume(MutableStateFlow(MediaVolume.Full), onChange = {})
    }
}
