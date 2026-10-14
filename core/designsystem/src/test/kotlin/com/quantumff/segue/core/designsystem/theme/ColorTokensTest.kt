package com.quantumff.segue.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorTokensTest {
    @Test
    fun `dark scheme uses the DESIGN md neutrals and the blue accent`() {
        val scheme = SeguePalettes.Dark.colorScheme(AccentColors(primary = AccentPreset.Blue.dark))

        assertThat(scheme.background).isEqualTo(Color(0xFF0C0C0C))
        assertThat(scheme.surface).isEqualTo(Color(0xFF0C0C0C))
        assertThat(scheme.surfaceContainer).isEqualTo(Color(0xFF141414))
        assertThat(scheme.surfaceContainerHigh).isEqualTo(Color(0xFF1C1C1C))
        assertThat(scheme.surfaceContainerHighest).isEqualTo(Color(0xFF242424))
        assertThat(scheme.onSurface).isEqualTo(Color(0xFFE1E1E1))
        assertThat(scheme.onSurfaceVariant).isEqualTo(Color(0xFF969696))
        assertThat(scheme.primary).isEqualTo(Color(0xFF3574FC))
        assertThat(scheme.outlineVariant).isEqualTo(Color.White.copy(alpha = 0.10f))
        assertThat(scheme.primaryContainer)
            .isEqualTo(Color(0xFF3574FC).copy(alpha = 0.16f).compositeOver(Color(0xFF0C0C0C)))
        // The navigation indicator pill is primary @ 16%.
        assertThat(scheme.secondaryContainer).isEqualTo(scheme.primaryContainer)
    }

    @Test
    fun `black and light palettes match the table`() {
        val black = SeguePalettes.Black.colorScheme(AccentColors(primary = AccentPreset.Blue.dark))
        assertThat(black.background).isEqualTo(Color.Black)
        assertThat(black.surfaceContainer).isEqualTo(Color(0xFF0A0A0A))
        assertThat(black.onSurfaceVariant).isEqualTo(Color(0xFF8C8C8C))

        val light = SeguePalettes.Light.colorScheme(AccentColors(primary = AccentPreset.Blue.light))
        assertThat(light.background).isEqualTo(Color(0xFFF5F5F5))
        assertThat(light.surfaceContainerLowest).isEqualTo(Color.White)
        assertThat(light.onSurface).isEqualTo(Color(0xFF191919))
        assertThat(light.primary).isEqualTo(Color(0xFF0066E0))
        assertThat(light.error).isEqualTo(Color(0xFFD93025))
    }

    @Test
    fun `black replaces dark only when the system is dark`() {
        assertThat(ThemeMode.Black.palette(systemDark = true)).isEqualTo(SeguePalettes.Black)
        assertThat(ThemeMode.Black.palette(systemDark = false)).isEqualTo(SeguePalettes.Light)
        assertThat(ThemeMode.System.palette(systemDark = true)).isEqualTo(SeguePalettes.Dark)
        assertThat(ThemeMode.Dark.palette(systemDark = false)).isEqualTo(SeguePalettes.Dark)
        assertThat(ThemeMode.Light.palette(systemDark = true)).isEqualTo(SeguePalettes.Light)
    }

    @Test
    fun `every light preset reaches 4_5 to 1 on the light background`() {
        AccentPreset.entries.forEach { preset ->
            assertThat(contrastRatio(preset.light, SeguePalettes.Light.background)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `darkening stops as soon as the contrast target is met`() {
        val teal = AccentPreset.Teal.dark
        val darkened = teal.darkenToContrast(SeguePalettes.Light.background)
        assertThat(darkened.toHsl().lightness).isLessThan(teal.toHsl().lightness)
        assertThat(darkened.toHsl().hue).isWithin(1f).of(teal.toHsl().hue)
    }

    @Test
    fun `hsl round trip keeps the color`() {
        val color = Color(0xFF8B5CF6)
        val back = color.toHsl().toColor()
        assertThat(back.red).isWithin(0.01f).of(color.red)
        assertThat(back.green).isWithin(0.01f).of(color.green)
        assertThat(back.blue).isWithin(0.01f).of(color.blue)
    }
}
