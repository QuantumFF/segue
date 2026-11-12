package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * The wall-clock time in epoch millis, refreshed on each minute boundary while composed and while
 * [ticking]; nothing runs once it leaves the composition. The sleep timer sheet and the top bar's chip
 * both count minutes left from it, so they never disagree.
 */
@Composable
internal fun rememberMinuteClock(ticking: Boolean = true, clock: () -> Long = System::currentTimeMillis): State<Long> =
    produceState(clock(), ticking, clock) {
        value = clock()
        while (ticking) {
            delay(MILLIS_PER_MINUTE - value % MILLIS_PER_MINUTE)
            value = clock()
        }
    }

/** Whole minutes from [nowMillis] until [endsAtMillis], counting part of a minute as one; never below 0. */
internal fun minutesLeft(endsAtMillis: Long, nowMillis: Long): Int =
    ((endsAtMillis - nowMillis).coerceAtLeast(0) + MILLIS_PER_MINUTE - 1).div(MILLIS_PER_MINUTE).toInt()

private const val MILLIS_PER_MINUTE = 60_000L
