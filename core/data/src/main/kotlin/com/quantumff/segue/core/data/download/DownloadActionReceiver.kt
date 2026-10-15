package com.quantumff.segue.core.data.download

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quantumff.segue.core.common.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The download notification's "Pause all" and "Cancel" buttons. Pausing holds every unfinished
 * download (resume from the Downloads screen); cancelling unpins every unfinished pin. Either way
 * the worker finds nothing left to claim and ends, taking the notification with it.
 */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var downloads: DownloadRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_PAUSE_ALL && action != ACTION_CANCEL_ALL) return
        val pending = goAsync()
        scope.launch {
            try {
                if (action == ACTION_PAUSE_ALL) downloads.pauseAll() else downloads.cancelAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_PAUSE_ALL = "com.quantumff.segue.download.PAUSE_ALL"
        private const val ACTION_CANCEL_ALL = "com.quantumff.segue.download.CANCEL_ALL"

        internal fun pauseAll(context: Context): PendingIntent = broadcast(context, ACTION_PAUSE_ALL, requestCode = 1)

        internal fun cancelAll(context: Context): PendingIntent = broadcast(context, ACTION_CANCEL_ALL, requestCode = 2)

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, DownloadActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
