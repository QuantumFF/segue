package com.quantumff.segue.core.designsystem.art

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.quantumff.segue.core.designsystem.theme.SegueTheme

/** One extractor per process, so its 200-entry LRU is shared by every screen and the widgets (DESIGN.md §2.1). */
private val SharedArtColorExtractor = ArtColorExtractor()

/** A cover's pre-blurred bitmap and its colors (null when Palette found none). */
class BlurredCover(val bitmap: Bitmap, val colors: ArtColors?)

/**
 * Loads [coverArtId]'s pre-blurred bitmap (the same one [BlurredArt] draws) and its colors, or null
 * when the bitmap does not load. For callers outside composition, such as the home-screen widgets.
 */
suspend fun loadBlurredCover(context: Context, imageLoader: ImageLoader, coverArtId: String): BlurredCover? {
    val request = blurredArtRequest(context, coverArtId)
        // Palette reads pixels, which hardware bitmaps do not allow; RemoteViews needs software ones too.
        .allowHardware(false)
        .build()
    val result = imageLoader.execute(request) as? SuccessResult ?: return null
    val bitmap = result.image.toBitmap()
    return BlurredCover(bitmap, SharedArtColorExtractor.extract(coverArtId, bitmap))
}

/**
 * The [ArtColors] of [coverArtId], or null until they are known (and when there is no art).
 * Loads the same pre-blurred 32px bitmap as [BlurredArt] (`blur:<coverArtId>` in the memory cache),
 * so a hero that shows both costs one request. Palette runs off the main thread.
 */
@Composable
fun rememberArtColors(coverArtId: String?): ArtColors? {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val colors by produceState(coverArtId?.let(SharedArtColorExtractor::cached), coverArtId, inspection) {
        if (coverArtId == null || inspection) {
            value = null
            return@produceState
        }
        SharedArtColorExtractor.cached(coverArtId)?.let {
            value = it
            return@produceState
        }
        value = loadBlurredCover(context, SingletonImageLoader.get(context), coverArtId)?.colors
    }
    return colors
}

/** The hero tint for [coverArtId] in the current theme, or null (callers fall back to `surfaceContainerHigh`). */
@Composable
fun rememberHeroTint(coverArtId: String?): Color? = rememberArtColors(coverArtId)?.tint(SegueTheme.isDark)
