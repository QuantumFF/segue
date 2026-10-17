package com.quantumff.segue.core.designsystem.art

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/**
 * What image requests carry for cover art: the art ID and the `size` bucket for `getCoverArt`.
 *
 * The network layer registers a Coil `Mapper<CoverArtRequest, Uri>` that builds the signed
 * `getCoverArt?id=…&size=…` URL, so UI code never sees server URLs or credentials. Until that
 * mapper exists the request fails and the placeholder stays, which is the designed failure mode.
 */
@Immutable
data class CoverArtRequest(val coverArtId: String, val size: Int)

/** Cover sizes (DESIGN.md §6). A fixed bucket set keeps Navidrome's resize cache hot. */
object CoverArtSize {
    /** The `size` values the app ever requests. */
    val buckets: List<Int> = listOf(128, 256, 512, 1024)

    /** Blur and palette source: the row-size entry, so it costs no extra request. */
    const val BLUR_SOURCE = 128

    private const val TOLERANCE = 0.9f

    /** The smallest bucket that is at least 0.9 × [displayPx]; the largest bucket otherwise. */
    fun bucketForPixels(displayPx: Float): Int = buckets.firstOrNull { it >= displayPx * TOLERANCE } ?: buckets.last()

    /** [bucketForPixels] for a size in dp. */
    fun bucketFor(display: Dp, density: Density): Int = with(density) { bucketForPixels(display.toPx()) }
}

/**
 * Album or artist art. The placeholder (`surfaceContainerHigh` plus a muted icon) is drawn under
 * the image and stays when loading fails: never a broken-image icon (DESIGN.md §5.8).
 *
 * @param displaySize the size the art is drawn at; picks the `size` bucket for the request.
 * @param crossfade crossfade in lists (`medium`); off for Now Playing art, which pages itself.
 */
@Composable
fun CoverArt(
    coverArtId: String?,
    displaySize: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = SegueShapes.sm,
    placeholderIcon: ImageVector = SegueIcons.Disc3,
    crossfade: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholderIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.icon),
        )
        if (coverArtId != null && !LocalInspectionMode.current) {
            val context = LocalContext.current
            val bucket = CoverArtSize.bucketFor(displaySize, LocalDensity.current)
            val request = remember(context, coverArtId, bucket, crossfade) {
                ImageRequest.Builder(context)
                    .data(CoverArtRequest(coverArtId, bucket))
                    .size(bucket)
                    .crossfade(if (crossfade) SegueMotion.MEDIUM_MS else 0)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The request behind every pre-blurred bitmap of [coverArtId], so they all share `blur:<coverArtId>`. */
fun blurredArtRequest(context: Context, coverArtId: String): ImageRequest.Builder = ImageRequest.Builder(context)
    .data(CoverArtRequest(coverArtId, CoverArtSize.BLUR_SOURCE))
    .size(CoverArtSize.BLUR_SOURCE)
    .transformations(PreBlurTransformation())
    .memoryCacheKey("blur:$coverArtId")

/**
 * Pre-blurred art backdrop (DESIGN.md §6). Requests the 128 bucket (shared with rows), blurs a
 * 32px copy in a Coil transformation, and draws it scaled with low filter quality. No live blur.
 * Memory-cached under `blur:<coverArtId>`.
 */
@Composable
fun BlurredArt(coverArtId: String?, modifier: Modifier = Modifier, alpha: Float = 1f) {
    if (coverArtId == null || LocalInspectionMode.current) return
    val context = LocalContext.current
    val request = remember(context, coverArtId) {
        blurredArtRequest(context, coverArtId).crossfade(SegueMotion.MEDIUM_MS).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        alpha = alpha,
        modifier = modifier,
    )
}
