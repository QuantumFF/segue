package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
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
 * scrolled away): a `background` band the height of the status bar. Align it to the top.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background.copy(alpha = STATUS_BAR_SCRIM_ALPHA)),
    )
}

private const val STATUS_BAR_SCRIM_ALPHA = 0.92f

/**
 * The settings action on Home, Library and Playlists: opens Settings. [serverName] is announced
 * and labelled.
 */
@Composable
fun AccountAction(serverName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SegueIconButton(
        icon = SegueIcons.Settings,
        contentDescription = serverName ?: stringResource(R.string.ds_account),
        onClick = onClick,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Detail top bar over a hero (DESIGN.md §4.3): transparent with back and ⋮ while the hero is
 * visible; once [collapsed], it fades (`medium`) to [containerColor] and shows [title] in
 * `titleMedium` plus a 36 play button, which shows Pause while [isPlaying] (the page is the
 * playing queue source). Alpha is applied in the draw phase.
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
) {
    val fraction by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = SegueMotion.medium(),
        label = "detailTopBar",
    )
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
                    .padding(horizontal = SegueSpacing.sm),
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
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ds_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .focusRequester(focusRequester),
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
 */
@Composable
fun NowPlayingTopBar(
    sourceLabel: String,
    sourceName: String?,
    onCollapseClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSourceClick: (() -> Unit)? = null,
    sourceClickLabel: String? = null,
) {
    val onArt = SegueTheme.colors.onArt
    Row(
        modifier = modifier
            .fillMaxWidth()
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
        SegueIconButton(
            icon = SegueIcons.MoreVertical,
            contentDescription = stringResource(R.string.ds_more_options),
            onClick = onMoreClick,
            tint = onArt,
        )
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
