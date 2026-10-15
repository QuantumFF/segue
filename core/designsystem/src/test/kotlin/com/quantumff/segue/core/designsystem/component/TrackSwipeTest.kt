package com.quantumff.segue.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackSwipeTest {
    private val width = 1000f
    private val fling = 2000f

    private fun settle(offset: Float, velocity: Float = 0f, hasPrevious: Boolean = true, hasNext: Boolean = true) =
        settleTrackSwipe(offset, velocity, width, fling, hasPrevious, hasNext)

    @Test
    fun `a short slow drag springs back`() {
        assertThat(settle(offset = -200f)).isEqualTo(TrackSwipe.Stay)
        assertThat(settle(offset = 200f)).isEqualTo(TrackSwipe.Stay)
    }

    @Test
    fun `dragging past 30 percent skips`() {
        assertThat(settle(offset = -300f)).isEqualTo(TrackSwipe.Next)
        assertThat(settle(offset = 300f)).isEqualTo(TrackSwipe.Previous)
    }

    @Test
    fun `a fast fling skips even when short`() {
        assertThat(settle(offset = -50f, velocity = -2500f)).isEqualTo(TrackSwipe.Next)
        assertThat(settle(offset = 50f, velocity = 2500f)).isEqualTo(TrackSwipe.Previous)
    }

    @Test
    fun `a fling back toward the start springs back`() {
        assertThat(settle(offset = -400f, velocity = 2500f)).isEqualTo(TrackSwipe.Stay)
    }

    @Test
    fun `no neighbor in that direction springs back`() {
        assertThat(settle(offset = -600f, hasNext = false)).isEqualTo(TrackSwipe.Stay)
        assertThat(settle(offset = 600f, hasPrevious = false)).isEqualTo(TrackSwipe.Stay)
    }

    @Test
    fun `an unmeasured width never skips`() {
        assertThat(settleTrackSwipe(-300f, 0f, 0f, fling, hasPrevious = true, hasNext = true))
            .isEqualTo(TrackSwipe.Stay)
    }
}
