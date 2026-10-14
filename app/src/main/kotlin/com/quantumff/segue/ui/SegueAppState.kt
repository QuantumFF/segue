package com.quantumff.segue.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.quantumff.segue.feature.login.navigation.LoginRoute
import com.quantumff.segue.feature.nowplaying.navigation.NowPlayingRoute
import com.quantumff.segue.feature.queue.navigation.QueueRoute
import com.quantumff.segue.navigation.TopLevelDestination

/** How the shell lays out navigation and the player (DESIGN.md §3). */
enum class ShellLayout {
    /** < 600dp: bottom navigation bar, mini player docked above it. */
    Compact,

    /** 600 to 839dp: navigation rail, mini player across the content area. */
    Medium,

    /** ≥ 840dp: permanent sidebar and a full-width player bar. */
    Expanded,
}

@Composable
fun rememberSegueAppState(
    shellLayout: ShellLayout,
    navController: NavHostController = rememberNavController(),
): SegueAppState {
    val currentTab = rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    return remember(navController, shellLayout, currentTab) {
        SegueAppState(navController = navController, shellLayout = shellLayout, currentTabState = currentTab)
    }
}

/**
 * Shell navigation state.
 *
 * Per-tab back stacks: switching tabs pops the current tab's graph with `saveState` and navigates
 * to the new tab's graph with `restoreState`. Only the current tab's stack is ever on the back
 * stack, and every tab, Home included, gets its saved stack back. Back at a non-Home tab root goes
 * to Home.
 */
@Stable
class SegueAppState(
    val navController: NavHostController,
    val shellLayout: ShellLayout,
    private val currentTabState: MutableState<TopLevelDestination>,
) {
    val currentTab: TopLevelDestination by currentTabState

    /** True while the last navigation was a tab switch: tab switches crossfade (DESIGN.md §2.5). */
    var isTabSwitch: Boolean by mutableStateOf(false)
        private set

    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    /** Full-screen destinations hide the navigation chrome and the mini player. */
    fun isFullScreen(destination: NavDestination?): Boolean = destination != null &&
        FullScreenRoutes.any { destination.hasRoute(it) }

    /** True at the root screen of [currentTab]. */
    fun isAtTabRoot(destination: NavDestination?): Boolean = destination?.hasRoute(currentTab.root) == true

    fun navigateToTab(tab: TopLevelDestination) {
        isTabSwitch = true
        if (tab == currentTab) {
            // Re-selecting the current tab returns to its root.
            navController.popBackStack(tab.root, inclusive = false)
            return
        }
        val previous = currentTab
        currentTabState.value = tab
        navController.navigate(tab.graph) {
            // Popping the previous tab's graph (inclusive) saves its stack under that graph's id,
            // which is what restoreState looks up when the tab is selected again.
            popUpTo(previous.graph) {
                inclusive = true
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Forward navigation inside the current tab (shared-axis transition). */
    fun navigate(action: NavHostController.() -> Unit) {
        isTabSwitch = false
        navController.action()
    }

    fun navigateBack() {
        isTabSwitch = false
        navController.navigateUp()
    }

    private companion object {
        val FullScreenRoutes = listOf(NowPlayingRoute::class, QueueRoute::class, LoginRoute::class)
    }
}
