package com.quantumff.segue.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/** How a [HeroHeader] lays out for the space it has (DESIGN.md §5.4). */
internal enum class HeroLayout {
    /** Centered column: art, text, actions. Portrait phones, and narrow short windows with capped art. */
    Compact,

    /** Art on the left, text bottom-aligned on the right, min height 340. Width ≥ 600 and height ≥ 480. */
    Wide,

    /**
     * One strip: a thumbnail of the art, the text, and the actions at the end, with no min height. Width
     * ≥ 600 and height < 480: phone landscape, where tracks must show under the hero.
     */
    Short,
}

/** Picks the hero layout from the hero's available [width] and the window's [screenHeight]. */
internal fun heroLayout(width: Dp, screenHeight: Dp): HeroLayout = when {
    width < SegueSizes.heroWideBreakpoint -> HeroLayout.Compact
    screenHeight < SegueSizes.heroShortBreakpoint -> HeroLayout.Short
    else -> HeroLayout.Wide
}

/**
 * The hero art's size for [layout]; [circular] is the artist's art. The short hero's is a thumbnail;
 * otherwise, under 480 of height, it's at most 40% of it.
 */
internal fun heroArtSize(layout: HeroLayout, circular: Boolean, screenHeight: Dp): Dp {
    val size = when {
        layout == HeroLayout.Short -> SegueSizes.heroArtShort
        circular -> SegueSizes.heroArtArtist
        layout == HeroLayout.Wide -> SegueSizes.heroArtWide
        else -> SegueSizes.heroArtCompact
    }
    return if (screenHeight <
        SegueSizes.heroShortBreakpoint
    ) {
        min(size, screenHeight * HERO_SHORT_ART_FRACTION)
    } else {
        size
    }
}

/** The window's height, which the hero's layout depends on: the hero itself sits in a scrolling list. */
@Composable
internal fun heroScreenHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

/**
 * Whether the window is under 480 tall, as a phone in landscape: hero pages shrink their hero and
 * keep what sits under it to one line, so tracks stay visible (DESIGN.md §5.4).
 */
@Composable
fun isShortWindow(): Boolean = heroScreenHeight() < SegueSizes.heroShortBreakpoint

private const val HERO_SHORT_ART_FRACTION = 0.4f
