package com.quantumff.segue.core.designsystem.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {
    @Test
    fun `formats minutes and hours`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(237)).isEqualTo("3:57")
        assertThat(formatDuration(3600)).isEqualTo("1:00:00")
        assertThat(formatDuration(4321)).isEqualTo("1:12:01")
        assertThat(formatDuration(-5)).isEqualTo("0:00")
    }
}
