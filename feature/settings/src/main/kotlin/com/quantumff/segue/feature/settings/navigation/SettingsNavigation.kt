package com.quantumff.segue.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

fun NavGraphBuilder.settingsScreen(onBackClick: () -> Unit, onDownloadsClick: () -> Unit) {
    composable<SettingsRoute> {
        SettingsScreen(onBackClick = onBackClick, onDownloadsClick = onDownloadsClick)
    }
}
