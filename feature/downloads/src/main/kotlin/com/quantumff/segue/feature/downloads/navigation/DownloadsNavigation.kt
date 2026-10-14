package com.quantumff.segue.feature.downloads.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.downloads.DownloadsScreen
import kotlinx.serialization.Serializable

@Serializable
data object DownloadsRoute

fun NavController.navigateToDownloads(navOptions: NavOptions? = null) = navigate(DownloadsRoute, navOptions)

fun NavGraphBuilder.downloadsScreen(onBackClick: () -> Unit) {
    composable<DownloadsRoute> {
        DownloadsScreen(onBackClick = onBackClick)
    }
}
