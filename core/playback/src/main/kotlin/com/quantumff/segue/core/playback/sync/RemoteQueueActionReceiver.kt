package com.quantumff.segue.core.playback.sync

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "Continue from <device>" notification's Continue, and its dismissal ([RemoteQueueNotifier]).
 * The notification's broadcast comes with a temporary allowance to start a foreground service, so
 * the playback service can start playing from the background.
 */
@AndroidEntryPoint
class RemoteQueueActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var playback: PlaybackController

    @Inject
    lateinit var queueSync: QueueSyncEngine

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CONTINUE -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        if (!playback.continueRemoteQueue()) Log.w(TAG, "Could not continue the other device's queue")
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_DISMISS -> queueSync.dismissOffer()
        }
    }

    companion object {
        private const val TAG = "RemoteQueueAction"
        private const val ACTION_CONTINUE = "com.quantumff.segue.remotequeue.CONTINUE"
        private const val ACTION_DISMISS = "com.quantumff.segue.remotequeue.DISMISS"

        internal fun continueQueue(context: Context): PendingIntent =
            broadcast(context, ACTION_CONTINUE, requestCode = 1)

        internal fun dismiss(context: Context): PendingIntent = broadcast(context, ACTION_DISMISS, requestCode = 2)

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, RemoteQueueActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
