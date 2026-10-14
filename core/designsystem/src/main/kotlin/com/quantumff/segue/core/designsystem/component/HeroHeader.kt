package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.art.ArtNoise
import com.quantumff.segue.core.designsystem.art.BlurredArt
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShadows
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.tabularNumbers

/** The "Hero style" setting (DESIGN.md §5.4). */
enum class HeroStyle {
    /** Gradient from the art tint to `background`. The default for album, artist, playlist. */
    Tint,

    /** Pre-blurred art at 70% under `background` gradients (Feishin `albumBackground`). */
    BlurredArt,
}

/** UI state for a hero header. [tint] comes from [com.quantumff.segue.core.designsystem.art.ArtColors]. */
@Immutable
data class HeroHeaderState(
    val overline: String,
    val title: String,
    val subtitle: String?,
    val metadata: String?,
    val coverArtId: String?,
    val tint: Color? = null,
    /** Artists get circular art (DESIGN.md §4.4). */
    val circularArt: Boolean = false,
)

/**
 * Album, artist and playlist hero (DESIGN.md §5.4). Draws behind the status bar; top padding is
 * the status bar plus 56 for the transparent top bar. Compact width is centered; medium and wider
 * (≥ 600dp available) puts the art on the left and bottom-aligns the text, Feishin style.
 *
 * @param actions the action row (Play, Shuffle, …), centered on compact width.
 */
@Composable
fun HeroHeader(
    state: HeroHeaderState,
    modifier: Modifier = Modifier,
    style: HeroStyle = HeroStyle.Tint,
    onSubtitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background
    val tint = state.tint ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.fillMaxWidth()) {
        when (style) {
            HeroStyle.Tint -> Spacer(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Spans the hero height + 64, so the gradient runs into the content below.
                        val height = size.height + SegueSizes.heroTintOverhang.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(listOf(tint, background), startY = 0f, endY = height),
                            size = Size(size.width, height),
                        )
                    },
            )

            HeroStyle.BlurredArt -> {
                BlurredArt(
                    coverArtId = state.coverArtId,
                    alpha = SegueAlpha.HERO_BLURRED_ART,
                    modifier = Modifier.matchParentSize(),
                )
                Spacer(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(background.copy(alpha = SegueAlpha.HERO_BLUR_GRADIENT_TOP), background),
                                ),
                            )
                        },
                )
                ArtNoise(Modifier.matchParentSize())
            }
        }
        BoxWithConstraints(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = SegueSizes.topBar, bottom = SegueSpacing.lg),
        ) {
            if (maxWidth >= WIDE_BREAKPOINT) {
                WideHero(state, onSubtitleClick, actions)
            } else {
                CompactHero(state, onSubtitleClick, actions)
            }
        }
    }
}

@Composable
private fun CompactHero(
    state: HeroHeaderState,
    onSubtitleClick: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.screenMarginCompact),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroArt(state, if (state.circularArt) SegueSizes.heroArtArtist else SegueSizes.heroArtCompact)
        Spacer(Modifier.height(SegueSpacing.lg))
        HeroText(state, MaterialTheme.typography.headlineMedium, TextAlign.Center, onSubtitleClick)
        Spacer(Modifier.height(SegueSpacing.lg))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.lg, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            content = actions,
        )
    }
}

@Composable
private fun WideHero(state: HeroHeaderState, onSubtitleClick: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SegueSizes.heroWideMinHeight - SegueSizes.topBar)
            .padding(horizontal = SegueSpacing.screenMarginWide),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeroArt(state, if (state.circularArt) SegueSizes.heroArtArtist else SegueSizes.heroArtWide)
        Spacer(Modifier.width(SegueSpacing.xl))
        Column(Modifier.weight(1f)) {
            HeroText(state, MaterialTheme.typography.displaySmall, TextAlign.Start, onSubtitleClick)
            Spacer(Modifier.height(SegueSpacing.lg))
            Row(
                horizontalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
private fun HeroArt(state: HeroHeaderState, size: Dp) {
    val shape = if (state.circularArt) SegueShapes.full else SegueShapes.md
    val shadowColor = SegueTheme.colors.shadow.copy(alpha = SegueShadows.HERO_ART_ALPHA)
    CoverArt(
        coverArtId = state.coverArtId,
        displaySize = size,
        shape = shape,
        crossfade = false,
        placeholderIcon = if (state.circularArt) SegueIcons.Artist else SegueIcons.Disc3,
        modifier = Modifier
            .size(size)
            .shadow(SegueShadows.heroArt, shape, ambientColor = shadowColor, spotColor = shadowColor),
    )
}

@Composable
private fun ColumnScope.HeroText(
    state: HeroHeaderState,
    titleStyle: TextStyle,
    textAlign: TextAlign,
    onSubtitleClick: (() -> Unit)?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = state.overline.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = muted,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(SegueSpacing.xs))
    Text(
        text = state.title,
        style = titleStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = TITLE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
    state.subtitle?.let { subtitle ->
        Spacer(Modifier.height(SegueSpacing.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier),
        )
    }
    state.metadata?.let { metadata ->
        Spacer(Modifier.height(SegueSpacing.xxs))
        Text(
            text = metadata,
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val TITLE_MAX_LINES = 3
private val WIDE_BREAKPOINT = SegueSizes.heroWideBreakpoint

private val PreviewHero = HeroHeaderState(
    overline = "Album",
    title = "In Rainbows",
    subtitle = "Radiohead",
    metadata = "2007 · 10 tracks · 42 min",
    coverArtId = null,
)

@PreviewSegueThemes
@Composable
private fun HeroHeaderTintPreview() {
    SeguePreview {
        HeroHeader(state = PreviewHero) {
            PlayButton(onClick = {})
            SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = null, onClick = {})
            SegueIconButton(icon = SegueIcons.Heart, contentDescription = null, onClick = {})
            SegueIconButton(icon = SegueIcons.Download, contentDescription = null, onClick = {})
            SegueIconButton(icon = SegueIcons.MoreVertical, contentDescription = null, onClick = {})
        }
    }
}

@PreviewSegueThemes
@Composable
private fun HeroHeaderBlurredArtistPreview() {
    SeguePreview {
        HeroHeader(
            state = PreviewHero.copy(overline = "Artist", title = "Radiohead", subtitle = null, circularArt = true),
            style = HeroStyle.BlurredArt,
        ) {
            PlayButton(onClick = {})
            SegueIconButton(icon = SegueIcons.ShuffleAlbums, contentDescription = null, onClick = {})
        }
    }
}
