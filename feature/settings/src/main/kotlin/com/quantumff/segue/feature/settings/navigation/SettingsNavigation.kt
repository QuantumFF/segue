package com.quantumff.segue.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.quantumff.segue.feature.settings.LicensesScreen
import com.quantumff.segue.feature.settings.SettingsScreen
import com.quantumff.segue.feature.settings.SettingsSection
import kotlinx.serialization.Serializable

/** Settings, opened at the top or at [section], a [SettingsSection] name. */
@Serializable
data class SettingsRoute(val section: String? = null)

@Serializable
data object LicensesRoute

/** Opens Settings, at [section] when given. */
fun NavController.navigateToSettings(section: SettingsSection? = null, navOptions: NavOptions? = null) =
    navigate(SettingsRoute(section?.name), navOptions)

fun NavController.navigateToLicenses(navOptions: NavOptions? = null) = navigate(LicensesRoute, navOptions)

fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onLicensesClick: () -> Unit,
) {
    composable<SettingsRoute> { entry ->
        SettingsScreen(
            onBackClick = onBackClick,
            onDownloadsClick = onDownloadsClick,
            onAddServerClick = onAddServerClick,
            onLicensesClick = onLicensesClick,
            section = entry.toRoute<SettingsRoute>().section?.let(SettingsSection::valueOf),
        )
    }
}

fun NavGraphBuilder.licensesScreen(onBackClick: () -> Unit) {
    composable<LicensesRoute> {
        LicensesScreen(onBackClick = onBackClick)
    }
}
