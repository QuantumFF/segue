package com.quantumff.segue.feature.nowplaying

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How Now Playing arranges the art, controls and panels (DESIGN.md §4.7). */
internal enum class NowPlayingLayout {
    /** Art, metadata and controls stacked; a panel replaces the art. */
    Portrait,

    /** Art | metadata, seekbar and transport | the open panel, side by side, for a phone held sideways. */
    PhoneLandscape,

    /** Art and controls on the left, the panels as tabs on the right. */
    TwoPane,
}

/** How the device is held. Only flat so far; a folding feature will add more. */
internal enum class Posture {
    Flat,
}

/** The window Now Playing fills, in dp, and the device's posture. */
internal data class WindowShape(val width: Dp, val height: Dp, val posture: Posture = Posture.Flat)

/**
 * The layout for [window]. A landscape window under 480dp tall is a phone held sideways, whatever its
 * width. Otherwise an expanded width, or a landscape window, gets two panes.
 */
internal fun nowPlayingLayout(window: WindowShape): NowPlayingLayout {
    val landscape = window.width > window.height
    return when {
        landscape && window.height < PHONE_LANDSCAPE_MAX_HEIGHT -> NowPlayingLayout.PhoneLandscape
        landscape || window.width >= EXPANDED_MIN_WIDTH -> NowPlayingLayout.TwoPane
        else -> NowPlayingLayout.Portrait
    }
}

private val PHONE_LANDSCAPE_MAX_HEIGHT = 480.dp
private val EXPANDED_MIN_WIDTH = 840.dp
