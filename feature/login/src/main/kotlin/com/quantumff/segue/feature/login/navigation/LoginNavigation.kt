package com.quantumff.segue.feature.login.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.login.LoginScreen
import kotlinx.serialization.Serializable

/**
 * Server setup. [canGoBack] is true when opened from Settings to add another server; on first
 * launch there is nothing to go back to.
 */
@Serializable
data class LoginRoute(val canGoBack: Boolean = false)

fun NavController.navigateToLogin(canGoBack: Boolean, navOptions: NavOptions? = null) =
    navigate(LoginRoute(canGoBack), navOptions)

fun NavGraphBuilder.loginScreen(onBackClick: () -> Unit, onSignedIn: () -> Unit) {
    composable<LoginRoute> {
        LoginScreen(onBackClick = onBackClick, onSignedIn = onSignedIn)
    }
}
