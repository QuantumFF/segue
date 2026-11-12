package com.quantumff.segue.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.SnackbarText
import org.junit.Test

/** How long a sleep timer runs, as the chip and the sheet both say it. */
class SleepDurationTest {
    @Test
    fun `minutes, whole hours, and hours with minutes`() {
        assertThat(sleepDuration(45)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, 45))
        assertThat(sleepDuration(120)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_hours, 2))
        assertThat(sleepDuration(90))
            .isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_hours_minutes, 1, 30))
    }
}
