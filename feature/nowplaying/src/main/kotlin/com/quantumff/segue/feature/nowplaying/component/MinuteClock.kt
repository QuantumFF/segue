package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * The wall-clock time in epoch millis, refreshed each whole minute before or after [alignedTo] while
 * composed and while [ticking]; nothing runs once it leaves the composition. The sleep timer sheet
 * and the top bar's chip both count minutes left from it, aligned to the timer's end, so they change
 * the moment a whole minute is left and never disagree.
 */
@Composable
internal fun rememberMinuteClock(
    ticking: Boolean = true,
    alignedTo: Long = 0L,
    clock: () -> Long = System::currentTimeMillis,
): State<Long> = produceState(clock(), ticking, alignedTo, clock) {
    value = clock()
    while (ticking) {
        delay(millisToNextMinute(value, alignedTo))
        value = clock()
    }
}

/** From [nowMillis] to the next whole minute before or after [alignedTo]; a full minute when on one. */
internal fun millisToNextMinute(nowMillis: Long, alignedTo: Long): Long =
    MILLIS_PER_MINUTE - Math.floorMod(nowMillis - alignedTo, MILLIS_PER_MINUTE)

private const val MILLIS_PER_MINUTE = 60_000L
