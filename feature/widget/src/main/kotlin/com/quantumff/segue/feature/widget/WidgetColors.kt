package com.quantumff.segue.feature.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.quantumff.segue.core.designsystem.art.ArtColors
import com.quantumff.segue.core.designsystem.theme.segueColorScheme

/** The colors drawn over one background. */
internal class WidgetPalette(
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val placeholder: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
) {
    companion object {
        /**
         * Over blurred art, text and icons are always light (DESIGN.md §4.7) and the play button
         * takes the art's accent, white when the art gave no colors. Its icon is black or white,
         * whichever reads on that accent.
         */
        fun overArt(colors: ArtColors?): WidgetPalette {
            val accent = colors?.accentDark ?: Color.White
            val icon = if (accent.luminance() > BLACK_ICON_MIN_LUMINANCE) Color.Black else Color.White
            return WidgetPalette(
                onSurface = ColorProvider(Color.White),
                onSurfaceVariant = ColorProvider(Color.White.copy(alpha = SECONDARY_TEXT_ALPHA)),
                placeholder = ColorProvider(Color.White.copy(alpha = PLACEHOLDER_ALPHA)),
                primary = ColorProvider(accent),
                onPrimary = ColorProvider(icon),
            )
        }

        private const val SECONDARY_TEXT_ALPHA = 0.7f
        private const val PLACEHOLDER_ALPHA = 0.12f

        /** Where black and white have equal contrast; above it, black has more. */
        private const val BLACK_ICON_MIN_LUMINANCE = 0.179f
    }
}

/**
 * Segue's neutrals with Material You accents (DESIGN.md §2.1), resolved for light and dark so the
 * launcher switches them with the system theme.
 */
internal class WidgetColors(context: Context) {
    private val light: ColorScheme = segueColorScheme(context, dark = false)
    private val dark: ColorScheme = segueColorScheme(context, dark = true)

    val glance = ColorProviders(light = light, dark = dark)
    val background = role { surfaceContainer }

    /** The palette on [background]. */
    val plain = WidgetPalette(
        onSurface = role { onSurface },
        onSurfaceVariant = role { onSurfaceVariant },
        placeholder = role { surfaceContainerHigh },
        primary = role { primary },
        onPrimary = role { onPrimary },
    )

    /** One color role, switching with the system theme. */
    private fun role(color: ColorScheme.() -> Color) = ColorProvider(day = light.color(), night = dark.color())
}
