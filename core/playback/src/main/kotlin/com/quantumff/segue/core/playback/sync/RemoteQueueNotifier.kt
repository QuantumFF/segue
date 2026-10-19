package com.quantumff.segue.core.playback.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.playback.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "Continue from <device>" notification (docs/adr/0004), on its own low-importance channel:
 * shown while [QueueSyncEngine.notificationOffer] holds a newer queue that play pressed outside
 * the app found. Continue takes it over and plays it ([RemoteQueueActionReceiver]); a tap opens
 * the app, which shows the same offer as the banner; swiping it away dismisses the offer. It goes
 * when the offer does, and a new process clears one left by the last. Call [start] once.
 */
@Singleton
class RemoteQueueNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueSync: QueueSyncEngine,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun start() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.playback_remote_queue_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        scope.launch {
            queueSync.notificationOffer.collect { offer ->
                if (offer == null) manager.cancel(NOTIFICATION_ID) else show(offer)
            }
        }
    }

    private fun show(offer: RemoteQueueOffer) {
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setContentTitle(context.getString(R.string.playback_continue_from, offer.deviceName))
            .setContentText(detail(offer))
            .apply { offer.changedAt?.let { setWhen(it).setShowWhen(true) } }
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setDeleteIntent(RemoteQueueActionReceiver.dismiss(context))
            .addAction(
                0,
                context.getString(R.string.playback_continue),
                RemoteQueueActionReceiver.continueQueue(context),
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun openApp(): PendingIntent? = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL_ID = "remote_queue"
        const val NOTIFICATION_ID = 0x5e9f
        const val MILLIS_PER_SECOND = 1_000L

        /** "Nude · 2:31", as the banner reads without its age; the notification shows the age itself. */
        fun detail(offer: RemoteQueueOffer): String = listOfNotNull(
            offer.currentTitle,
            DateUtils.formatElapsedTime(offer.positionMillis / MILLIS_PER_SECOND).removePrefix("0")
                .takeIf { offer.positionMillis > 0 },
        ).joinToString(" · ")
    }
}
