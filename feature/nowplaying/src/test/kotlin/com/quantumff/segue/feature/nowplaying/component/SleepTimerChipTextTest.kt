package com.quantumff.segue.feature.nowplaying.component

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.feature.nowplaying.R
import org.junit.Test

/** The text on the sleep timer chip in Now Playing's top bar, for each kind of timer. */
class SleepTimerChipTextTest {
    private fun atTimeLeft(millisLeft: Long) = SleepTimer.AtTime(NOW + millisLeft).chipText(NOW)

    @Test
    fun `a timer says the minutes left`() {
        assertThat(atTimeLeft(23 * MINUTE)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, 23))
    }

    @Test
    fun `part of a minute left counts as a whole minute`() {
        assertThat(
            atTimeLeft(22 * MINUTE + 1),
        ).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, 23))
        assertThat(atTimeLeft(1)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, 1))
    }

    @Test
    fun `just under an hour left reads as an hour`() {
        assertThat(atTimeLeft(60 * MINUTE - 1)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_hours, 1))
    }

    @Test
    fun `an hour or more left says it in hours and minutes, like the sheet`() {
        assertThat(atTimeLeft(120 * MINUTE)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_hours, 2))
        assertThat(atTimeLeft(90 * MINUTE)).isEqualTo(
            SnackbarText.of(R.string.feature_nowplaying_sleep_hours_minutes, 1, 30),
        )
    }

    @Test
    fun `a timer that is due or past due has no minutes left`() {
        assertThat(atTimeLeft(0)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, 0))
        assertThat(atTimeLeft(-5 * MINUTE)).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, 0))
    }

    @Test
    fun `an end of track timer says so`() {
        assertThat(SleepTimer.EndOfTrack.chipText(NOW))
            .isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_end_of_track))
    }

    @Test
    fun `an end of album timer says so`() {
        assertThat(SleepTimer.EndOfAlbum.chipText(NOW))
            .isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_end_of_album))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
