package com.quantumff.segue.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.quantumff.segue.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.feature.home.navigation.HomeRoute
import com.quantumff.segue.feature.library.navigation.LibraryRoute
import com.quantumff.segue.feature.playlist.navigation.PlaylistsRoute
import com.quantumff.segue.feature.search.navigation.SearchRoute
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/** Nested graph per tab. Each tab keeps its own back stack (see [com.quantumff.segue.ui.SegueAppState]). */
@Serializable data object HomeGraph

@Serializable data object LibraryGraph

@Serializable data object PlaylistsGraph

@Serializable data object SearchGraph

/** The four tabs (DESIGN.md §3): bottom bar on phones, rail or sidebar on larger windows. */
enum class TopLevelDestination(
    @param:StringRes val label: Int,
    /** The tab's nested graph. Navigating to it restores the tab's saved back stack. */
    val graph: Any,
    /** The tab's first screen. */
    val root: KClass<*>,
) {
    Home(label = R.string.tab_home, graph = HomeGraph, root = HomeRoute::class),
    Library(label = R.string.tab_library, graph = LibraryGraph, root = LibraryRoute::class),
    Playlists(label = R.string.tab_playlists, graph = PlaylistsGraph, root = PlaylistsRoute::class),
    Search(label = R.string.tab_search, graph = SearchGraph, root = SearchRoute::class),
    ;

    /** Lucide outline when unselected. */
    val icon: ImageVector
        get() = when (this) {
            Home -> SegueIcons.Home
            Library -> SegueIcons.Library
            Playlists -> SegueIcons.Playlists
            Search -> SegueIcons.Search
        }

    /** Bold (stroke 2.5) when selected. */
    val selectedIcon: ImageVector
        get() = when (this) {
            Home -> SegueIcons.HomeSelected
            Library -> SegueIcons.LibrarySelected
            Playlists -> SegueIcons.PlaylistsSelected
            Search -> SegueIcons.SearchSelected
        }
}
