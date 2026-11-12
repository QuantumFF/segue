package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme

/**
 * Top-level screen title bar ("Home", "Library"), DESIGN.md §4.1: `headlineSmall`, not pinned.
 * Put it as the first item of the scrolling content so it scrolls away. Draws under the status bar.
 */
@Composable
fun TopLevelTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(SegueSizes.topBar)
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

/**
 * Keeps the status bar readable over content that scrolls under it (a [TopLevelTopBar] that has
 * scrolled away): a `background` band the height of the status bar, DESIGN.md §4.1. Pass the
 * state of the list that scrolls under the status bar and align it to the top. At the top of the
 * list it isn't drawn; once the list scrolls it fades in (`medium`), or appears at once with
 * reduce motion on. Hero screens don't use it: their art stays under the status bar.
 */
@Composable
fun StatusBarScrim(scrollState: ScrollableState, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = scrollState.canScrollBackward,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(SegueMotion.medium()),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(SegueMotion.medium()),
        modifier = modifier,
    ) {
        Spacer(
            Modifier
                .testTag(STATUS_BAR_SCRIM_TAG)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background.copy(alpha = STATUS_BAR_SCRIM_ALPHA)),
        )
    }
}

internal const val STATUS_BAR_SCRIM_TAG = "statusBarScrim"
private const val STATUS_BAR_SCRIM_ALPHA = 0.92f

/**
 * The settings action on Home, Library and Playlists: opens Settings. It is announced as
 * "Settings"; [serverName], when known, shows in a plain tooltip on long-press or hover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAction(serverName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val button = @Composable { buttonModifier: Modifier ->
        SegueIconButton(
            icon = SegueIcons.Settings,
            contentDescription = stringResource(R.string.ds_settings),
            onClick = onClick,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = buttonModifier,
        )
    }
    if (serverName == null) {
        button(modifier)
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text(serverName) } },
            state = rememberTooltipState(),
            modifier = modifier,
        ) {
            button(Modifier)
        }
    }
}

/**
 * Detail top bar over a hero (DESIGN.md §4.3): transparent with back and ⋮ while the hero is
 * visible; once [collapsed], it fades (`medium`) to [containerColor] and shows [title] in
 * `titleMedium` plus a 36 play button, which shows Pause while [isPlaying] (the page is the
 * playing queue source). Alpha is applied in the draw phase. [actions] sit at the end and stay
 * visible whether or not the bar is collapsed, e.g. playlist edit mode's Cancel and Save.
 */
@Composable
fun DetailTopBar(
    title: String,
    collapsed: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onPlayClick: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    onMoreClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val fraction by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = SegueMotion.medium(),
        label = "detailTopBar",
    )
    // While the title is invisible the hero's title is on screen; only one of them is read.
    val titleHidden by remember { derivedStateOf { fraction == 0f } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(containerColor, alpha = fraction) }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(SegueSizes.topBar)
            .padding(horizontal = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegueIconButton(
            icon = SegueIcons.Back,
            contentDescription = stringResource(R.string.ds_back),
            onClick = onBackClick,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = fraction },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = SegueSpacing.sm)
                    .then(if (titleHidden) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }),
            )
            if (onPlayClick != null && collapsed) {
                PlayButton(onClick = onPlayClick, isPlaying = isPlaying, size = SegueSizes.detailTopBarPlay)
            }
        }
        if (onMoreClick != null) {
            SegueIconButton(
                icon = SegueIcons.MoreVertical,
                contentDescription = stringResource(R.string.ds_more_options),
                onClick = onMoreClick,
            )
        }
        actions()
    }
}

/**
 * Opaque bar for secondary screens (Settings, Downloads, Queue): back, then [title] in
 * `titleMedium` with an optional muted [subtitle] ("Playing from …"), then [actions].
 */
