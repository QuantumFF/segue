package com.quantumff.segue.core.model

/**
 * Whether an album, playlist or artist page is the queue source, and whether it plays: what the
 * hero's Play and the collapsed top bar's play button do (DESIGN.md §4.3).
 */
enum class HeroPlayback {
    /**
     * The queue came from elsewhere, or the page's queue played to its end: Play replaces the queue
     * with the page.
     */
    NotSource,

    /** The page is the queue source and plays: the buttons show Pause, and a tap pauses. */
    Playing,

    /** The page is the queue source, paused: Play resumes the queue. */
    Paused,
}

/**
 * The hero playback of a page that [isPage] recognises as a queue source. Compare the kind as well
 * as the id: ids are unique per kind only.
 */
fun PlayerUiState.heroPlayback(isPage: (QueueSource) -> Boolean): HeroPlayback = when {
    !isPage(queueSource) || hasEnded -> HeroPlayback.NotSource
    isPlaying -> HeroPlayback.Playing
    else -> HeroPlayback.Paused
}
