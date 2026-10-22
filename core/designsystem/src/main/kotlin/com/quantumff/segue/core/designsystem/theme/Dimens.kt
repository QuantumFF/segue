package com.quantumff.segue.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing scale, DESIGN.md §2.3. */
object SegueSpacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Screen side margin on compact width. */
    val screenMarginCompact = lg

    /** Screen side margin on medium and expanded width. */
    val screenMarginWide = xl

    /** Grid gutter and carousel item spacing. */
    val gutter = md

    /** Gap between Home sections. */
    val sectionGap = xl
}

/** Radius tokens, DESIGN.md §2.3. */
object SegueRadius {
    val xs = 2.dp
    val sm = 4.dp
    val md = 6.dp
    val lg = 12.dp
    val xl = 16.dp
}

/** Shapes keyed by radius token. `full` is [CircleShape]. */
object SegueShapes {
    val xs = RoundedCornerShape(SegueRadius.xs)
    val sm = RoundedCornerShape(SegueRadius.sm)
    val md = RoundedCornerShape(SegueRadius.md)
    val lg = RoundedCornerShape(SegueRadius.lg)
    val xl = RoundedCornerShape(SegueRadius.xl)
    val full = CircleShape
    val sheetTop = RoundedCornerShape(topStart = SegueRadius.xl, topEnd = SegueRadius.xl)
}

internal val SegueMaterialShapes = Shapes(
    extraSmall = SegueShapes.xs,
    small = SegueShapes.sm,
    medium = SegueShapes.md,
    large = SegueShapes.lg,
    extraLarge = SegueShapes.xl,
)

/** Component sizes from DESIGN.md §3 to §5. */
object SegueSizes {
    val touchTarget = 48.dp
    val icon = 24.dp
    val iconSmall = 20.dp
    val iconTransport = 28.dp
    val iconEmptyState = 48.dp
    val badge = 16.dp
    val explicitBadge = 14.dp

    val trackRow = 56.dp
    val trackRowDense = 48.dp
    val trackRowArt = 40.dp
    val trackNumberWidth = 32.dp
    val mediaRow = 64.dp
    val mediaRowArt = 48.dp
    val overflowButton = 40.dp

    val sectionHeader = 48.dp
    val topBar = 56.dp
    val detailTopBarPlay = 36.dp
    val sheetRow = 48.dp
    val sheetHeaderArt = 48.dp

    val navigationBar = 64.dp
    val navigationRail = 80.dp
    val sidebar = 240.dp
    val miniPlayer = 64.dp
    val miniPlayerArt = 48.dp
    val miniPlayerProgress = 2.dp
    val playerBar = 80.dp
    val playerBarArt = 56.dp
    val playerBarPlay = 40.dp

    /** Around the player bar's 40 play circle, 2 clear of it. */
    val playerBarBufferingRing = 48.dp

    /** Around the mini player's 24 play/pause icon, inside its 48 target. */
    val miniPlayerBufferingRing = 40.dp
    val bufferingRingStroke = 2.dp
    val volumeSlider = 96.dp

    val heroArtCompact = 220.dp
    val heroArtWide = 250.dp
    val heroArtArtist = 160.dp
    val heroWideMinHeight = 340.dp
    val heroWideBreakpoint = SegueBreakpoints.medium
    val heroTintOverhang = 64.dp
    val nowPlayingArtMax = 400.dp
    val loginMaxWidth = 400.dp
    val primaryPlayButton = 56.dp

    val albumCard = 144.dp
    val albumCardSmall = 112.dp
    val albumCardLarge = 176.dp
    val albumGridCell = 150.dp
    val artistCircle = 96.dp

    val chip = 32.dp
    val pill = 28.dp
    val spinner = 20.dp
    val spinnerStroke = 2.dp
    val progressLine = 2.dp
    val equalizerBar = 3.dp
    val hairline = 1.dp

    /** Flat tonal surfaces only: M3 tonal elevation is always 0 (DESIGN.md §2.4). */
    val noElevation = 0.dp
}

/** The only three shadows allowed, all outside scrolling lists (DESIGN.md §2.4). */
object SegueShadows {
    val heroArt = 8.dp
    const val HERO_ART_ALPHA = 0.5f
    val nowPlayingArt = 24.dp
    const val NOW_PLAYING_ART_ALPHA = 0.3f
    val nowPlayingArtOffsetY = 8.dp
    val featureCarouselArt = 20.dp
    const val FEATURE_CAROUSEL_ART_ALPHA = 0.5f
}

/** Window width classes (DESIGN.md §3): compact < 600, medium 600 to 839, expanded ≥ 840. */
object SegueBreakpoints {
    val medium = 600.dp
    val expanded = 840.dp
}
