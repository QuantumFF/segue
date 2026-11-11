package com.quantumff.segue.feature.nowplaying

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the phone landscape layout shares its width (DESIGN.md §4.7): the art is sized from the height,
 * and an open panel takes width from the art only down to the art's 160 floor, then narrows itself.
 * Windows have a 24dp status bar; the art keeps 24 on each side, so the controls get the rest.
 */
class PhoneLandscapeWidthsTest {
    @Test
    fun aSmallPhoneKeepsTheControlsAndShrinksTheArtOnlyForThem() {
        assertWidths(art = 280.dp, panel = 0.dp, controls = 312.dp, width = 640.dp, height = 360.dp, open = false)
    }

    @Test
    fun aSmallPhoneWithAPanelKeepsTheArtAtItsFloorAndNarrowsThePanel() {
        assertWidths(art = 160.dp, panel = 120.dp, controls = 312.dp, width = 640.dp, height = 360.dp, open = true)
    }

    @Test
    fun aPhoneWithRoomForTheHeightKeepsTheArtFull() {
        assertWidths(art = 312.dp, panel = 0.dp, controls = 324.dp, width = 684.dp, height = 360.dp, open = false)
        assertWidths(art = 312.dp, panel = 0.dp, controls = 440.dp, width = 800.dp, height = 360.dp, open = false)
        assertWidths(art = 364.dp, panel = 0.dp, controls = 503.dp, width = 915.dp, height = 412.dp, open = false)
    }

    @Test
    fun aPanelTakesWidthFromTheArtDownToItsFloor() {
        assertWidths(art = 160.dp, panel = 164.dp, controls = 312.dp, width = 684.dp, height = 360.dp, open = true)
        assertWidths(art = 160.dp, panel = 280.dp, controls = 312.dp, width = 800.dp, height = 360.dp, open = true)
        assertWidths(art = 275.dp, panel = 280.dp, controls = 312.dp, width = 915.dp, height = 412.dp, open = true)
    }

    @Test
    fun aWideWindowGivesThePanelTheSpareWidthAndTheArtItsFullHeight() {
        assertWidths(art = 352.dp, panel = 288.dp, controls = 312.dp, width = 1000.dp, height = 400.dp, open = true)
    }

    @Test
    fun aWindowTooNarrowForBothFloorsSharesWhatIsLeftAndNothingVanishes() {
        // 600 wide: 552 less the controls' 312 leaves 240 for art and panel, under 160 + 120.
        val widths = phoneLandscapeWidths(width = 600.dp, height = 300.dp, statusBar = 24.dp, panelOpen = true)
        assertEquals(240f, (widths.art + widths.panel).value, 0.01f)
        assert(widths.art > 120.dp && widths.panel > 90.dp) { widths }
    }

    private fun assertWidths(art: Dp, panel: Dp, controls: Dp, width: Dp, height: Dp, open: Boolean) {
        val widths = phoneLandscapeWidths(width = width, height = height, statusBar = 24.dp, panelOpen = open)
        assertEquals("art at $width x $height", art, widths.art)
        assertEquals("panel at $width x $height", panel, widths.panel)
        assertEquals("controls at $width x $height", controls, width - 48.dp - widths.art - widths.panel)
    }
}
