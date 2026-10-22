package com.quantumff.segue.core.data.play

import com.quantumff.segue.core.model.HeroPlayback
import com.quantumff.segue.core.model.PagePlayback
import com.quantumff.segue.core.model.SourcePage
import com.quantumff.segue.core.model.pagePlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What [page] shows of the player as it changes: the song its rows mark, and its hero's playback. */
fun PlayerStateSource.pagePlayback(page: SourcePage): Flow<PagePlayback> =
    state.map { it.pagePlayback(page) }.distinctUntilChanged()

/**
 * The hero's Play, or the collapsed top bar's (DESIGN.md §4.3), for a page whose hero playback is
 * [hero]: pauses the page while it is the queue source and plays, resumes it while it is the paused
 * queue source, and otherwise runs [playPage], which replaces the queue with the page.
 */
fun PlayerTransport.heroPlay(hero: HeroPlayback, playPage: () -> Unit) = when (hero) {
    HeroPlayback.Playing -> pause()
    HeroPlayback.Paused -> play()
    HeroPlayback.NotSource -> playPage()
}
