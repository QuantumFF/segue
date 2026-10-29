package com.quantumff.segue.feature.nowplaying

import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarSlot
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.post

/**
 * "Pausing in 30 min" with Cancel (DESIGN.md §5.7), posted to the app's [Snackbars] when a sleep
 * timer is set, so it stays when Now Playing collapses. There is one timer, so a newer timer, or
 * cancelling it from the sheet, takes down the snackbar of the one before: its Cancel would clear
 * a timer it doesn't name.
 */
class SleepTimerConfirmations(snackbars: Snackbars) {
    private val slot = SnackbarSlot<Unit>(snackbars)

    /** Confirms a timer set for [choice]; Cancel runs [onCancel], which should not need Now Playing. */
    fun confirm(choice: SleepTimerChoice, onCancel: () -> Unit) {
        slot.post(
            choice.confirmation(),
            SnackbarAction(SnackbarText.of(R.string.feature_nowplaying_sleep_set_cancel), onClick = onCancel),
        )
    }

    /** Takes the confirmation down, as when the timer is cancelled from the sheet. */
    fun takeDown() = slot.takeDown()
}

/** "Pausing in 45 min", "in 1 h", "in 1 h 30 min", like the sheet's labels, or "at end of track". */
private fun SleepTimerChoice.confirmation(): SnackbarText = when (this) {
    is SleepTimerChoice.Minutes -> {
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        when {
            hours == 0 -> SnackbarText.of(R.string.feature_nowplaying_sleep_set_minutes, rest)
            rest == 0 -> SnackbarText.of(R.string.feature_nowplaying_sleep_set_hours, hours)
            else -> SnackbarText.of(R.string.feature_nowplaying_sleep_set_hours_minutes, hours, rest)
        }
    }

    SleepTimerChoice.EndOfTrack -> SnackbarText.of(R.string.feature_nowplaying_sleep_set_end_of_track)

    SleepTimerChoice.EndOfAlbum -> SnackbarText.of(R.string.feature_nowplaying_sleep_set_end_of_album)
}

private const val MINUTES_PER_HOUR = 60
