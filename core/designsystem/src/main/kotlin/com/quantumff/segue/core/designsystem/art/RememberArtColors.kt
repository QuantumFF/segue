package com.quantumff.segue.core.designsystem.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import com.quantumff.segue.core.designsystem.theme.SegueTheme

/** One extractor per process, so its 200-entry LRU is shared by every screen and the widgets (DESIGN.md §2.1). */
val SharedArtColorExtractor = ArtColorExtractor()

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
        val request = ImageRequest.Builder(context)
            .data(CoverArtRequest(coverArtId, CoverArtSize.BLUR_SOURCE))
            .size(CoverArtSize.BLUR_SOURCE)
            .transformations(PreBlurTransformation())
            .memoryCacheKey("blur:$coverArtId")
            // Palette reads pixels, which hardware bitmaps do not allow.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@produceState
        value = SharedArtColorExtractor.extract(coverArtId, result.image.toBitmap())
    }
    return colors
}

/** The hero tint for [coverArtId] in the current theme, or null (callers fall back to `surfaceContainerHigh`). */
@Composable
fun rememberHeroTint(coverArtId: String?): Color? = rememberArtColors(coverArtId)?.tint(SegueTheme.isDark)
