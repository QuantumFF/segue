package com.quantumff.segue.feature.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.quantumff.segue.core.designsystem.art.BlurredCover
import com.quantumff.segue.core.designsystem.art.CoverArtRequest
import com.quantumff.segue.core.designsystem.art.loadBlurredCover
import com.quantumff.segue.core.playback.PlaybackController
import com.quantumff.segue.core.playback.PlaybackIntents
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun playbackController(): PlaybackController

    fun imageLoader(): ImageLoader

    fun stateStore(): WidgetStateStore
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.widgetEntryPoint().playbackController().togglePlayPause()
}

internal class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.widgetEntryPoint().playbackController().skipToNext()
}

internal class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.widgetEntryPoint().playbackController().skipToPrevious()
}

/** The app, on Now Playing when something is queued. */
internal fun openAppIntent(context: Context, nowPlaying: Boolean): Intent {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    return launch.apply {
        if (nowPlaying) action = PlaybackIntents.ACTION_OPEN_NOW_PLAYING
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

/** One cover as the widgets draw it. Each part is null when it failed to load. */
internal class WidgetArt(
    val coverArtId: String?,
    /** The cover at widget size. */
    val cover: Bitmap?,
    /** The pre-blurred cover the widget draws behind everything (DESIGN.md §6), and its colors. */
    val backdrop: BlurredCover?,
) {
    companion object {
        val None = WidgetArt(coverArtId = null, cover = null, backdrop = null)
    }
}

/** The cover, its blurred backdrop and its colors, from the app's image caches when they are there. */
internal suspend fun loadWidgetArt(context: Context, coverArtId: String?, size: Int): WidgetArt {
    if (coverArtId == null) return WidgetArt.None
    val imageLoader = context.widgetEntryPoint().imageLoader()
    val request = ImageRequest.Builder(context)
        .data(CoverArtRequest(coverArtId, size))
        .size(size)
        // Widgets draw through RemoteViews, which needs a software bitmap.
        .allowHardware(false)
        .build()
    val cover = (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    return WidgetArt(coverArtId, cover, loadBlurredCover(context, imageLoader, coverArtId))
}
