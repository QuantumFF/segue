package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Now Playing's layout for a window and posture, over the table in DESIGN.md §4.7. */
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

    @Test
    fun aHalfOpenedHorizontalFoldGetsTheTabletopLayout() {
        val tabletop = Posture.Tabletop(hingeTop = 420.dp, hingeBottom = 420.dp)
        assertEquals(
            NowPlayingLayout.Tabletop,
            nowPlayingLayout(WindowShape(width = 700.dp, height = 840.dp, posture = tabletop)),
        )
        assertEquals(
            NowPlayingLayout.Tabletop,
            nowPlayingLayout(WindowShape(width = 1000.dp, height = 840.dp, posture = tabletop)),
        )
    }

    @Test
    fun aHingeOutsideTheWindowLeavesTheLayoutToTheWindowSize() {
        val below = Posture.Tabletop(hingeTop = 900.dp, hingeBottom = 900.dp)
        assertEquals(
            NowPlayingLayout.Portrait,
            nowPlayingLayout(WindowShape(width = 700.dp, height = 840.dp, posture = below)),
        )
    }

    @Test
    fun bookPostureUsesTheLayoutForTheWindowSize() {
        assertEquals(
            NowPlayingLayout.TwoPane,
            nowPlayingLayout(WindowShape(width = 840.dp, height = 700.dp, posture = Posture.Book)),
        )
    }

    @Test
    fun aHalfOpenedHorizontalHingeIsTabletopPosture() {
        val hinge = HingeInfo(
            bounds = Rect(left = 0f, top = 800f, right = 1400f, bottom = 840f),
            isFlat = false,
            isVertical = false,
            isSeparating = false,
            isOccluding = false,
        )
        assertEquals(
            Posture.Tabletop(hingeTop = 400.dp, hingeBottom = 420.dp),
            postureOf(listOf(hinge), Density(2f)),
        )
    }

    @Test
    fun aHalfOpenedVerticalHingeIsBookPosture() {
        val hinge = HingeInfo(
            bounds = Rect(left = 840f, top = 0f, right = 840f, bottom = 1400f),
            isFlat = false,
            isVertical = true,
            isSeparating = false,
            isOccluding = false,
        )
        assertEquals(Posture.Book, postureOf(listOf(hinge), Density(2f)))
    }

    @Test
    fun aFlatHingeOrNoHingeIsFlat() {
        val flat = HingeInfo(
            bounds = Rect(left = 0f, top = 800f, right = 1400f, bottom = 800f),
            isFlat = true,
            isVertical = false,
            isSeparating = false,
            isOccluding = false,
        )
        assertEquals(Posture.Flat, postureOf(listOf(flat), Density(2f)))
        assertEquals(Posture.Flat, postureOf(emptyList(), Density(2f)))
    }
}