@Composable
fun SubScreenTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(SegueSizes.topBar)
            .padding(horizontal = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegueIconButton(
            icon = SegueIcons.Back,
            contentDescription = stringResource(R.string.ds_back),
            onClick = onBackClick,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = SegueSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/**
 * Multi-select's bar (DESIGN.md §5.10), in place of a [TopLevelTopBar] while items are selected:
 * close (ends multi-select), the [title] ("3 selected") in `titleMedium`, then [actions].
 * Same height as the bar it replaces, so the content does not move.
 */
@Composable
fun SelectionTopBar(
    title: String,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(SegueSizes.topBar)
            .padding(horizontal = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegueIconButton(
            icon = SegueIcons.Close,
            contentDescription = stringResource(R.string.ds_clear_selection),
            onClick = onCloseClick,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SegueSpacing.sm)
                .semantics { heading() },
        )
        actions()
    }
}

/**
 * Pinned search field (DESIGN.md §4.6): 48 tall, `surfaceContainerHighest`, radius `full`,
 * leading search icon, trailing clear. Focuses itself when [autoFocus].
 */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    onSearch: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SegueSizes.touchTarget)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, SegueShapes.full)
                .padding(start = SegueSpacing.md, end = SegueSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SegueIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SegueSizes.iconSmall),
            )
            Spacer(Modifier.width(SegueSpacing.sm))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val hint = stringResource(R.string.ds_search_hint)
                if (query.isEmpty()) {
                    // The field carries the hint as its label, so the placeholder is not read twice.
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = hint },
                )
            }
            if (query.isNotEmpty()) {
                SegueIconButton(
                    icon = SegueIcons.Close,
                    contentDescription = stringResource(R.string.ds_clear),
                    onClick = { onQueryChange("") },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Now Playing top bar (DESIGN.md §4.7): chevron-down, "PLAYING FROM ALBUM" over the source name,
 * then ⋮. Always light content: it sits on art.
 *
 * @param sourceName null when [sourceLabel] says it all ("SHUFFLED LIBRARY").
 * @param onSourceClick opens the source; null when it opens nothing. [sourceClickLabel] says
 *   what a tap does, for accessibility.
 * @param onMoreClick opens the ⋮ menu; null hides the ⋮ when there is nothing to act on.
 * @param moreBadge draws a dot on ⋮: something in its menu is set, such as a sleep timer.
 * @param status drawn at the end of the source line, before the ⋮, such as the sleep timer chip; it
 *   should be 48 tall at most.
 */
@Composable
fun NowPlayingTopBar(
    sourceLabel: String,
    sourceName: String?,
    onCollapseClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSourceClick: (() -> Unit)? = null,
    sourceClickLabel: String? = null,
    moreBadge: Boolean = false,
    status: (@Composable () -> Unit)? = null,
) {
    val onArt = SegueTheme.colors.onArt
    val shade = SegueTheme.colors.artShade
    val topBarShade = remember(shade) {
        val color = shade.copy(alpha = SegueAlpha.NOW_PLAYING_TOP_BAR_SHADE)
        // Solid where the overline and name sit; only the bar's bottom edge fades into the art.
        Brush.verticalGradient(0f to color, NOW_PLAYING_SHADE_HOLD to color, 1f to color.copy(alpha = 0f))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Behind the status bar and top bar only, so the overline reads over bright art.
            .background(topBarShade)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(SegueSizes.topBar)
            .padding(horizontal = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegueIconButton(
            icon = SegueIcons.ChevronDown,
            contentDescription = stringResource(R.string.ds_collapse),
            onClick = onCollapseClick,
            tint = onArt,
        )
        val source = if (onSourceClick == null) {
            Modifier
        } else {
            Modifier.clickable(onClickLabel = sourceClickLabel, role = Role.Button, onClick = onSourceClick)
        }
        Column(
            Modifier
                .weight(1f)
                .height(SegueSizes.touchTarget)
                .then(source),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = sourceLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SegueTheme.colors.onArtMuted,
                maxLines = 1,
            )
            if (sourceName != null) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = onArt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        status?.invoke()
        if (onMoreClick != null) {
            Box(contentAlignment = Alignment.Center) {
                SegueIconButton(
                    icon = SegueIcons.MoreVertical,
                    contentDescription = stringResource(R.string.ds_more_options),
                    onClick = onMoreClick,
                    tint = onArt,
                )
                if (moreBadge) {
                    // At the icon's top end, as on Now Playing's bottom-row ⋮.
                    Box(Modifier.size(SegueSizes.icon)) {
                        BadgeDot(Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        } else {
            // Keeps the source label centred under the missing ⋮.
            Spacer(Modifier.size(SegueSizes.touchTarget))
        }
    }
}

@PreviewSegueThemes
@Composable
private fun TopBarsPreview() {
    SeguePreview {
        Column {
            TopLevelTopBar(title = "Home") { AccountAction(serverName = "Navidrome", onClick = {}) }
            DetailTopBar(title = "In Rainbows", collapsed = true, onBackClick = {}, onPlayClick = {}, onMoreClick = {})
            SubScreenTopBar(title = "Up next", subtitle = "Playing from In Rainbows", onBackClick = {})
            SelectionTopBar(title = "3 selected", onCloseClick = {}) {
                SegueIconButton(icon = SegueIcons.MoreVertical, contentDescription = null, onClick = {})
            }
            SearchTopBar(query = "", onQueryChange = {}, autoFocus = false)
        }
    }
}

@PreviewSegueThemes
@Composable
private fun NowPlayingTopBarPreview() {
    SeguePreview {
        Box(Modifier.background(MaterialTheme.colorScheme.scrim)) {
            NowPlayingTopBar(
                sourceLabel = "Playing from album",
                sourceName = "In Rainbows",
                onCollapseClick = {},
                onMoreClick = {},
            )
        }
    }
}

/** How far down the Now Playing top bar its shade stays solid before fading out. */
private const val NOW_PLAYING_SHADE_HOLD = 0.75f
