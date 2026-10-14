package com.quantumff.segue.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The neutral palette from docs/DESIGN.md §2.1. Neutrals are pure gray and never tinted by
 * dynamic color, so every [ColorScheme] is built from one of these plus an [AccentColors].
 */
@Immutable
internal data class NeutralPalette(
    val isDark: Boolean,
    val background: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val success: Color,
    val scrim: Color,
    /** How strongly `primaryContainer` mixes the accent into the surface. */
    val containerAccentAlpha: Float,
)

internal object SeguePalettes {
    val Dark = NeutralPalette(
        isDark = true,
        background = Color(0xFF0C0C0C),
        surfaceContainerLowest = Color(0xFF080808),
        surfaceContainerLow = Color(0xFF101010),
        surfaceContainer = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF1C1C1C),
        surfaceContainerHighest = Color(0xFF242424),
        onSurface = Color(0xFFE1E1E1),
        onSurfaceVariant = Color(0xFF969696),
        outline = Color(0xFF3B3B3B),
        outlineVariant = Color.White.copy(alpha = 0.10f),
        error = Color(0xFFE5484D),
        success = Color(0xFF3DCC5C),
        scrim = Color.Black.copy(alpha = 0.60f),
        containerAccentAlpha = 0.16f,
    )

    val Black = NeutralPalette(
        isDark = true,
        background = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF050505),
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF1A1A1A),
        onSurface = Color(0xFFE1E1E1),
        onSurfaceVariant = Color(0xFF8C8C8C),
        outline = Color(0xFF2E2E2E),
        outlineVariant = Color.White.copy(alpha = 0.08f),
        error = Color(0xFFE5484D),
        success = Color(0xFF3DCC5C),
        scrim = Color.Black.copy(alpha = 0.70f),
        containerAccentAlpha = 0.16f,
    )

    val Light = NeutralPalette(
        isDark = false,
        background = Color(0xFFF5F5F5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFEFEF),
        surfaceContainer = Color(0xFFE9E9E9),
        surfaceContainerHigh = Color(0xFFE1E1E1),
        surfaceContainerHighest = Color(0xFFD8D8D8),
        onSurface = Color(0xFF191919),
        onSurfaceVariant = Color(0xFF505050),
        outline = Color(0xFFBDBDBD),
        outlineVariant = Color.Black.copy(alpha = 0.06f),
        error = Color(0xFFD93025),
        success = Color(0xFF1E8E3E),
        scrim = Color.Black.copy(alpha = 0.40f),
        containerAccentAlpha = 0.12f,
    )
}

/** The accent roles that may vary (fixed preset, Material You, or album art). */
@Immutable
data class AccentColors(
    val primary: Color,
    val onPrimary: Color = Color.White,
    val secondary: Color = primary,
    val onSecondary: Color = onPrimary,
    val tertiary: Color = primary,
    val onTertiary: Color = onPrimary,
    /** Null means "primary @ 16% (12% light) over the surface", per DESIGN.md. */
    val primaryContainer: Color? = null,
    val onPrimaryContainer: Color? = null,
    val secondaryContainer: Color? = null,
    val onSecondaryContainer: Color? = null,
    val tertiaryContainer: Color? = null,
    val onTertiaryContainer: Color? = null,
)

/** Colors outside the M3 [ColorScheme]. Read with `SegueTheme.colors`. */
@Immutable
data class SegueExtendedColors(
    /** Download done, success states. */
    val success: Color,
    /** Primary text and icons drawn over album art (Now Playing, feature carousel). */
    val onArt: Color,
    /** Secondary text over album art. */
    val onArtMuted: Color,
    /** Gradient end for art backdrops. */
    val artShade: Color,
)

internal val LocalSegueColors = staticCompositionLocalOf {
    SegueExtendedColors(
        success = SeguePalettes.Dark.success,
        onArt = Color.White,
        onArtMuted = Color.White.copy(alpha = ON_ART_MUTED_ALPHA),
        artShade = Color.Black,
    )
}

private const val ON_ART_MUTED_ALPHA = 0.70f

internal fun NeutralPalette.extendedColors() = SegueExtendedColors(
    success = success,
    onArt = Color.White,
    onArtMuted = Color.White.copy(alpha = ON_ART_MUTED_ALPHA),
    artShade = Color.Black,
)

/** Builds the M3 scheme: neutrals from [this], accent roles from [accent]. Tonal elevation is unused. */
internal fun NeutralPalette.colorScheme(accent: AccentColors): ColorScheme {
    val tintedContainer = accent.primary.copy(alpha = containerAccentAlpha).compositeOver(background)
    val errorContainer = error.copy(alpha = containerAccentAlpha).compositeOver(background)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer ?: tintedContainer,
        onPrimaryContainer = accent.onPrimaryContainer ?: onSurface,
        inversePrimary = accent.primary,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        // M3 uses secondaryContainer for the navigation indicator pill: primary @ 16%.
        secondaryContainer = accent.secondaryContainer ?: tintedContainer,
        onSecondaryContainer = accent.onSecondaryContainer ?: onSurface,
        tertiary = accent.tertiary,
        onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer ?: tintedContainer,
        onTertiaryContainer = accent.onTertiaryContainer ?: onSurface,
        background = background,
        onBackground = onSurface,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = surfaceContainerHighest,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = accent.primary,
        // Snackbars: a light chip on dark themes, a dark chip on the light theme.
        inverseSurface = if (isDark) onSurface else SeguePalettes.Dark.surfaceContainerHigh,
        inverseOnSurface = if (isDark) SeguePalettes.Light.onSurface else SeguePalettes.Dark.onSurface,
        error = error,
        onError = Color.White,
        errorContainer = errorContainer,
        onErrorContainer = onSurface,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceContainerHighest,
        surfaceDim = background,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}
