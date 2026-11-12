package com.quantumff.segue.feature.nowplaying

import com.quantumff.segue.core.data.snackbar.SnackbarText

/** How long a sleep timer runs or has left, as the chip and the sheet say it: "45 min", "1 h", "1 h 30 min". */
internal fun sleepDuration(minutes: Int): SnackbarText {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> SnackbarText.of(R.string.feature_nowplaying_sleep_minutes, rest)
        rest == 0 -> SnackbarText.of(R.string.feature_nowplaying_sleep_hours, hours)
        else -> SnackbarText.of(R.string.feature_nowplaying_sleep_hours_minutes, hours, rest)
    }
}

private const val MINUTES_PER_HOUR = 60
