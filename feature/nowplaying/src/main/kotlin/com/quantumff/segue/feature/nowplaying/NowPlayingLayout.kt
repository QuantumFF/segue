package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.ui.unit.Density
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

    /** On a foldable in tabletop posture: the art above the hinge, the controls and bottom row below it. */
    Tabletop,
}

/** How the device is held, from the window's folding feature. */
internal sealed interface Posture {
    /** No fold, or a fold opened flat. */
    data object Flat : Posture

    /** Half opened at a fold down the window, like a book. */
    data object Book : Posture

    /** Half opened at a fold across the window, the hinge from [hingeTop] to [hingeBottom] below the window's top. */
    data class Tabletop(val hingeTop: Dp, val hingeBottom: Dp) : Posture
}

/** The posture for the window's [hinges], whose bounds are in pixels in the window. */
internal fun postureOf(hinges: List<HingeInfo>, density: Density): Posture {
    val fold = hinges.firstOrNull { !it.isFlat } ?: return Posture.Flat
    if (fold.isVertical) return Posture.Book
    return with(density) {
        Posture.Tabletop(hingeTop = fold.bounds.top.toDp(), hingeBottom = fold.bounds.bottom.toDp())
    }
}

/** The window Now Playing fills, in dp, and the device's posture. */
internal data class WindowShape(val width: Dp, val height: Dp, val posture: Posture = Posture.Flat)

/**
 * The layout for [window]. Tabletop posture, with the hinge inside the window, splits at the hinge.
 * Otherwise a landscape window under 480dp tall is a phone held sideways, whatever its width, and an
 * expanded width, or a landscape window, gets two panes.
 */
internal fun nowPlayingLayout(window: WindowShape): NowPlayingLayout {
    val landscape = window.width > window.height
    val posture = window.posture
    return when {
        posture is Posture.Tabletop && posture.hingeTop > 0.dp && posture.hingeBottom < window.height ->
            NowPlayingLayout.Tabletop

        landscape && window.height < PHONE_LANDSCAPE_MAX_HEIGHT -> NowPlayingLayout.PhoneLandscape

        landscape || window.width >= EXPANDED_MIN_WIDTH -> NowPlayingLayout.TwoPane

        else -> NowPlayingLayout.Portrait
    }
}

private val PHONE_LANDSCAPE_MAX_HEIGHT = 480.dp
private val EXPANDED_MIN_WIDTH = 840.dp
