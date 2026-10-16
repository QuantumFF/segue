package com.quantumff.segue.core.designsystem.art

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.quantumff.segue.core.designsystem.theme.clampHsl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colors derived from one piece of album art (DESIGN.md §2.1, "Art color"). */
@Immutable
data class ArtColors(
    /** Hero and Now Playing tint on dark themes. */
    val tintDark: Color,
    /** Hero and Now Playing tint on the light theme. */
    val tintLight: Color,
    /** The "Album art" accent on dark themes. */
    val accentDark: Color,
    /** The "Album art" accent on the light theme. */
    val accentLight: Color,
) {
    fun tint(isDark: Boolean): Color = if (isDark) tintDark else tintLight

    fun accent(isDark: Boolean): Color = if (isDark) accentDark else accentLight

    companion object {
        /** Clamps one seed color into the tint and accent ranges. */
        fun fromSeed(seed: Color): ArtColors = ArtColors(
            tintDark = seed.clampHsl(maxSaturation = TINT_MAX_SATURATION, lightness = 0.18f..0.30f),
            tintLight = seed.clampHsl(maxSaturation = TINT_MAX_SATURATION, lightness = 0.82f..0.90f),
            accentDark = seed.clampHsl(maxSaturation = 1f, lightness = 0.55f..0.70f),
            accentLight = seed.clampHsl(maxSaturation = 1f, lightness = 0.35f..0.45f),
        )

        private const val TINT_MAX_SATURATION = 0.55f
    }
}

/**
 * Extracts [ArtColors] from the 32px pre-blur bitmap (see [PreBlurTransformation]) with
 * androidx.palette, off the main thread, and caches the result per `coverArtId`.
 *
 * Keep one instance per process. Callers fall back to `surfaceContainerHigh` when the result is null.
 */
class ArtColorExtractor(
    maxEntries: Int = DEFAULT_CACHE_SIZE,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val cache = LruCache<String, ArtColors>(maxEntries)

    /** The cached colors for [coverArtId], if they were extracted before. */
    fun cached(coverArtId: String): ArtColors? = cache[coverArtId]

    /** Returns cached colors, or runs Palette on [bitmap] and caches the result. */
    suspend fun extract(coverArtId: String, bitmap: Bitmap): ArtColors? {
        cache[coverArtId]?.let { return it }
        val seed = withContext(dispatcher) { seedColor(bitmap) } ?: return null
        return ArtColors.fromSeed(seed).also { cache.put(coverArtId, it) }
    }

    private fun seedColor(bitmap: Bitmap): Color? {
        val palette = Palette.from(bitmap)
            .maximumColorCount(PALETTE_COLORS)
            .clearFilters()
            .generate()
        // Vibrant, then dominant, then muted.
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        return swatch?.rgb?.let(::Color)
    }

    private companion object {
        const val DEFAULT_CACHE_SIZE = 200
        const val PALETTE_COLORS = 16
    }
}
