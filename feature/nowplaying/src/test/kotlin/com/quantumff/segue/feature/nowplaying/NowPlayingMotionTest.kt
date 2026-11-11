package com.quantumff.segue.feature.nowplaying

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NowPlayingMotionTest {
    // DESIGN.md §2.5: `medium` is 200ms on the standard curve.
    private val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val crossfadeIn = fadeIn(tween(200, easing = standard))
    private val crossfadeOut = fadeOut(tween(200, easing = standard))

    @Test
    fun withReduceMotionPanelsCrossfadeAtMedium() {
        for (draggedDown in listOf(false, true)) {
            val transform = NowPlayingMotion.panelTransition(reduceMotion = true, draggedDown = draggedDown)
            assertThat(transform.targetContentEnter).isEqualTo(crossfadeIn)
            assertThat(transform.initialContentExit).isEqualTo(crossfadeOut)
        }
    }

    @Test
    fun withoutReduceMotionPanelsStillSlide() {
        for (draggedDown in listOf(false, true)) {
            val transform = NowPlayingMotion.panelTransition(reduceMotion = false, draggedDown = draggedDown)
            assertThat(transform.targetContentEnter).isNotEqualTo(crossfadeIn)
        }
    }

    @Test
    fun inPhoneLandscapeThePanelAreaGrowsOnlyWithoutReduceMotion() {
        assertThat(NowPlayingMotion.sidePanelGrows(reduceMotion = false)).isTrue()
        assertThat(NowPlayingMotion.sidePanelGrows(reduceMotion = true)).isFalse()
    }

    @Test
    fun withReduceMotionLyricLinesKeepTheirSizeAndJump() {
        val motion = NowPlayingMotion.lyricLines(reduceMotion = true)
        assertThat(motion.scale(active = true)).isEqualTo(1f)
        assertThat(motion.scale(active = false)).isEqualTo(1f)
        assertThat(motion.smoothScroll).isFalse()
    }

    @Test
    fun withoutReduceMotionInactiveLyricLinesShrinkAndTheListGlides() {
        val motion = NowPlayingMotion.lyricLines(reduceMotion = false)
        assertThat(motion.scale(active = true)).isEqualTo(1f)
        assertThat(motion.scale(active = false)).isLessThan(1f)
        assertThat(motion.smoothScroll).isTrue()
    }

    @Test
    fun theTitleMarqueesOnlyWhileResumedAndWithoutReduceMotion() {
        assertThat(NowPlayingMotion.titleMarquees(resumed = true, reduceMotion = false)).isTrue()
        assertThat(NowPlayingMotion.titleMarquees(resumed = false, reduceMotion = false)).isFalse()
        assertThat(NowPlayingMotion.titleMarquees(resumed = true, reduceMotion = true)).isFalse()
        assertThat(NowPlayingMotion.titleMarquees(resumed = false, reduceMotion = true)).isFalse()
    }

    @Test
    fun choosingAPanelGlidesThePagerOnlyWithoutReduceMotion() {
        assertThat(NowPlayingMotion.panelPagesGlide(reduceMotion = false)).isTrue()
        assertThat(NowPlayingMotion.panelPagesGlide(reduceMotion = true)).isFalse()
    }
}
