package com.quantumff.segue.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.playback.nowplaying.NowPlayingListener
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the playback service publishes a change (track, play/pause, shuffle).
 * There is no periodic update: the widget providers declare `updatePeriodMillis="0"`. Bursts of
 * events collapse into one redraw, and nothing is drawn when no widget is placed.
 */
@Singleton
internal class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WidgetStateStore,
    @ApplicationScope scope: CoroutineScope,
) : NowPlayingListener {
    private val latest = MutableStateFlow<NowPlayingSnapshot?>(null)

    init {
        scope.launch {
            latest.filterNotNull().collectLatest { snapshot ->
                try {
                    store.write(snapshot)
                    widgets.forEach { widget -> if (isPlaced(widget)) widget.updateAll(context) }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.w(TAG, "Widget update failed", e)
                }
            }
        }
    }

    override fun onNowPlayingChanged(snapshot: NowPlayingSnapshot) {
        latest.value = snapshot
    }

    private suspend fun isPlaced(widget: GlanceAppWidget) =
        GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isNotEmpty()

    private companion object {
        const val TAG = "WidgetUpdater"
        val widgets: List<GlanceAppWidget> = listOf(NowPlayingStripWidget(), NowPlayingCardWidget())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface WidgetModule {
    @Binds
    @IntoSet
    fun widgetUpdater(impl: WidgetUpdater): NowPlayingListener
}
