package com.quantumff.segue.core.designsystem.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.quantumff.segue.core.designsystem.art.ArtColors

/**
 * Segue's theme (DESIGN.md §2). Neutrals come from [SegueThemeConfig.themeMode]; the accent comes
 * from [SegueThemeConfig.accentSource].
 *
 * @param artColors colors of the current track's art. Used when the accent source is
 *   [AccentSource.AlbumArt]; the accent animates to the new color over `long` (300ms).
 * @param systemDark whether the system is in dark mode. Previews and tests override it.
 */
@Composable
fun SegueTheme(
    config: SegueThemeConfig = SegueThemeConfig(),
    artColors: ArtColors? = null,
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = config.themeMode.palette(systemDark)
    val context = LocalContext.current
    val accent = when (config.accentSource) {
        AccentSource.Fixed -> config.accentPreset.accentColors(palette.isDark)

        AccentSource.MaterialYou ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamic = remember(context, palette.isDark) {
                    if (palette.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                dynamic.accentColors()
            } else {
                config.accentPreset.accentColors(palette.isDark)
            }

        AccentSource.AlbumArt -> {
            val target = artColors?.accent(palette.isDark)
                ?: config.accentPreset.accentColors(palette.isDark).primary
            val animated by animateColorAsState(
                targetValue = target,
                animationSpec = SegueMotion.longEnter(),
                label = "artAccent",
            )
            // Art accents can be light (L up to 0.70 on dark), so pick readable content.
            AccentColors(primary = animated, onPrimary = animated.readableContent())
        }
    }
    val colorScheme = remember(palette, accent) { palette.colorScheme(accent) }
    val extended = remember(palette) { palette.extendedColors() }
    val reduceMotion = config.reduceMotion || remember(context) { context.systemAnimationsDisabled() }

    CompositionLocalProvider(
        LocalSegueColors provides extended,
        LocalDarkTheme provides palette.isDark,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SegueTypography,
            shapes = SegueMaterialShapes,
            content = content,
        )
    }
}

/** Accessors for Segue-specific theme values. Colors, type and shapes use [MaterialTheme]. */
object SegueTheme {
    val colors: SegueExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalSegueColors.current

    /** True for the Dark and Black palettes. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalDarkTheme.current
}

private val LocalDarkTheme = staticCompositionLocalOf { true }

private fun AccentPreset.accentColors(isDark: Boolean) = AccentColors(primary = if (isDark) dark else light)

/** Material You: only the accent roles, never the neutrals (DESIGN.md §2.1). */
private fun ColorScheme.accentColors() = AccentColors(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    tertiary = tertiary,
    onTertiary = onTertiary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
)
