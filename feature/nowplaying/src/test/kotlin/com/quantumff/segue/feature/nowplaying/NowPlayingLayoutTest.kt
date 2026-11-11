package com.quantumff.segue.feature.nowplaying

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Now Playing's layout for a window, over the size table in DESIGN.md §4.7. */
class NowPlayingLayoutTest {
    @Test
    fun portraitPhonesKeepThePortraitLayout() {
        assertEquals(NowPlayingLayout.Portrait, nowPlayingLayout(WindowShape(width = 360.dp, height = 800.dp)))
        assertEquals(NowPlayingLayout.Portrait, nowPlayingLayout(WindowShape(width = 412.dp, height = 915.dp)))
    }

    @Test
    fun aPortraitWindowUnderTheExpandedWidthKeepsThePortraitLayout() {
        assertEquals(NowPlayingLayout.Portrait, nowPlayingLayout(WindowShape(width = 800.dp, height = 1280.dp)))
    }

    @Test
    fun phonesInLandscapeGetThePhoneLandscapeLayout() {
        assertEquals(NowPlayingLayout.PhoneLandscape, nowPlayingLayout(WindowShape(width = 800.dp, height = 360.dp)))
        assertEquals(NowPlayingLayout.PhoneLandscape, nowPlayingLayout(WindowShape(width = 640.dp, height = 360.dp)))
    }

    @Test
    fun aLandscapeWindowJustUnder480TallIsStillAPhone() {
        assertEquals(NowPlayingLayout.PhoneLandscape, nowPlayingLayout(WindowShape(width = 1000.dp, height = 479.dp)))
    }

    @Test
    fun largePhonesInLandscapeAreStillPhonesThoughTheirWidthIsExpanded() {
        assertEquals(NowPlayingLayout.PhoneLandscape, nowPlayingLayout(WindowShape(width = 915.dp, height = 412.dp)))
    }

    @Test
    fun landscapeWindowsAtLeast480TallGetTwoPanes() {
        assertEquals(NowPlayingLayout.TwoPane, nowPlayingLayout(WindowShape(width = 1280.dp, height = 800.dp)))
        assertEquals(NowPlayingLayout.TwoPane, nowPlayingLayout(WindowShape(width = 700.dp, height = 480.dp)))
    }

    @Test
    fun expandedPortraitWindowsGetTwoPanes() {
        assertEquals(NowPlayingLayout.TwoPane, nowPlayingLayout(WindowShape(width = 840.dp, height = 1200.dp)))
    }
}
