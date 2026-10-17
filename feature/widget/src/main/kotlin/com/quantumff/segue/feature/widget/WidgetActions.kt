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
import coil3.request.transformations
import coil3.toBitmap
import com.quantumff.segue.core.designsystem.art.ArtColors
import com.quantumff.segue.core.designsystem.art.CoverArtRequest
import com.quantumff.segue.core.designsystem.art.CoverArtSize
import com.quantumff.segue.core.designsystem.art.PreBlurTransformation
import com.quantumff.segue.core.designsystem.art.SharedArtColorExtractor
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
    /** The pre-blurred 32px cover the widget draws behind everything (DESIGN.md §6). */
    val backdrop: Bitmap?,
    val colors: ArtColors?,
)

/**
 * The cover, its blurred backdrop and its colors, from the app's image cache when they are there.
 * The backdrop shares the app's `blur:<coverArtId>` cache entry, and the colors its extractor.
 */
internal suspend fun loadWidgetArt(context: Context, coverArtId: String?, size: Int): WidgetArt {
    if (coverArtId == null) return WidgetArt(null, null, null, null)
    val cover = context.loadBitmap(CoverArtRequest(coverArtId, size), size)
    val backdrop = context.loadBitmap(CoverArtRequest(coverArtId, CoverArtSize.BLUR_SOURCE), CoverArtSize.BLUR_SOURCE) {
        transformations(PreBlurTransformation()).memoryCacheKey("blur:$coverArtId")
    }
    val colors = backdrop?.let { SharedArtColorExtractor.extract(coverArtId, it) }
    return WidgetArt(coverArtId, cover, backdrop, colors)
}

private suspend fun Context.loadBitmap(
    data: CoverArtRequest,
    size: Int,
    configure: ImageRequest.Builder.() -> ImageRequest.Builder = { this },
): Bitmap? {
    val request = ImageRequest.Builder(this)
        .data(data)
        .size(size)
        .configure()
        // Widgets draw through RemoteViews, which needs a software bitmap; Palette reads pixels too.
        .allowHardware(false)
        .build()
    val result = widgetEntryPoint().imageLoader().execute(request)
    return (result as? SuccessResult)?.image?.toBitmap()
}
