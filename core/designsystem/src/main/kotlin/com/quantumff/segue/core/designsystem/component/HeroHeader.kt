package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.R
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
import kotlin.math.roundToInt

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
 * (≥ 600dp available) puts the art on the left and bottom-aligns the text, Feishin style. On short
 * windows (height < 480dp) the art is at most 40% of the window height; in phone landscape the hero is
 * one compact strip of art thumbnail, text and actions, so tracks show below it ([heroLayout]).
 *
 * @param actions the action row (Play, Shuffle, …), centered on compact width. It wraps when it can't fit on one line.
 */
@Composable
fun HeroHeader(
    state: HeroHeaderState,
    modifier: Modifier = Modifier,
    style: HeroStyle = HeroStyle.Tint,
    onSubtitleClick: (() -> Unit)? = null,
    onTitleBottomChange: (Int) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background
    val tint = state.tint ?: MaterialTheme.colorScheme.surfaceContainerHigh
    var heroCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestOnTitleBottomChange by rememberUpdatedState(onTitleBottomChange)
    // The title's bottom edge in the hero, in px: where a detail top bar should start to collapse.
    val onTitlePlace: (LayoutCoordinates) -> Unit = { title ->
        heroCoordinates?.let { hero ->
            latestOnTitleBottomChange((hero.localPositionOf(title, Offset.Zero).y + title.size.height).roundToInt())
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .onPlaced { heroCoordinates = it },
    ) {
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
                .padding(top = SegueSizes.topBar),
        ) {
            val screenHeight = heroScreenHeight()
            val layout = heroLayout(maxWidth, screenHeight)
            val artSize = heroArtSize(layout, state.circularArt, screenHeight)
            when (layout) {
                HeroLayout.Compact -> CompactHero(state, artSize, onSubtitleClick, onTitlePlace, actions)

                HeroLayout.Wide -> WideHero(state, artSize, onSubtitleClick, onTitlePlace, actions)

                HeroLayout.Short -> ShortHero(
                    state = state,
                    artSize = artSize,
                    width = maxWidth,
                    onSubtitleClick = onSubtitleClick,
                    onTitlePlace = onTitlePlace,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun CompactHero(
    state: HeroHeaderState,
    artSize: Dp,
    onSubtitleClick: (() -> Unit)?,
    onTitlePlace: (LayoutCoordinates) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.screenMarginCompact)
            .padding(bottom = SegueSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroArt(state, artSize)
        Spacer(Modifier.height(SegueSpacing.lg))
        HeroText(state, MaterialTheme.typography.headlineMedium, TextAlign.Center, onSubtitleClick, onTitlePlace)
        Spacer(Modifier.height(SegueSpacing.lg))
        HeroActions(
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.lg, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
            actions = actions,
        )
    }
}

/** Art on the left, text bottom-aligned on the right in `displaySmall`, with a min height. */
@Composable
private fun WideHero(
    state: HeroHeaderState,
    artSize: Dp,
    onSubtitleClick: (() -> Unit)?,
    onTitlePlace: (LayoutCoordinates) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SegueSizes.heroWideMinHeight - SegueSizes.topBar)
            .padding(horizontal = SegueSpacing.screenMarginWide)
            .padding(bottom = SegueSpacing.lg),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeroArt(state, artSize)
        Spacer(Modifier.width(SegueSpacing.xl))
        Column(Modifier.weight(1f)) {
            HeroText(state, MaterialTheme.typography.displaySmall, TextAlign.Start, onSubtitleClick, onTitlePlace)
            Spacer(Modifier.height(SegueSpacing.lg))
            HeroActions(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.lg), actions = actions)
        }
    }
}

/**
 * Phone landscape: one strip, no taller than the art thumbnail or the text, so tracks show under it.
 * The art, then the text in `titleLarge`, then the actions at the end. The actions take what they need
 * of the window's [width], leaving the text at least [SHORT_TEXT_MIN_WIDTH], and wrap past that.
 */
@Composable
private fun ShortHero(
    state: HeroHeaderState,
    artSize: Dp,
    width: Dp,
    onSubtitleClick: (() -> Unit)?,
    onTitlePlace: (LayoutCoordinates) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val margin = SegueSpacing.screenMarginWide
    val actionsMaxWidth = width - margin * 2 - artSize - SegueSpacing.lg * 2 - SHORT_TEXT_MIN_WIDTH
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = margin)
            .padding(bottom = SegueSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroArt(state, artSize)
        Spacer(Modifier.width(SegueSpacing.lg))
        Column(Modifier.weight(1f)) {
            HeroText(
                state = state,
                titleStyle = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start,
                onSubtitleClick = onSubtitleClick,
                onTitlePlace = onTitlePlace,
                titleMaxLines = SHORT_TITLE_MAX_LINES,
            )
        }
        Spacer(Modifier.width(SegueSpacing.lg))
        HeroActions(
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.lg, Alignment.End),
            modifier = Modifier.widthIn(max = actionsMaxWidth.coerceAtLeast(SegueSizes.touchTarget)),
            actions = actions,
        )
    }
}

/**
 * The hero's action row. It wraps onto another line when the buttons don't fit, on a narrow phone
 * or beside the short hero's art, so no button is squeezed (DESIGN.md §5.4).
 */
@Composable
private fun HeroActions(
    horizontalArrangement: Arrangement.Horizontal,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        actions()
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

/**
 * The subtitle as a link to the album's artist: a button TalkBack announces as "Go to artist", with
 * a 48dp touch target that overlaps the title and metadata rather than pushing them apart.
 */
@Composable
private fun Modifier.artistLink(onClick: () -> Unit): Modifier = overflowingTouchTarget(
    Modifier.clickable(
        role = Role.Button,
        onClickLabel = stringResource(R.string.ds_action_go_to_artist),
        onClick = onClick,
    ),
)

@Composable
private fun ColumnScope.HeroText(
    state: HeroHeaderState,
    titleStyle: TextStyle,
    textAlign: TextAlign,
    onSubtitleClick: (() -> Unit)?,
    onTitlePlace: (LayoutCoordinates) -> Unit = {},
    titleMaxLines: Int = TITLE_MAX_LINES,
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
        maxLines = titleMaxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced(onTitlePlace),
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
                .then(if (onSubtitleClick != null) Modifier.artistLink(onSubtitleClick) else Modifier),
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
private const val SHORT_TITLE_MAX_LINES = 2

/** The narrowest the short hero's text gets beside its actions before they wrap. */
private val SHORT_TEXT_MIN_WIDTH = 160.dp

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
