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
 * A page that can be the queue source, so its hero's Play can pause and resume it: an album,
 * playlist or artist page.
 */
sealed interface SourcePage {
    data class Album(val albumId: String) : SourcePage

    data class Playlist(val playlistId: String) : SourcePage

    data class Artist(val artistId: String) : SourcePage
}

/** The hero playback of [page]. */
fun PlayerUiState.heroPlayback(page: SourcePage): HeroPlayback = when {
    !queueSource.opens(page) || hasEnded -> HeroPlayback.NotSource
    isPlaying -> HeroPlayback.Playing
    else -> HeroPlayback.Paused
}

/** What a [SourcePage] shows of the player: the song its rows mark, and its [hero] playback. */
data class PagePlayback(val currentSong: CurrentSong? = null, val hero: HeroPlayback = HeroPlayback.NotSource)

/** What [page] shows of this player state. */
fun PlayerUiState.pagePlayback(page: SourcePage): PagePlayback = PagePlayback(currentSong, heroPlayback(page))
