package com.quantumff.segue.core.designsystem.util

import java.util.Locale

/** Formats a duration as `m:ss`, or `h:mm:ss` from one hour. Negative input reads as zero. */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
