package com.quantumff.segue.core.playback.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.playback.SeguePlaybackService
import com.quantumff.segue.core.playback.browse.await
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hears Bluetooth audio (A2DP) devices connect, for [BluetoothAutoplay]. From Android 12 the
 * broadcast arrives only with the Nearby devices permission, and it lets the app start its playback
 * service from the background.
 */
@AndroidEntryPoint
class BluetoothAutoplayReceiver : BroadcastReceiver() {
    @Inject
    lateinit var preferences: UserPreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    @Dispatcher(SegueDispatchers.Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
        val audio = context.getSystemService(AudioManager::class.java)
        val autoplay = BluetoothAutoplay(preferences, audio::isMusicActive) {
            withContext(mainDispatcher) { play(context.applicationContext) }
        }
        val pending = goAsync()
        scope.launch {
            try {
                autoplay.onConnectionState(state)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Could not play on Bluetooth connect", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Presses play through a short-lived controller. With nothing queued, the session resumes this
     * device's saved queue (`onPlaybackResumption`). The connection is held until playback starts,
     * so the service is not unbound and destroyed before it goes into the foreground.
     */
    private suspend fun play(context: Context) {
        val token = SessionToken(context, ComponentName(context, SeguePlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        try {
            val controller = future.await()
            controller.play()
            // Nothing to resume never starts playing: give up then.
            withTimeoutOrNull(START_TIMEOUT_MILLIS) { controller.awaitPlaying() }
        } finally {
            MediaController.releaseFuture(future)
        }
    }

    private suspend fun MediaController.awaitPlaying() {
        if (isPlaying) return
        suspendCancellableCoroutine { continuation ->
            // Released with the controller, so the listener is never removed here.
            addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying && continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
        }
    }

    private companion object {
        const val TAG = "BluetoothAutoplay"
        const val START_TIMEOUT_MILLIS = 10_000L
    }
}
