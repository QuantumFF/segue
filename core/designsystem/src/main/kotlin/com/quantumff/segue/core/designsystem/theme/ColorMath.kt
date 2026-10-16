package com.quantumff.segue.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** Hue in degrees [0, 360), saturation and lightness in [0, 1]. */
internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float) {
    fun toColor(): Color = Color.hsl(hue, saturation, lightness)
}

internal fun Color.toHsl(): Hsl {
    val max = max(red, max(green, blue))
    val min = min(red, min(green, blue))
    val lightness = (max + min) / 2f
    if (max == min) return Hsl(hue = 0f, saturation = 0f, lightness = lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        red -> ((green - blue) / delta).let { if (green < blue) it + 6f else it }
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return Hsl(hue = hue % 360f, saturation = saturation, lightness = lightness)
}

/** Clamps saturation to at most [maxSaturation] and lightness into [lightness]. */
internal fun Color.clampHsl(maxSaturation: Float, lightness: ClosedFloatingPointRange<Float>): Color {
    val hsl = toHsl()
    return hsl.copy(
        saturation = min(hsl.saturation, maxSaturation),
        lightness = hsl.lightness.coerceIn(lightness),
    ).toColor()
}

/** WCAG 2 contrast ratio between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

/**
 * Content color on an arbitrary accent: white (the design's default on `primary`) unless it drops
 * below 3:1, the WCAG minimum for icons and large text; black then.
 */
internal fun Color.readableContent(): Color =
    if (contrastRatio(Color.White, this) >= MIN_CONTENT_CONTRAST) Color.White else Color.Black

private const val MIN_CONTENT_CONTRAST = 3f

/**
 * Darkens [this] in HSL lightness steps until it reaches [minContrast] against [background].
 * Used for light-theme accent presets (DESIGN.md §2.1: 4.5:1 on #F5F5F5).
 */
internal fun Color.darkenToContrast(background: Color, minContrast: Float = 4.5f): Color {
    var hsl = toHsl()
    var color = this
    while (contrastRatio(color, background) < minContrast && hsl.lightness > 0f) {
        hsl = hsl.copy(lightness = max(0f, hsl.lightness - LIGHTNESS_STEP))
        color = hsl.toColor()
    }
    return color
}

private const val LIGHTNESS_STEP = 0.01f
