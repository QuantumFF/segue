package com.quantumff.segue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.R
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.navigationIndicator
import com.quantumff.segue.navigation.TopLevelDestination
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Phone navigation bar (DESIGN.md §3): 64 tall plus the system navigation inset,
 * `surfaceContainer`, no tonal elevation, labels always shown, indicator pill `primary` @ 16%.
 */
@Composable
fun SegueNavigationBar(
    currentTab: TopLevelDestination,
    onTabClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = SegueSizes.noElevation,
        // The parent pads for the system navigation; M3's 80dp minimum is capped to 64 here.
        windowInsets = WindowInsets(0),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .height(SegueSizes.navigationBar),
    ) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.navigationIndicator,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Medium-width navigation rail, 80 wide. */
@Composable
fun SegueNavigationRail(
    currentTab: TopLevelDestination,
    onTabClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        modifier = modifier.width(SegueSizes.navigationRail),
    ) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationRailItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.navigationIndicator,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * Expanded-width permanent sidebar, 240 wide, Feishin style: the destinations, then a "Playlists"
 * list that opens each playlist.
 */
@Composable
fun SegueSidebar(
    currentTab: TopLevelDestination,
    onTabClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    playlists: ImmutableList<SidebarPlaylist> = persistentListOf(),
    onPlaylistClick: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(SegueSizes.sidebar)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(horizontal = SegueSpacing.md, vertical = SegueSpacing.lg),
    ) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationDrawerItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge) },
                shape = SegueShapes.full,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.navigationIndicator,
                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.height(SegueSpacing.xl))
        Text(
            text = stringResource(R.string.sidebar_playlists).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SegueSpacing.lg),
        )
        Spacer(Modifier.height(SegueSpacing.sm))
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.sidebar_no_playlists),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SegueSpacing.lg),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SegueSizes.touchTarget)
                            .clip(SegueShapes.full)
                            .clickable { onPlaylistClick(playlist.id) }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = SegueSpacing.lg),
                    )
                }
            }
        }
    }
}

@PreviewSegueThemes
@Composable
private fun SegueNavigationBarPreview() {
    SeguePreview { SegueNavigationBar(currentTab = TopLevelDestination.Library, onTabClick = {}) }
}

@PreviewSegueThemes
@Composable
private fun SegueNavigationRailPreview() {
    SeguePreview { SegueNavigationRail(currentTab = TopLevelDestination.Home, onTabClick = {}) }
}

@PreviewSegueThemes
@Composable
private fun SegueSidebarPreview() {
    SeguePreview { SegueSidebar(currentTab = TopLevelDestination.Playlists, onTabClick = {}) }
}
