package com.quantumff.segue.core.data.util

import javax.inject.Inject

/** Wall-clock time in epoch milliseconds. Injected so tests control it. */
internal fun interface Clock {
    fun nowMillis(): Long
}

internal class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
