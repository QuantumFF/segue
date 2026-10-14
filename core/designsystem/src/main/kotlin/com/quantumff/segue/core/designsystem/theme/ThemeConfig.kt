package com.quantumff.segue.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The "Theme" setting (DESIGN.md §2.1). */
enum class ThemeMode {
    System,
    Light,
    Dark,

    /** AMOLED black. Follows the system like [System], but uses black instead of dark. */
    Black,
}

/** The "Accent source" setting (DESIGN.md §2.1). */
enum class AccentSource {
    /** One of [AccentPreset]. The default. */
    Fixed,

    /** Material You (API 31+). Falls back to [Fixed] on older devices. */
    MaterialYou,

    /** Follows the current track's art color. Falls back to [Fixed] while there is none. */
    AlbumArt,
}

/** Fixed accent presets. [light] is darkened until it reaches 4.5:1 on the light background. */
enum class AccentPreset(val dark: Color, light: Color? = null) {
    Blue(dark = Color(0xFF3574FC), light = Color(0xFF0066E0)),
    Teal(dark = Color(0xFF14B8A6)),
    Violet(dark = Color(0xFF8B5CF6)),
    Pink(dark = Color(0xFFEC4899)),
    Orange(dark = Color(0xFFF97316)),
    Green(dark = Color(0xFF22C55E)),
    ;

    val light: Color = light ?: dark.darkenToContrast(SeguePalettes.Light.background)
}

/** Everything the user can configure about the theme. Comes from DataStore once settings exist. */
@Immutable
data class SegueThemeConfig(
    val themeMode: ThemeMode = ThemeMode.System,
    val accentSource: AccentSource = AccentSource.Fixed,
    val accentPreset: AccentPreset = AccentPreset.Blue,
    /** Follows the system animator scale too; see [LocalReduceMotion]. */
    val reduceMotion: Boolean = false,
)

/** Which neutral palette a [ThemeMode] resolves to, given the system dark-mode flag. */
internal fun ThemeMode.palette(systemDark: Boolean): NeutralPalette = when (this) {
    ThemeMode.System -> if (systemDark) SeguePalettes.Dark else SeguePalettes.Light
    ThemeMode.Light -> SeguePalettes.Light
    ThemeMode.Dark -> SeguePalettes.Dark
    ThemeMode.Black -> if (systemDark) SeguePalettes.Black else SeguePalettes.Light
}
