package com.quantumff.segue.feature.login.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.login.LoginScreen
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

fun NavController.navigateToLogin(navOptions: NavOptions? = null) = navigate(LoginRoute, navOptions)

fun NavGraphBuilder.loginScreen() {
    composable<LoginRoute> {
        LoginScreen()
    }
}
