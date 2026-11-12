package com.quantumff.segue.feature.nowplaying.sheet

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.UserPreferences
import java.util.Locale
import org.junit.Test

class SpeedLabelTest {
    @Test
    fun `each preset reads as a number without trailing zeros`() {
        assertThat(UserPreferences.PLAYBACK_SPEEDS.map { speedMultiplier(it, Locale.US) })
            .containsExactly("0.5", "0.75", "1", "1.25", "1.5", "1.75", "2")
            .inOrder()
    }

    @Test
    fun `the number follows the locale`() {
        assertThat(speedMultiplier(0.75f, Locale.GERMANY)).isEqualTo("0,75")
        assertThat(speedMultiplier(1.5f, Locale.FRANCE)).isEqualTo("1,5")
    }
}
