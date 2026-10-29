package com.quantumff.segue.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import org.junit.Test

/** The "Pausing in 30 min" snackbars with Cancel that confirm a sleep timer. */
class SleepTimerConfirmationsTest {
    private val snackbars = Snackbars()
    private val confirmations = SleepTimerConfirmations(snackbars)
    private var cleared = 0

    private fun set(choice: SleepTimerChoice) = confirmations.set(choice) { cleared++ }

    private fun shownText() = snackbars.current.value?.text

    @Test
    fun `a timer in minutes says how long until it pauses`() {
        set(SleepTimerChoice.Minutes(30))

        assertThat(shownText()).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_set_minutes, 30))
    }

    @Test
    fun `a timer of whole hours says it in hours, like the sheet`() {
        set(SleepTimerChoice.Minutes(60))

        assertThat(shownText()).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_set_hours, 1))
    }

    @Test
    fun `a timer over an hour says it in hours and minutes, like the sheet`() {
        set(SleepTimerChoice.Minutes(90))

        assertThat(shownText()).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_set_hours_minutes, 1, 30))
    }

    @Test
    fun `a timer at the end of the track says so`() {
        set(SleepTimerChoice.EndOfTrack)

        assertThat(shownText()).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_set_end_of_track))
    }

    @Test
    fun `a timer at the end of the album says so`() {
        set(SleepTimerChoice.EndOfAlbum)

        assertThat(shownText()).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_set_end_of_album))
    }

    @Test
    fun `Cancel on the snackbar clears the timer`() {
        set(SleepTimerChoice.Minutes(30))

        snackbars.onAction(snackbars.current.value!!)

        assertThat(cleared).isEqualTo(1)
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun `a newer timer takes the earlier one's snackbar down`() {
        set(SleepTimerChoice.Minutes(30))
        set(SleepTimerChoice.EndOfAlbum)

        // There is one timer, so only the snackbar that names it stays.
        assertThat(shownText()).isEqualTo(SnackbarText.of(R.string.feature_nowplaying_sleep_set_end_of_album))
        snackbars.onDismiss(snackbars.current.value!!)
        assertThat(snackbars.current.value).isNull()
        assertThat(cleared).isEqualTo(0)
    }

    @Test
    fun `cancelling the timer from the sheet takes its snackbar down`() {
        set(SleepTimerChoice.Minutes(30))

        confirmations.cancelled()

        assertThat(snackbars.current.value).isNull()
        assertThat(cleared).isEqualTo(0)
    }
}
