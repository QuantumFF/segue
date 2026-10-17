package com.quantumff.segue.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the playback service publishes a change (track, play/pause).
 * There is no periodic update: the widget providers declare `updatePeriodMillis="0"`. Bursts of
 * events collapse into one redraw, and nothing is drawn when no widget is placed.
 *
 * On API 35+ it also gives the widget picker its previews when they are missing: on the first run
 * and after each app update, which clears them.
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            scope.launch {
                try {
                    publishPreviews()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.w(TAG, "Widget previews failed", e)
                }
            }
        }
    }

    override fun onNowPlayingChanged(snapshot: NowPlayingSnapshot) {
        latest.value = snapshot
    }

    private suspend fun isPlaced(widget: GlanceAppWidget) =
        GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isNotEmpty()

    /**
     * Renders each widget's preview for the picker, skipping widgets that already have one (the API
     * is rate limited).
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun publishPreviews() {
        val providers = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        receivers.forEach { receiver ->
            val provider = providers.firstOrNull { it.provider.className == receiver.java.name }
            if (provider != null && provider.generatedPreviewCategories == 0) {
                GlanceAppWidgetManager(context).setWidgetPreviews(receiver)
            }
        }
    }

    private companion object {
        const val TAG = "WidgetUpdater"
        val widgets: List<GlanceAppWidget> = listOf(NowPlayingStripWidget(), NowPlayingCardWidget())
        val receivers: List<KClass<out GlanceAppWidgetReceiver>> =
            listOf(NowPlayingStripWidgetReceiver::class, NowPlayingCardWidgetReceiver::class)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface WidgetModule {
    @Binds
    @IntoSet
    fun widgetUpdater(impl: WidgetUpdater): NowPlayingListener
}
