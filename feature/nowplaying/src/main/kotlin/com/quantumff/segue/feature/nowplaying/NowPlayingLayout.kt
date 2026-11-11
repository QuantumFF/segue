package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

/**
 * How Now Playing arranges the art, controls and panels (DESIGN.md §4.7).
 *
 * @property panelCoversArt an open panel takes the art's place, so a drag down or back closes it.
 * @property panelAlwaysShown a panel is always on screen, as a tab, and is never closed.
 */
internal enum class NowPlayingLayout(val panelCoversArt: Boolean, val panelAlwaysShown: Boolean) {
    /** Art, metadata and controls stacked; a panel replaces the art. */
    Portrait(panelCoversArt = true, panelAlwaysShown = false),

    /** Art | metadata, seekbar and transport | the open panel, side by side, for a phone held sideways. */
    PhoneLandscape(panelCoversArt = false, panelAlwaysShown = false),

    /** Art and controls on the left, the panels as tabs on the right. */
    TwoPane(panelCoversArt = false, panelAlwaysShown = true),

    /** On a foldable in tabletop posture: the art above the hinge, the controls and bottom row below it. */
    Tabletop(panelCoversArt = true, panelAlwaysShown = false),
    ;

    /** The panel on screen while [openPanel] is open: a layout that always shows one shows Queue when none is. */
    fun shownPanel(openPanel: NowPlayingPanel?): NowPlayingPanel? =
        if (panelAlwaysShown) openPanel ?: NowPlayingPanel.Queue else openPanel
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
 * The layout for [window]. Tabletop posture, with the hinge inside the window, splits at the hinge, and
 * book posture gets two panes. Otherwise a landscape window under 480dp tall is a phone held sideways,
 * whatever its width, and an expanded width, or a landscape window, gets two panes.
 */
internal fun nowPlayingLayout(window: WindowShape): NowPlayingLayout {
    val landscape = window.width > window.height
    val posture = window.posture
    return when {
        posture is Posture.Tabletop && posture.hingeTop > 0.dp && posture.hingeBottom < window.height ->
            NowPlayingLayout.Tabletop

        // Two panes, one each side of the fold (DESIGN.md §3).
        posture is Posture.Book -> NowPlayingLayout.TwoPane

        landscape && window.height < PHONE_LANDSCAPE_MAX_HEIGHT -> NowPlayingLayout.PhoneLandscape

        landscape || window.width >= EXPANDED_MIN_WIDTH -> NowPlayingLayout.TwoPane

        else -> NowPlayingLayout.Portrait
    }
}

private val PHONE_LANDSCAPE_MAX_HEIGHT = 480.dp
private val EXPANDED_MIN_WIDTH = 840.dp

/** The phone landscape layout's [art] side and [panel] width; the controls get the rest. */
internal data class PhoneLandscapeWidths(val art: Dp, val panel: Dp)

/**
 * How the phone landscape layout shares a [width] by [height] window, below a [statusBar] (DESIGN.md
 * §4.7). The art is a square sized from the height. Without a panel it gives up width only so the
 * controls keep [LANDSCAPE_CONTROLS_WIDTH]. An open panel gets [LANDSCAPE_PANEL_WIDTH] by taking width
 * from the art down to [LANDSCAPE_ART_MIN_WIDTH]; past that the panel narrows, down to
 * [LANDSCAPE_PANEL_MIN_WIDTH]. A window too narrow for both floors shares what's left between them, so
 * neither vanishes.
 */
internal fun phoneLandscapeWidths(width: Dp, height: Dp, statusBar: Dp, panelOpen: Boolean): PhoneLandscapeWidths {
    // The art keeps xl on each side, and xl in total above and below it.
    val fromHeight = (height - statusBar - LANDSCAPE_ART_PADDING).coerceAtLeast(0.dp)
    val spare = (width - LANDSCAPE_ART_PADDING * 2 - LANDSCAPE_CONTROLS_WIDTH).coerceAtLeast(0.dp)
    val fullArt = min(fromHeight, spare)
    if (!panelOpen) return PhoneLandscapeWidths(art = fullArt, panel = 0.dp)
    val artFloor = min(LANDSCAPE_ART_MIN_WIDTH, fullArt)
    val art = min(fullArt, max(spare - LANDSCAPE_PANEL_WIDTH, artFloor))
    if (spare - art >= LANDSCAPE_PANEL_MIN_WIDTH) return PhoneLandscapeWidths(art = art, panel = spare - art)
    val shared = spare * (artFloor / (artFloor + LANDSCAPE_PANEL_MIN_WIDTH))
    return PhoneLandscapeWidths(art = shared, panel = spare - shared)
}

/** The art's side padding in phone landscape, and its top and bottom padding together. */
internal val LANDSCAPE_ART_PADDING = 24.dp

/** The phone landscape layout's middle column: the narrowest its transport and bottom rows fit. */
private val LANDSCAPE_CONTROLS_WIDTH = 312.dp

/** The phone landscape panel's width when the art can give it that and stay above its floor. */
private val LANDSCAPE_PANEL_WIDTH = 280.dp

/** The narrowest the phone landscape art gets for an open panel before the panel narrows instead. */
private val LANDSCAPE_ART_MIN_WIDTH = 160.dp

/** The narrowest the phone landscape panel gets before the art goes under its floor. */
private val LANDSCAPE_PANEL_MIN_WIDTH = 120.dp
