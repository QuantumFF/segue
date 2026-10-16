package com.quantumff.segue.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.settings.LicensesScreen
import com.quantumff.segue.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object LicensesRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

fun NavController.navigateToLicenses(navOptions: NavOptions? = null) = navigate(LicensesRoute, navOptions)

fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onLicensesClick: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBackClick = onBackClick,
            onDownloadsClick = onDownloadsClick,
            onAddServerClick = onAddServerClick,
            onLicensesClick = onLicensesClick,
        )
    }
}

fun NavGraphBuilder.licensesScreen(onBackClick: () -> Unit) {
    composable<LicensesRoute> {
        LicensesScreen(onBackClick = onBackClick)
    }
}
