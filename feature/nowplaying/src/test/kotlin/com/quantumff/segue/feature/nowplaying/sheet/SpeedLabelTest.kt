package com.quantumff.segue.feature.nowplaying.sheet

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.UserPreferences
import org.junit.Test

class SpeedLabelTest {
    @Test
    fun `each preset reads as a multiplier without trailing zeros`() {
        assertThat(UserPreferences.PLAYBACK_SPEEDS.map(::speedLabel))
            .containsExactly("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×")
            .inOrder()
    }
}
