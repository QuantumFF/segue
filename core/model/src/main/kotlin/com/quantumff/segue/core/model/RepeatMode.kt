package com.quantumff.segue.core.model

/** Repeat setting of the player. */
enum class RepeatMode {
    Off,
    All,
    One,
    ;

    /** The repeat button's next setting: Off → All → One → Off. */
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}
