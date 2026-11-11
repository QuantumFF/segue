package com.quantumff.segue.feature.nowplaying

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.PagerState
import com.quantumff.segue.core.designsystem.theme.SegueMotion

/**
 * Now Playing's motion choices (DESIGN.md §2.5). With reduce motion on, nothing slides, scales or
 * marquees: panels crossfade at `medium`, lyric lines only fade and jump, and the title ends in an
 * ellipsis.
 */
internal object NowPlayingMotion {
    /**
     * Opening or closing a panel (Queue, Lyrics, Related) in the phone layout. Dragged down, the
     * panel slides out the bottom and the art follows it down, from above.
     */
    fun panelTransition(reduceMotion: Boolean, draggedDown: Boolean): ContentTransform {
        if (reduceMotion) return fadeIn(SegueMotion.medium()) togetherWith fadeOut(SegueMotion.medium())
        val from = if (draggedDown) -1 else 1
        val enter = slideInVertically(SegueMotion.longEnter()) { from * it / PANEL_SLIDE_FRACTION } +
            fadeIn(SegueMotion.longEnter())
        return enter togetherWith fadeOut(if (draggedDown) SegueMotion.longExit() else SegueMotion.medium())
    }

    /**
     * Whether choosing a panel from a tab, the art or the menu glides the panel pager to it. With
     * reduce motion the pager jumps instead. A swipe still follows the finger.
     */
    fun panelPagesGlide(reduceMotion: Boolean): Boolean = !reduceMotion

    /**
     * Whether, in phone landscape, the panel area grows out of the controls' edge as a panel opens
     * (and the art gives up width with it). With reduce motion it appears at its full width instead,
     * and only the panel crossfades.
     */
    fun sidePanelGrows(reduceMotion: Boolean): Boolean = !reduceMotion

    /** How synced lyric lines move as the active line changes. */
    fun lyricLines(reduceMotion: Boolean): LyricLineMotion =
        if (reduceMotion) LyricLineMotion.Calm else LyricLineMotion.Full

    /** The title marquees while the screen is resumed; otherwise it ends in an ellipsis. */
    fun titleMarquees(resumed: Boolean, reduceMotion: Boolean): Boolean = resumed && !reduceMotion

    private const val PANEL_SLIDE_FRACTION = 4
}

/**
 * @property inactiveScale the size of lines other than the active one, which is always full size.
 * @property smoothScroll whether the list glides to the active line, rather than jumping to it.
 */
internal data class LyricLineMotion(val inactiveScale: Float, val smoothScroll: Boolean) {
    fun scale(active: Boolean): Float = if (active) 1f else inactiveScale

    companion object {
        val Full = LyricLineMotion(inactiveScale = 0.96f, smoothScroll = true)
        val Calm = LyricLineMotion(inactiveScale = 1f, smoothScroll = false)
    }
}

/** Shows [page], gliding there only when [NowPlayingMotion.panelPagesGlide]. */
internal suspend fun PagerState.showPanelPage(page: Int, reduceMotion: Boolean) {
    if (NowPlayingMotion.panelPagesGlide(reduceMotion)) animateScrollToPage(page) else scrollToPage(page)
}
